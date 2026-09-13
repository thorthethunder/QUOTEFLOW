package com.quoteflow.subscription.dto;

public record UsageMeterDto(
		long used,
		Integer limit,
		boolean unlimited
) {
	public static UsageMeterDto of(long used, Integer limit) {
		return new UsageMeterDto(used, limit, limit == null);
	}
}
