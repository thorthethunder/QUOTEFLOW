package com.quoteflow.ai;

import com.quoteflow.ai.provider.AiProvider;
import com.quoteflow.ai.provider.ollama.OllamaAiProvider;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class AiEnabledOllamaIntegrationTest {

	private static final HttpServer SERVER;
	private static final String BASE_URL;

	static {
		try {
			SERVER = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
			SERVER.createContext("/api/tags", exchange -> {
				byte[] body = "{\"models\":[]}".getBytes(StandardCharsets.UTF_8);
				exchange.sendResponseHeaders(200, body.length);
				try (OutputStream os = exchange.getResponseBody()) {
					os.write(body);
				}
			});
			SERVER.setExecutor(Executors.newCachedThreadPool());
			SERVER.start();
			BASE_URL = "http://127.0.0.1:" + SERVER.getAddress().getPort();
		} catch (IOException e) {
			throw new ExceptionInInitializerError(e);
		}
	}

	@AfterAll
	static void stopFakeOllama() {
		SERVER.stop(0);
	}

	@DynamicPropertySource
	static void aiProps(DynamicPropertyRegistry registry) {
		registry.add("quoteflow.ai.enabled", () -> "true");
		registry.add("quoteflow.ai.provider", () -> "OLLAMA");
		registry.add("quoteflow.ai.adapter", () -> "legacy-rest");
		registry.add("quoteflow.ai.ollama.base-url", () -> BASE_URL);
		registry.add("quoteflow.ai.ollama.model", () -> "qwen3:8b");
	}

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
	void wiresOllamaProviderWhenEnabled() {
		assertThat(aiProvider).isInstanceOf(OllamaAiProvider.class);
		assertThat(aiProvider.isEnabled()).isTrue();
		assertThat(aiProvider.model()).isEqualTo("qwen3:8b");
	}

	@Test
	void healthStaysUpEvenIfConfiguredModelMissing() throws Exception {
		mockMvc.perform(get("/actuator/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"));
	}
}
