package com.quoteflow.ai;

import com.quoteflow.ai.exception.AiUnavailableException;
import com.quoteflow.ai.provider.AiFeature;
import com.quoteflow.ai.provider.AiProvider;
import com.quoteflow.ai.provider.AiProviderType;
import com.quoteflow.ai.provider.AiRequest;
import com.quoteflow.ai.provider.DisabledAiProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class AiDisabledIntegrationTest {

	@Autowired
	AiProvider aiProvider;

	@Autowired
	WebApplicationContext webApplicationContext;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
				.apply(springSecurity())
				.build();
	}

	@Test
	void disabledProviderIsWired() {
		assertThat(aiProvider).isInstanceOf(DisabledAiProvider.class);
		assertThat(aiProvider.isEnabled()).isFalse();
		assertThat(aiProvider.type()).isEqualTo(AiProviderType.DISABLED);
		assertThatThrownBy(() -> aiProvider.generate(AiRequest.of(AiFeature.PROVIDER_SMOKE, "hi")))
				.isInstanceOf(AiUnavailableException.class);
	}

	@Test
	void healthRemainsUpWhenAiDisabled() throws Exception {
		mockMvc.perform(get("/actuator/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"));
	}
}
