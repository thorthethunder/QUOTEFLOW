package com.quoteflow.security;

public class InvalidAccessTokenException extends RuntimeException {

	public InvalidAccessTokenException(String message) {
		super(message);
	}
}
