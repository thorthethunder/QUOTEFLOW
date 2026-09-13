package com.quoteflow.config;

import com.quoteflow.billing.BillingProperties;
import com.quoteflow.notification.email.EmailProperties;
import com.quoteflow.security.SecurityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionIntegrationValidatorTest {

	@Test
	void rejectsFakeBillingWhenEnabledInProd() {
		BillingProperties billing = new BillingProperties();
		billing.setEnabled(true);
		billing.setProvider("FAKE");
		ProductionIntegrationValidator validator = validator(
				prodEnv(), billing, emailResendReady(), corsReady());
		assertThatThrownBy(() -> validator.run(null))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("FAKE");
	}

	@Test
	void rejectsConsoleEmailInProdWithoutOverride() {
		BillingProperties billing = new BillingProperties();
		billing.setEnabled(false);
		EmailProperties email = new EmailProperties();
		email.setProvider("CONSOLE");
		email.setFromEmail("noreply@example.com");
		ProductionIntegrationValidator validator = validator(
				prodEnv(), billing, email, corsReady());
		assertThatThrownBy(() -> validator.run(null))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("CONSOLE");
	}

	@Test
	void allowsConsoleEmailInProdWithExplicitStagingOverride() {
		MockEnvironment env = prodEnv();
		env.setProperty("EMAIL_ALLOW_DEV_PROVIDER", "true");
		BillingProperties billing = new BillingProperties();
		billing.setEnabled(false);
		EmailProperties email = new EmailProperties();
		email.setProvider("CONSOLE");
		email.setFromEmail("noreply@example.com");
		ProductionIntegrationValidator validator = validator(env, billing, email, corsReady());
		assertThatCode(() -> validator.run(null)).doesNotThrowAnyException();
	}

	@Test
	void acceptsDisabledEmailProviderInProd() {
		BillingProperties billing = new BillingProperties();
		billing.setEnabled(false);
		EmailProperties email = new EmailProperties();
		email.setProvider("DISABLED");
		ProductionIntegrationValidator validator = validator(
				prodEnv(), billing, email, corsReady());
		assertThatCode(() -> validator.run(null)).doesNotThrowAnyException();
	}

	@Test
	void rejectsResendWithoutApiKey() {
		BillingProperties billing = new BillingProperties();
		billing.setEnabled(false);
		EmailProperties email = new EmailProperties();
		email.setProvider("RESEND");
		email.setFromEmail("noreply@example.com");
		email.getResend().setApiKey("");
		ProductionIntegrationValidator validator = validator(
				prodEnv(), billing, email, corsReady());
		assertThatThrownBy(() -> validator.run(null))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("RESEND_API_KEY");
	}

	@Test
	void acceptsCompleteRazorpayBillingConfig() {
		BillingProperties billing = new BillingProperties();
		billing.setEnabled(true);
		billing.setProvider("RAZORPAY");
		billing.getRazorpay().setKeyId("rzp_test_x");
		billing.getRazorpay().setKeySecret("secret");
		billing.getRazorpay().setWebhookSecret("whsec");
		billing.getRazorpay().setProMonthlyPlanId("plan_pro");
		EmailProperties email = emailResendReady();
		ProductionIntegrationValidator validator = validator(
				prodEnv(), billing, email, corsReady());
		assertThatCode(() -> validator.run(null)).doesNotThrowAnyException();
	}

	private static MockEnvironment prodEnv() {
		MockEnvironment env = new MockEnvironment();
		env.setActiveProfiles("prod");
		env.setProperty("server.port", "8080");
		return env;
	}

	private static EmailProperties emailResendReady() {
		EmailProperties email = new EmailProperties();
		email.setProvider("RESEND");
		email.setFromEmail("noreply@example.com");
		email.getResend().setApiKey("re_test_key");
		return email;
	}

	private static SecurityProperties corsReady() {
		SecurityProperties security = new SecurityProperties();
		security.getCors().setAllowedOrigins(java.util.List.of("https://app.example.com"));
		return security;
	}

	private static ProductionIntegrationValidator validator(
			Environment env,
			BillingProperties billing,
			EmailProperties email,
			SecurityProperties security) {
		return new ProductionIntegrationValidator(env, billing, email, security);
	}
}
