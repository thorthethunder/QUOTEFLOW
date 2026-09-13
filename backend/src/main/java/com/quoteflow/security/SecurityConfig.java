package com.quoteflow.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {

	private final JwtAuthenticationFilter jwtAuthenticationFilter;
	private final RestAuthenticationEntryPoint authenticationEntryPoint;
	private final RestAccessDeniedHandler accessDeniedHandler;
	private final SecurityProperties securityProperties;
	private final CsrfCookieFilter csrfCookieFilter;
	private final Environment environment;

	public SecurityConfig(
			JwtAuthenticationFilter jwtAuthenticationFilter,
			RestAuthenticationEntryPoint authenticationEntryPoint,
			RestAccessDeniedHandler accessDeniedHandler,
			SecurityProperties securityProperties,
			CsrfCookieFilter csrfCookieFilter,
			Environment environment) {
		this.jwtAuthenticationFilter = jwtAuthenticationFilter;
		this.authenticationEntryPoint = authenticationEntryPoint;
		this.accessDeniedHandler = accessDeniedHandler;
		this.securityProperties = securityProperties;
		this.csrfCookieFilter = csrfCookieFilter;
		this.environment = environment;
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
		csrfRepository.setCookiePath("/");
		csrfRepository.setCookieName("XSRF-TOKEN");
		csrfRepository.setHeaderName("X-XSRF-TOKEN");

		CsrfTokenRequestAttributeHandler requestHandler = new CsrfTokenRequestAttributeHandler();
		requestHandler.setCsrfRequestAttributeName(null);

		RequestMatcher cookieAuthStateChange = request -> {
			String path = request.getRequestURI();
			return ("POST".equalsIgnoreCase(request.getMethod()))
					&& (path.endsWith("/api/v1/auth/refresh") || path.endsWith("/api/v1/auth/logout"));
		};

		http
				.csrf(csrf -> csrf
						.csrfTokenRepository(csrfRepository)
						.csrfTokenRequestHandler(requestHandler)
						.requireCsrfProtectionMatcher(cookieAuthStateChange))
				.cors(Customizer.withDefaults())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.exceptionHandling(ex -> ex
						.authenticationEntryPoint(authenticationEntryPoint)
						.accessDeniedHandler(accessDeniedHandler))
				.authorizeHttpRequests(auth -> {
					boolean prod = Arrays.stream(environment.getActiveProfiles())
							.anyMatch(p -> p.equalsIgnoreCase("prod"));
					auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
							.requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf").permitAll()
							.requestMatchers(
									"/api/v1/auth/register",
									"/api/v1/auth/login",
									"/api/v1/auth/refresh",
									"/api/v1/auth/logout")
							.permitAll()
							.requestMatchers(
									"/actuator/health",
									"/actuator/health/**")
							.permitAll();
					if (!prod) {
						auth.requestMatchers("/actuator/info").permitAll()
								.requestMatchers(
										"/v3/api-docs/**",
										"/swagger-ui/**",
										"/swagger-ui.html")
								.permitAll();
					}
					auth.requestMatchers(HttpMethod.POST, "/api/v1/webhooks/razorpay").permitAll()
							.anyRequest().authenticated();
				})
				.headers(headers -> {
					headers.cacheControl(cache -> cache.disable());
					headers.contentTypeOptions(Customizer.withDefaults());
					headers.frameOptions(frame -> frame.deny());
					headers.referrerPolicy(referrer -> referrer.policy(
							ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER));
					headers.permissionsPolicy(permissions -> permissions.policy(
							"camera=(), microphone=(), geolocation=(), payment=()"));
					headers.contentSecurityPolicy(csp -> csp.policyDirectives(
							"default-src 'none'; frame-ancestors 'none'; base-uri 'none'"));
					boolean prod = Arrays.stream(environment.getActiveProfiles())
							.anyMatch(p -> p.equalsIgnoreCase("prod"));
					if (prod) {
						headers.httpStrictTransportSecurity(hsts -> hsts
								.includeSubDomains(true)
								.maxAgeInSeconds(31_536_000));
					} else {
						headers.httpStrictTransportSecurity(hsts -> hsts.disable());
					}
				})
				.addFilterAfter(csrfCookieFilter, CsrfFilter.class)
				.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder(securityProperties.getBcryptStrength());
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration configuration = new CorsConfiguration();
		List<String> origins = securityProperties.getCors().getAllowedOrigins().stream()
				.flatMap(value -> java.util.Arrays.stream(value.split(",")))
				.map(String::trim)
				.filter(s -> !s.isEmpty())
				.toList();
		configuration.setAllowedOrigins(origins);
		configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		configuration.setAllowedHeaders(List.of(
				"Authorization",
				"Content-Type",
				"Accept",
				"X-Requested-With",
				"X-XSRF-TOKEN",
				"X-Correlation-Id"));
		configuration.setExposedHeaders(List.of("X-Correlation-Id"));
		configuration.setAllowCredentials(true);
		configuration.setMaxAge(3600L);
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}
}
