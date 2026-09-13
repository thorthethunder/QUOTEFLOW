package com.quoteflow.business;

import com.quoteflow.common.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "businesses")
public class Business extends BaseAuditableEntity {

	@Id
	@Column(nullable = false, updatable = false)
	private UUID id;

	@Column(nullable = false, length = 200)
	private String name;

	@Column(length = 320)
	private String email;

	@Column(length = 32)
	private String phone;

	@Column(name = "address_line1", length = 200)
	private String addressLine1;

	@Column(name = "address_line2", length = 200)
	private String addressLine2;

	@Column(length = 100)
	private String city;

	@Column(length = 100)
	private String state;

	@Column(name = "postal_code", length = 20)
	private String postalCode;

	@Column(name = "country_code", length = 2)
	private String countryCode;

	@Column(name = "tax_identification_number", length = 50)
	private String taxIdentificationNumber;

	@Column(nullable = false, length = 3)
	private String currency;

	@Column(nullable = false, length = 64)
	private String timezone;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private BusinessStatus status;

	protected Business() {
	}

	public Business(String name, String currency, String timezone, BusinessStatus status) {
		this.id = UUID.randomUUID();
		this.name = name;
		this.currency = currency;
		this.timezone = timezone;
		this.status = status;
	}

	public UUID getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
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

	public String getState() {
		return state;
	}

	public void setState(String state) {
		this.state = state;
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

	public String getTaxIdentificationNumber() {
		return taxIdentificationNumber;
	}

	public void setTaxIdentificationNumber(String taxIdentificationNumber) {
		this.taxIdentificationNumber = taxIdentificationNumber;
	}

	public String getCurrency() {
		return currency;
	}

	public void setCurrency(String currency) {
		this.currency = currency;
	}

	public String getTimezone() {
		return timezone;
	}

	public void setTimezone(String timezone) {
		this.timezone = timezone;
	}

	public BusinessStatus getStatus() {
		return status;
	}

	public void setStatus(BusinessStatus status) {
		this.status = status;
	}
}
