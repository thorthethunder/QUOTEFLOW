package com.quoteflow.auth;

import com.quoteflow.auth.dto.AuthResponse;
import com.quoteflow.auth.dto.LoginRequest;
import com.quoteflow.auth.dto.MeResponse;
import com.quoteflow.auth.dto.RegisterRequest;
import com.quoteflow.business.Business;
import com.quoteflow.business.BusinessRepository;
import com.quoteflow.business.BusinessStatus;
import com.quoteflow.common.EmailNormalizer;
import com.quoteflow.identity.AppUser;
import com.quoteflow.identity.RefreshToken;
import com.quoteflow.identity.RefreshTokenRepository;
import com.quoteflow.identity.TenantRole;
import com.quoteflow.identity.UserRepository;
import com.quoteflow.identity.UserStatus;
import com.quoteflow.security.AuthenticatedUser;
import com.quoteflow.security.JwtService;
import com.quoteflow.security.SecurityProperties;
import com.quoteflow.subscription.SubscriptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class AuthService {

	private static final Logger log = LoggerFactory.getLogger(AuthService.class);

	private final BusinessRepository businessRepository;
	private final UserRepository userRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final RefreshTokenHasher refreshTokenHasher;
	private final SecurityProperties securityProperties;
	private final SubscriptionService subscriptionService;

	public AuthService(
			BusinessRepository businessRepository,
			UserRepository userRepository,
			RefreshTokenRepository refreshTokenRepository,
			PasswordEncoder passwordEncoder,
			JwtService jwtService,
			RefreshTokenHasher refreshTokenHasher,
			SecurityProperties securityProperties,
			SubscriptionService subscriptionService) {
		this.businessRepository = businessRepository;
		this.userRepository = userRepository;
		this.refreshTokenRepository = refreshTokenRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
		this.refreshTokenHasher = refreshTokenHasher;
		this.securityProperties = securityProperties;
		this.subscriptionService = subscriptionService;
	}

	@Transactional
	public AuthResponse register(RegisterRequest request) {
		String email = EmailNormalizer.normalize(request.email());
		if (userRepository.existsByEmail(email)) {
			log.info("auth.event=register_rejected reason=duplicate_email");
			throw new DuplicateEmailException();
		}

		Business business = new Business(
				request.businessName().trim(),
				request.currency().trim().toUpperCase(),
				request.timezone().trim(),
				BusinessStatus.ACTIVE);
		business.setEmail(email);

		String passwordHash = passwordEncoder.encode(request.password());
		AppUser owner = new AppUser(business, email, passwordHash, TenantRole.OWNER, UserStatus.ACTIVE);
		owner.setFirstName(request.firstName().trim());
		owner.setLastName(request.lastName().trim());

		try {
			businessRepository.save(business);
			userRepository.save(owner);
			subscriptionService.ensureDefaultFreeSubscription(business.getId());
			userRepository.flush();
		}
		catch (DataIntegrityViolationException ex) {
			log.info("auth.event=register_rejected reason=integrity");
			throw new DuplicateEmailException();
		}

		log.info("auth.event=register_success userId={} businessId={}", owner.getId(), business.getId());
		return issueSession(owner, business);
	}

	@Transactional
	public AuthResponse login(LoginRequest request) {
		String email = EmailNormalizer.normalize(request.email());
		AppUser user = userRepository.findByEmailWithBusiness(email).orElse(null);
		if (user == null) {
			log.info("auth.event=login_failure reason=unknown_or_invalid");
			throw new AuthenticationFailedException();
		}

		Business business = user.getBusiness();
		if (user.getStatus() != UserStatus.ACTIVE) {
			log.info("auth.event=login_failure reason=user_disabled userId={}", user.getId());
			throw new AccountNotEligibleException("Account is not available for login.");
		}
		if (business.getStatus() != BusinessStatus.ACTIVE) {
			log.info("auth.event=login_failure reason=business_inactive businessId={} status={}",
					business.getId(), business.getStatus());
			throw new AccountNotEligibleException("Business account is not available for login.");
		}
		if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
			log.info("auth.event=login_failure reason=unknown_or_invalid");
			throw new AuthenticationFailedException();
		}

		user.setLastLoginAt(Instant.now());
		log.info("auth.event=login_success userId={} businessId={}", user.getId(), business.getId());
		return issueSession(user, business);
	}

	@Transactional
	public AuthResponse refresh(String rawRefreshToken) {
		String tokenHash = refreshTokenHasher.hash(rawRefreshToken);
		RefreshToken existing = refreshTokenRepository.findByTokenHashForUpdate(tokenHash)
				.orElseThrow(() -> {
					log.info("auth.event=refresh_failure reason=not_found");
					return new AuthenticationFailedException("Invalid refresh token.");
				});

		Instant now = Instant.now();
		if (existing.getRevokedAt() != null) {
			log.info("auth.event=refresh_failure reason=revoked tokenId={}", existing.getId());
			throw new AuthenticationFailedException("Invalid refresh token.");
		}
		if (existing.getExpiresAt().isBefore(now)) {
			log.info("auth.event=refresh_failure reason=expired tokenId={}", existing.getId());
			throw new AuthenticationFailedException("Invalid refresh token.");
		}

		AppUser user = existing.getUser();
		Business business = user.getBusiness();
		assertEligibleForCredentials(user, business);

		existing.setRevokedAt(now);
		existing.setLastUsedAt(now);

		log.info("auth.event=refresh_success userId={} rotatedFrom={}", user.getId(), existing.getId());
		return issueSession(user, business);
	}

	@Transactional
	public void logout(String rawRefreshToken) {
		String tokenHash = refreshTokenHasher.hash(rawRefreshToken);
		refreshTokenRepository.findByTokenHashForUpdate(tokenHash).ifPresent(token -> {
			if (token.getRevokedAt() == null) {
				token.setRevokedAt(Instant.now());
				log.info("auth.event=logout_success tokenId={} userId={}", token.getId(), token.getUser().getId());
			}
			else {
				log.info("auth.event=logout_idempotent tokenId={}", token.getId());
			}
		});
	}

	@Transactional(readOnly = true)
	public MeResponse me(AuthenticatedUser principal) {
		AppUser user = userRepository.findByIdWithBusiness(principal.getUserId())
				.orElseThrow(() -> new AccountNotEligibleException("Authenticated user no longer exists."));
		Business business = user.getBusiness();
		return new MeResponse(
				user.getId(),
				business.getId(),
				business.getName(),
				user.getFirstName(),
				user.getLastName(),
				user.getEmail(),
				user.getTenantRole());
	}

	private AuthResponse issueSession(AppUser user, Business business) {
		assertEligibleForCredentials(user, business);

		AuthenticatedUser principal = new AuthenticatedUser(
				user.getId(),
				business.getId(),
				user.getTenantRole(),
				user.getEmail());
		JwtService.IssuedAccessToken access = jwtService.issueAccessToken(principal);

		String rawRefresh = refreshTokenHasher.generateOpaqueToken();
		String refreshHash = refreshTokenHasher.hash(rawRefresh);
		Instant refreshExpires = Instant.now().plus(securityProperties.getJwt().getRefreshTokenTtl());
		refreshTokenRepository.save(new RefreshToken(user, refreshHash, refreshExpires));

		return new AuthResponse(
				access.token(),
				"Bearer",
				access.expiresInSeconds(),
				access.expiresAt(),
				rawRefresh,
				new AuthResponse.UserSummary(
						user.getId(),
						business.getId(),
						business.getName(),
						user.getEmail(),
						user.getFirstName(),
						user.getLastName(),
						user.getTenantRole()));
	}

	private void assertEligibleForCredentials(AppUser user, Business business) {
		if (user.getStatus() != UserStatus.ACTIVE) {
			throw new AccountNotEligibleException("Account is not available.");
		}
		if (business.getStatus() != BusinessStatus.ACTIVE) {
			throw new AccountNotEligibleException("Business account is not available.");
		}
	}
}
