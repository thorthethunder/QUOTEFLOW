package com.quoteflow.billing;

import com.quoteflow.billing.provider.BillingProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import java.util.Arrays;

@Configuration
@EnableConfigurationProperties(BillingProperties.class)
public class BillingConfiguration implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(BillingConfiguration.class);

	private final BillingProperties properties;
	private final Environment environment;
	private final BillingProvider billingProvider;

	public BillingConfiguration(
			BillingProperties properties,
			Environment environment,
			BillingProvider billingProvider) {
		this.properties = properties;
		this.environment = environment;
		this.billingProvider = billingProvider;
	}

	@Override
	public void run(ApplicationArguments args) {
		boolean prod = Arrays.stream(environment.getActiveProfiles()).anyMatch(p -> p.equalsIgnoreCase("prod"));
		if (!properties.isEnabled()) {
			log.info("billing.disabled providerBean={}", billingProvider.providerName());
			return;
		}
		if ("FAKE".equalsIgnoreCase(properties.getProvider())) {
			if (prod) {
				throw new IllegalStateException(
						"Production forbids BILLING_PROVIDER=FAKE when BILLING_ENABLED=true");
			}
			log.info("billing.enabled provider=FAKE");
			return;
		}
		BillingProperties.Razorpay rz = properties.getRazorpay();
		boolean ready = StringUtils.hasText(rz.getKeyId())
				&& StringUtils.hasText(rz.getKeySecret())
				&& StringUtils.hasText(rz.getWebhookSecret())
				&& (StringUtils.hasText(rz.getProMonthlyPlanId()) || StringUtils.hasText(rz.getProAnnualPlanId()));
		if (!ready) {
			if (prod) {
				throw new IllegalStateException("BILLING_ENABLED=true but Razorpay configuration is incomplete");
			}
			log.warn("billing.configuration_incomplete — checkout will return BILLING_CONFIGURATION_ERROR");
		} else {
			log.info("billing.enabled provider={}", billingProvider.providerName());
		}
	}
}
