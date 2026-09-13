package com.quoteflow.subscription.dto;

public record FeatureFlagsDto(
		boolean removeQuoteFlowBranding,
		boolean multiUser,
		boolean advancedReports,
		boolean emailSending,
		boolean aiAssistant
) {
}
