package com.quoteflow.customer;

import com.quoteflow.business.Business;
import com.quoteflow.common.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "customers")
public class Customer extends BaseAuditableEntity {

	@Id
	@Column(nullable = false, updatable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "business_id", nullable = false, updatable = false)
	private Business business;

	@Column(name = "display_name", nullable = false, length = 200)
	private String displayName;

	@Column(length = 320)
	private String email;

	@Column(length = 40)
	private String phone;

	@Column(name = "company_name", length = 200)
	private String companyName;

	@Column(name = "address_line1", length = 200)
	private String addressLine1;

	@Column(name = "address_line2", length = 200)
	private String addressLine2;

	@Column(length = 100)
	private String city;

	@Column(name = "state_region", length = 100)
	private String stateRegion;

	@Column(name = "postal_code", length = 20)
	private String postalCode;

	@Column(name = "country_code", length = 2)
	private String countryCode;

	@Column(name = "tax_id", length = 50)
	private String taxId;

	@Column(length = 2000)
	private String notes;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private CustomerStatus status;

	protected Customer() {
	}

	public Customer(Business business, String displayName) {
		this.id = UUID.randomUUID();
		this.business = business;
		this.displayName = displayName;
		this.status = CustomerStatus.ACTIVE;
	}

	public UUID getId() {
		return id;
	}

	public Business getBusiness() {
		return business;
	}

	public UUID getBusinessId() {
		return business != null ? business.getId() : null;
	}

	public String getDisplayName() {
		return displayName;
	}

	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getPhone() {
		return phone;
	}

	public void setPhone(String phone) {
		this.phone = phone;
	}

	public String getCompanyName() {
		return companyName;
	}

	public void setCompanyName(String companyName) {
		this.companyName = companyName;
	}

	public String getAddressLine1() {
		return addressLine1;
	}

	public void setAddressLine1(String addressLine1) {
		this.addressLine1 = addressLine1;
	}

	public String getAddressLine2() {
		return addressLine2;
	}

	public void setAddressLine2(String addressLine2) {
		this.addressLine2 = addressLine2;
	}

	public String getCity() {
		return city;
	}

	public void setCity(String city) {
		this.city = city;
	}

	public String getStateRegion() {
		return stateRegion;
	}

	public void setStateRegion(String stateRegion) {
		this.stateRegion = stateRegion;
	}

	public String getPostalCode() {
		return postalCode;
	}

	public void setPostalCode(String postalCode) {
		this.postalCode = postalCode;
	}

	public String getCountryCode() {
		return countryCode;
	}

	public void setCountryCode(String countryCode) {
		this.countryCode = countryCode;
	}

	public String getTaxId() {
		return taxId;
	}

	public void setTaxId(String taxId) {
		this.taxId = taxId;
	}

	public String getNotes() {
		return notes;
	}

	public void setNotes(String notes) {
		this.notes = notes;
	}

	public CustomerStatus getStatus() {
		return status;
	}

	public void setStatus(CustomerStatus status) {
		this.status = status;
	}
}
