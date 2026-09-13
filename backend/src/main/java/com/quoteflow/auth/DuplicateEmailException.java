package com.quoteflow.auth;

public class DuplicateEmailException extends RuntimeException {

	public DuplicateEmailException() {
		super("Unable to complete registration with the provided email.");
	}
}
