package com.quoteflow.ai.usage;

import com.quoteflow.ai.provider.AiFeature;
import com.quoteflow.ai.provider.AiProviderType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@ExtendWith(OutputCaptureExtension.class)
class LoggingAiUsageRecorderTest {

	@Test
	void doesNotLogPromptOrCustomerPayload(CapturedOutput output) {
		LoggingAiUsageRecorder recorder = new LoggingAiUsageRecorder(mock(AiUsageLedgerService.class));
		recorder.record(new AiUsageEvent(
				AiProviderType.OLLAMA,
				"OLLAMA",
				"qwen3:8b",
				AiFeature.STRUCTURED_SMOKE,
				true,
				null,
				42L,
				10,
				20,
				UUID.randomUUID(),
				UUID.randomUUID()));
		String logs = output.getOut() + output.getErr();
		assertThat(logs).contains("ai.usage");
		assertThat(logs).doesNotContain("Raj Electrical");
		assertThat(logs).doesNotContain("Create a quotation");
		assertThat(logs).doesNotContain("customerName");
	}
}
