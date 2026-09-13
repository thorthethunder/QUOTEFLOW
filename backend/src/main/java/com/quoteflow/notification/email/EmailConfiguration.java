package com.quoteflow.notification.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.StringUtils;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(EmailProperties.class)
public class EmailConfiguration implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(EmailConfiguration.class);

	private final EmailProperties properties;
	private final EmailProvider emailProvider;

	public EmailConfiguration(EmailProperties properties, EmailProvider emailProvider) {
		this.properties = properties;
		this.emailProvider = emailProvider;
	}

	@Override
	public void run(ApplicationArguments args) {
		String provider = properties.getProvider() == null ? "CONSOLE" : properties.getProvider().toUpperCase();
		if ("RESEND".equals(provider) && !StringUtils.hasText(properties.getResend().getApiKey())) {
			log.warn("email.configuration_incomplete provider=RESEND — sends will fail until API key is set");
		} else {
			log.info("email.provider={} fromConfigured={} workerEnabled={}",
					emailProvider.providerName(),
					StringUtils.hasText(properties.getFromEmail()),
					properties.isWorkerEnabled());
		}
	}
}
