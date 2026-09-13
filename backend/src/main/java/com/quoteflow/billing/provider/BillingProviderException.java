package com.quoteflow.billing.provider;

public class BillingProviderException extends RuntimeException {

	private final String code;
	private final boolean retryableRead;

	public BillingProviderException(String code, String message) {
		this(code, message, false, null);
	}

	public BillingProviderException(String code, String message, boolean retryableRead, Throwable cause) {
		super(message, cause);
		this.code = code;
		this.retryableRead = retryableRead;
	}

	public String getCode() {
		return code;
	}

	public boolean isRetryableRead() {
		return retryableRead;
	}
}
