package com.quoteflow.quotation.pdf;

public final class PdfFilename {

	private PdfFilename() {
	}

	/** Safe Content-Disposition filename from quotation number only. */
	public static String fromQuotationNumber(String quotationNumber) {
		return fromDocumentNumber(quotationNumber, "quotation");
	}

	/** Safe Content-Disposition filename from receipt number only. */
	public static String fromReceiptNumber(String receiptNumber) {
		return fromDocumentNumber(receiptNumber, "receipt");
	}

	private static String fromDocumentNumber(String documentNumber, String fallback) {
		String raw = documentNumber == null ? fallback : documentNumber.trim();
		String sanitized = raw.replaceAll("[^A-Za-z0-9._-]", "_");
		if (sanitized.isBlank()) {
			sanitized = fallback;
		}
		if (sanitized.length() > 64) {
			sanitized = sanitized.substring(0, 64);
		}
		return sanitized + ".pdf";
	}
}
