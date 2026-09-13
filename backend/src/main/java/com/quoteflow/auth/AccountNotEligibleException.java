package com.quoteflow.auth;

public class AccountNotEligibleException extends RuntimeException {

	public AccountNotEligibleException(String message) {
		super(message);
	}
}
