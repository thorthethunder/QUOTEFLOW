package com.quoteflow;

import com.quoteflow.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class QuoteflowBackendApplicationTests extends PostgresIntegrationTest {

	@Test
	void contextLoadsWithMigratedSchema() {
	}
}
