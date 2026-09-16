package com.quoteflow.ai.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiConfigurationValidationTest {

	@Test
	void disabledDoesNotRequireOllama() {
		AiProperties props = new AiProperties();
		props.setEnabled(false);
		props.getOllama().setBaseUrl("");
		props.getOllama().setModel("");
		MockEnvironment env = new MockEnvironment();
		assertThatCode(() -> AiConfiguration.validate(props, env)).doesNotThrowAnyException();
	}

	@Test
	void enabledOllamaRequiresBaseUrlAndModel() {
		AiProperties props = new AiProperties();
		props.setEnabled(true);
		props.setProvider("OLLAMA");
		props.getOllama().setBaseUrl("");
		props.getOllama().setModel("qwen3:8b");
		MockEnvironment env = new MockEnvironment();
		assertThatThrownBy(() -> AiConfiguration.validate(props, env))
				.hasMessageContaining("OLLAMA_BASE_URL");
	}

	@Test
	void unsupportedProviderFailsClearly() {
		AiProperties props = new AiProperties();
		props.setEnabled(true);
		props.setProvider("OPENAI");
		MockEnvironment env = new MockEnvironment();
		assertThatThrownBy(() -> AiConfiguration.validate(props, env))
				.hasMessageContaining("not implemented");
	}

	@Test
	void enabledOllamaWithConfigPasses() {
		AiProperties props = new AiProperties();
		props.setEnabled(true);
		props.setProvider("OLLAMA");
		props.getOllama().setBaseUrl("http://localhost:11434");
		props.getOllama().setModel("qwen3:8b");
		MockEnvironment env = new MockEnvironment();
		assertThatCode(() -> AiConfiguration.validate(props, env)).doesNotThrowAnyException();
	}
}
