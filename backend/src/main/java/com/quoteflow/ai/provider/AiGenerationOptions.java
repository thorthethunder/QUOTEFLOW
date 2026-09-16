package com.quoteflow.ai.provider;

import java.util.Objects;

/**
 * Provider-neutral generation options. Feature code chooses settings;
 * do not hardcode one temperature for every AI use case.
 */
public record AiGenerationOptions(
		Double temperature,
		Integer numPredict,
		boolean disableThinking
) {

	public static AiGenerationOptions defaults() {
		return new AiGenerationOptions(null, null, true);
	}

	/** Conservative settings for structured extraction. */
	public static AiGenerationOptions structuredExtraction() {
		return new AiGenerationOptions(0.1d, 1024, true);
	}

	public AiGenerationOptions {
		if (temperature != null && (temperature < 0 || temperature > 2)) {
			throw new IllegalArgumentException("temperature must be between 0 and 2");
		}
		if (numPredict != null && numPredict < 1) {
			throw new IllegalArgumentException("numPredict must be >= 1");
		}
	}

	public AiGenerationOptions withTemperature(double value) {
		return new AiGenerationOptions(value, numPredict, disableThinking);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof AiGenerationOptions that)) {
			return false;
		}
		return disableThinking == that.disableThinking
				&& Objects.equals(temperature, that.temperature)
				&& Objects.equals(numPredict, that.numPredict);
	}
}
