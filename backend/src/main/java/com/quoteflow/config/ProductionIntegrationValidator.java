package com.quoteflow.config;

import com.quoteflow.billing.BillingProperties;
import com.quoteflow.notification.email.EmailProperties;
import com.quoteflow.security.SecurityProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Locale;

/**
 * Production fail-closed checks for billing/email providers beyond JWT/CORS.
 */
@Component
@Order(50)
public class ProductionIntegrationValidator implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(ProductionIntegrationValidator.class);

	private final Environment environment;
	private final BillingProperties billingProperties;
	private final EmailProperties emailProperties;
	private final SecurityProperties securityProperties;

	public ProductionIntegrationValidator(
			Environment environment,
			BillingProperties billingProperties,
			EmailProperties emailProperties,
			SecurityProperties securityProperties) {
		this.environment = environment;
		this.billingProperties = billingProperties;
		this.emailProperties = emailProperties;
		this.securityProperties = securityProperties;
	}

	@Override
	public void run(ApplicationArguments args) {
		boolean prod = Arrays.stream(environment.getActiveProfiles())
				.anyMatch(p -> p.equalsIgnoreCase("prod"));
		if (!prod) {
			return;
		}

		validateBilling();
		validateEmail();

		log.info(
				"startup.config profile=prod port={} billingEnabled={} billingProvider={} emailProvider={} corsOrigins={}",
				environment.getProperty("server.port"),
				billingProperties.isEnabled(),
				billingProperties.getProvider(),
				emailProperties.getProvider(),
				securityProperties.getCors().getAllowedOrigins());
	}

	private void validateBilling() {
		if (!billingProperties.isEnabled()) {
			return;
		}
		String provider = billingProperties.getProvider() == null
				? ""
				: billingProperties.getProvider().trim().toUpperCase(Locale.ROOT);
		if ("FAKE".equals(provider)) {
			throw new IllegalStateException(
					"Production forbids BILLING_PROVIDER=FAKE when BILLING_ENABLED=true");
		}
		if (!"RAZORPAY".equals(provider)) {
			throw new IllegalStateException(
					"Production BILLING_ENABLED=true requires BILLING_PROVIDER=RAZORPAY");
		}
		BillingProperties.Razorpay rz = billingProperties.getRazorpay();
		boolean ready = StringUtils.hasText(rz.getKeyId())
				&& StringUtils.hasText(rz.getKeySecret())
				&& StringUtils.hasText(rz.getWebhookSecret())
				&& (StringUtils.hasText(rz.getProMonthlyPlanId()) || StringUtils.hasText(rz.getProAnnualPlanId()));
		if (!ready) {
			throw new IllegalStateException(
					"BILLING_ENABLED=true but Razorpay configuration is incomplete");
		}
	}

	private void validateEmail() {
		String provider = emailProperties.getProvider() == null
				? "CONSOLE"
				: emailProperties.getProvider().trim().toUpperCase(Locale.ROOT);
		boolean allowDev = Boolean.parseBoolean(
				environment.getProperty("EMAIL_ALLOW_DEV_PROVIDER", "false"));

		if ("DISABLED".equals(provider)) {
			log.info("email.provider=DISABLED — outbound email unavailable");
			return;
		}
		if ("FAKE".equals(provider) && !allowDev) {
			throw new IllegalStateException(
					"Production forbids EMAIL_PROVIDER=FAKE (set EMAIL_ALLOW_DEV_PROVIDER=true only for isolated staging)");
		}
		if ("CONSOLE".equals(provider) && !allowDev) {
			throw new IllegalStateException(
					"Production forbids EMAIL_PROVIDER=CONSOLE (use RESEND, DISABLED, or EMAIL_ALLOW_DEV_PROVIDER=true for staging)");
		}
		if ("RESEND".equals(provider)) {
			if (!StringUtils.hasText(emailProperties.getResend().getApiKey())) {
				throw new IllegalStateException("EMAIL_PROVIDER=RESEND requires RESEND_API_KEY");
			}
			if (!StringUtils.hasText(emailProperties.getFromEmail())) {
				throw new IllegalStateException("EMAIL_PROVIDER=RESEND requires EMAIL_FROM");
			}
		}
	}
}
