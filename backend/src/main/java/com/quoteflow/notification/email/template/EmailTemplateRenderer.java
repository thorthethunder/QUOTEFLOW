package com.quoteflow.notification.email.template;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class EmailTemplateRenderer {

	private final ObjectMapper objectMapper;

	public EmailTemplateRenderer(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public RenderedEmail render(String templateKey, String varsJson, boolean showQuoteFlowBranding) {
		Map<String, String> vars = parse(varsJson);
		return switch (templateKey) {
			case "quotation_email" -> quotation(vars, showQuoteFlowBranding);
			case "invoice_reminder_friendly", "invoice_reminder_standard", "invoice_reminder_firm" ->
					reminder(templateKey, vars, showQuoteFlowBranding);
			default -> throw new IllegalArgumentException("Unknown template: " + templateKey);
		};
	}

	private RenderedEmail quotation(Map<String, String> vars, boolean branding) {
		String business = esc(vars, "businessName");
		String customer = esc(vars, "customerName");
		String number = esc(vars, "documentNumber");
		String total = esc(vars, "totalDisplay");
		String issueDate = esc(vars, "issueDate");
		String message = esc(vars, "customMessage");
		String subject = "Quotation " + plain(vars, "documentNumber") + " from " + plain(vars, "businessName");
		subject = sanitizeSubject(subject);

		StringBuilder text = new StringBuilder();
		text.append("Hello ").append(plain(vars, "customerName")).append(",\n\n");
		text.append(plain(vars, "businessName")).append(" has sent you quotation ")
				.append(plain(vars, "documentNumber")).append(".\n");
		text.append("Issue date: ").append(plain(vars, "issueDate")).append("\n");
		text.append("Total: ").append(plain(vars, "totalDisplay")).append("\n");
		if (!plain(vars, "customMessage").isBlank()) {
			text.append("\n").append(plain(vars, "customMessage")).append("\n");
		}
		text.append("\nThe quotation PDF is attached.\n");
		if (branding) {
			text.append("\nSent with QuoteFlow\n");
		}

		String html = """
				<!DOCTYPE html><html><body style="font-family:Arial,sans-serif;color:#1a1a1a;line-height:1.5">
				<p>Hello %s,</p>
				<p><strong>%s</strong> has sent you quotation <strong>%s</strong>.</p>
				<p>Issue date: %s<br/>Total: %s</p>
				%s
				<p>The quotation PDF is attached.</p>
				%s
				</body></html>
				""".formatted(
				customer,
				business,
				number,
				issueDate,
				total,
				message.isBlank() ? "" : "<p>" + message.replace("\n", "<br/>") + "</p>",
				branding ? "<p style=\"color:#666;font-size:12px\">Sent with QuoteFlow</p>" : "");

		return new RenderedEmail(subject, html, text.toString());
	}

	private RenderedEmail reminder(String templateKey, Map<String, String> vars, boolean branding) {
		String business = esc(vars, "businessName");
		String customer = esc(vars, "customerName");
		String number = esc(vars, "documentNumber");
		String balance = esc(vars, "balanceDisplay");
		String note = esc(vars, "customMessage");
		String tone = switch (templateKey) {
			case "invoice_reminder_friendly" ->
					"Just a friendly reminder that invoice <strong>%s</strong> has an outstanding balance of <strong>%s</strong>."
							.formatted(number, balance);
			case "invoice_reminder_firm" ->
					"This is a reminder that invoice <strong>%s</strong> still has an outstanding balance of <strong>%s</strong>. Please arrange payment at your earliest convenience."
							.formatted(number, balance);
			default ->
					"Reminder: invoice <strong>%s</strong> has an outstanding balance of <strong>%s</strong>."
							.formatted(number, balance);
		};
		String toneText = switch (templateKey) {
			case "invoice_reminder_friendly" ->
					"Just a friendly reminder that invoice " + plain(vars, "documentNumber")
							+ " has an outstanding balance of " + plain(vars, "balanceDisplay") + ".";
			case "invoice_reminder_firm" ->
					"This is a reminder that invoice " + plain(vars, "documentNumber")
							+ " still has an outstanding balance of " + plain(vars, "balanceDisplay")
							+ ". Please arrange payment at your earliest convenience.";
			default ->
					"Reminder: invoice " + plain(vars, "documentNumber")
							+ " has an outstanding balance of " + plain(vars, "balanceDisplay") + ".";
		};
		String subject = sanitizeSubject(
				"Payment reminder: " + plain(vars, "documentNumber") + " from " + plain(vars, "businessName"));

		StringBuilder text = new StringBuilder();
		text.append("Hello ").append(plain(vars, "customerName")).append(",\n\n");
		text.append(toneText).append("\n");
		text.append("From: ").append(plain(vars, "businessName")).append("\n");
		if (!plain(vars, "customMessage").isBlank()) {
			text.append("\n").append(plain(vars, "customMessage")).append("\n");
		}
		if (branding) {
			text.append("\nSent with QuoteFlow\n");
		}

		String html = """
				<!DOCTYPE html><html><body style="font-family:Arial,sans-serif;color:#1a1a1a;line-height:1.5">
				<p>Hello %s,</p>
				<p>%s</p>
				<p>From: <strong>%s</strong></p>
				%s
				%s
				</body></html>
				""".formatted(
				customer,
				tone,
				business,
				note.isBlank() ? "" : "<p>" + note.replace("\n", "<br/>") + "</p>",
				branding ? "<p style=\"color:#666;font-size:12px\">Sent with QuoteFlow</p>" : "");

		return new RenderedEmail(subject, html, text.toString());
	}

	private Map<String, String> parse(String varsJson) {
		try {
			return objectMapper.readValue(varsJson, new TypeReference<>() {
			});
		} catch (Exception ex) {
			throw new IllegalStateException("Invalid template vars", ex);
		}
	}

	private static String esc(Map<String, String> vars, String key) {
		return HtmlEscaper.escape(plain(vars, key));
	}

	private static String plain(Map<String, String> vars, String key) {
		String value = vars.get(key);
		return value == null ? "" : value;
	}

	public static String sanitizeSubject(String subject) {
		if (subject == null) {
			return "";
		}
		return subject.replaceAll("[\\r\\n]+", " ").trim();
	}

	public record RenderedEmail(String subject, String htmlBody, String textBody) {
	}
}
