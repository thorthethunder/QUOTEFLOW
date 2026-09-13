package com.quoteflow.identity;

import com.quoteflow.business.Business;
import com.quoteflow.common.BaseAuditableEntity;
import com.quoteflow.common.EmailNormalizer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_users")
public class AppUser extends BaseAuditableEntity {

	@Id
	@Column(nullable = false, updatable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "business_id", nullable = false)
	private Business business;

	@Column(nullable = false, length = 320, unique = true)
	private String email;

	@Column(name = "password_hash", nullable = false, length = 255)
	private String passwordHash;

	@Column(name = "first_name", length = 100)
	private String firstName;

	@Column(name = "last_name", length = 100)
	private String lastName;

	@Enumerated(EnumType.STRING)
	@Column(name = "tenant_role", nullable = false, length = 20)
	private TenantRole tenantRole;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private UserStatus status;

	@Column(name = "email_verified", nullable = false)
	private boolean emailVerified;

	@Column(name = "last_login_at")
	private Instant lastLoginAt;

	protected AppUser() {
	}

	public AppUser(Business business, String email, String passwordHash, TenantRole tenantRole, UserStatus status) {
		this.id = UUID.randomUUID();
		this.business = business;
		this.email = EmailNormalizer.normalize(email);
		this.passwordHash = passwordHash;
		this.tenantRole = tenantRole;
		this.status = status;
		this.emailVerified = false;
	}

	@PrePersist
	@PreUpdate
	void normalizeEmail() {
		this.email = EmailNormalizer.normalize(this.email);
	}

	public UUID getId() {
		return id;
	}

	public Business getBusiness() {
		return business;
	}

	public void setBusiness(Business business) {
		this.business = business;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = EmailNormalizer.normalize(email);
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public void setPasswordHash(String passwordHash) {
		this.passwordHash = passwordHash;
	}

	public String getFirstName() {
		return firstName;
	}

	public void setFirstName(String firstName) {
		this.firstName = firstName;
	}

	public String getLastName() {
		return lastName;
	}

	public void setLastName(String lastName) {
		this.lastName = lastName;
	}

	public TenantRole getTenantRole() {
		return tenantRole;
	}

	public void setTenantRole(TenantRole tenantRole) {
		this.tenantRole = tenantRole;
	}

	public UserStatus getStatus() {
		return status;
	}

	public void setStatus(UserStatus status) {
		this.status = status;
	}

	public boolean isEmailVerified() {
		return emailVerified;
	}

	public void setEmailVerified(boolean emailVerified) {
		this.emailVerified = emailVerified;
	}

	public Instant getLastLoginAt() {
		return lastLoginAt;
	}

	public void setLastLoginAt(Instant lastLoginAt) {
		this.lastLoginAt = lastLoginAt;
	}
}
