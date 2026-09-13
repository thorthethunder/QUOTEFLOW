package com.quoteflow.notification.email;

public class EmailProviderException extends RuntimeException {

	private final String code;
	private final boolean retryable;

	public EmailProviderException(String code, String message, boolean retryable) {
		super(message);
		this.code = code;
		this.retryable = retryable;
	}

	public EmailProviderException(String code, String message, boolean retryable, Throwable cause) {
		super(message, cause);
		this.code = code;
		this.retryable = retryable;
	}

	public String getCode() {
		return code;
	}

	public boolean isRetryable() {
		return retryable;
	}
}
