package com.quoteflow.payment.pdf;

import com.quoteflow.payment.PaymentRecordStatus;
import com.quoteflow.quotation.pdf.DocumentMoneyFormatter;
import org.openpdf.text.Chunk;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.BaseFont;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfPageEventHelper;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

@Component
public class ReceiptPdfRenderer {

	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy");
	private static final float MARGIN = 48f;
	private static final Color LINE = new Color(210, 214, 220);
	private static final Color MUTED = new Color(90, 98, 110);

	private final Font regular;
	private final Font bold;
	private final Font small;
	private final Font smallBold;
	private final Font title;
	private final Font voidFont;

	public ReceiptPdfRenderer() {
		try {
			byte[] regularBytes = readFont("fonts/DejaVuSans.ttf");
			byte[] boldBytes = readFont("fonts/DejaVuSans-Bold.ttf");
			BaseFont bfRegular = BaseFont.createFont("DejaVuSans.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, regularBytes, null);
			BaseFont bfBold = BaseFont.createFont("DejaVuSans-Bold.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, boldBytes, null);
			this.regular = new Font(bfRegular, 10f);
			this.bold = new Font(bfBold, 10f);
			this.small = new Font(bfRegular, 8.5f, Font.NORMAL, MUTED);
			this.smallBold = new Font(bfBold, 8.5f);
			this.title = new Font(bfBold, 16f);
			this.voidFont = new Font(bfBold, 14f, Font.NORMAL, new Color(140, 50, 50));
		} catch (Exception ex) {
			throw new IllegalStateException("Failed to load PDF fonts", ex);
		}
	}

	public byte[] render(ReceiptPdfDocument doc) {
		Objects.requireNonNull(doc, "doc");
		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			Document document = new Document(PageSize.A4, MARGIN, MARGIN, MARGIN + 18f, MARGIN + 28f);
			PdfWriter writer = PdfWriter.getInstance(document, out);
			writer.setPageEvent(new FooterEvent(doc.receiptNumber(), small, doc.showQuoteFlowBranding()));
			document.addTitle(doc.receiptNumber());
			document.addCreator("QuoteFlow");
			document.open();

			addHeader(document, doc);
			addParties(document, doc);
			addPaymentDetails(document, doc);
			addMoney(document, doc);

			document.close();
			return out.toByteArray();
		} catch (DocumentException | IOException ex) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to generate receipt PDF", ex);
		}
	}

	private void addHeader(Document document, ReceiptPdfDocument doc) throws DocumentException {
		PdfPTable header = new PdfPTable(2);
		header.setWidthPercentage(100);
		header.setWidths(new float[]{3f, 2f});

		PdfPCell left = new PdfPCell();
		left.setBorder(Rectangle.NO_BORDER);
		left.addElement(new Paragraph(safe(doc.business().name()), title));
		left.addElement(contactLines(doc.business()));
		header.addCell(left);

		PdfPCell right = new PdfPCell();
		right.setBorder(Rectangle.NO_BORDER);
		right.setHorizontalAlignment(Element.ALIGN_RIGHT);
		Paragraph label = new Paragraph("RECEIPT", bold);
		label.setAlignment(Element.ALIGN_RIGHT);
		right.addElement(label);
		Paragraph number = new Paragraph(safe(doc.receiptNumber()), title);
		number.setAlignment(Element.ALIGN_RIGHT);
		right.addElement(number);
		if (doc.paymentStatus() == PaymentRecordStatus.VOIDED) {
			Paragraph voided = new Paragraph("VOIDED", voidFont);
			voided.setAlignment(Element.ALIGN_RIGHT);
			right.addElement(voided);
		}
		header.addCell(right);
		document.add(header);
		document.add(Chunk.NEWLINE);
	}

	private void addParties(Document document, ReceiptPdfDocument doc) throws DocumentException {
		PdfPTable parties = new PdfPTable(2);
		parties.setWidthPercentage(100);
		parties.setWidths(new float[]{1f, 1f});

		PdfPCell receivedFrom = boxed("Received from");
		receivedFrom.addElement(new Paragraph(safe(doc.customer().name()), bold));
		if (notBlank(doc.customer().companyName())) {
			receivedFrom.addElement(new Paragraph(safe(doc.customer().companyName()), regular));
		}
		receivedFrom.addElement(contactLines(doc.customer()));
		parties.addCell(receivedFrom);

		PdfPCell business = boxed("Business");
		business.addElement(new Paragraph(safe(doc.business().name()), bold));
		business.addElement(contactLines(doc.business()));
		parties.addCell(business);
		document.add(parties);
		document.add(Chunk.NEWLINE);
	}

	private void addPaymentDetails(Document document, ReceiptPdfDocument doc) throws DocumentException {
		PdfPTable meta = new PdfPTable(2);
		meta.setWidthPercentage(100);
		meta.addCell(metaCell("Invoice", safe(doc.invoiceNumber())));
		meta.addCell(metaCell("Payment date", doc.paymentDate() == null ? "—" : DATE_FORMAT.format(doc.paymentDate())));
		meta.addCell(metaCell("Method", humanMethod(doc.paymentMethod())));
		meta.addCell(metaCell("Reference", notBlank(doc.reference()) ? safe(doc.reference()) : "—"));
		document.add(meta);
		document.add(Chunk.NEWLINE);
	}

	private void addMoney(Document document, ReceiptPdfDocument doc) throws DocumentException {
		PdfPTable wrap = new PdfPTable(1);
		wrap.setWidthPercentage(55);
		wrap.setHorizontalAlignment(Element.ALIGN_RIGHT);

		PdfPTable totals = new PdfPTable(2);
		totals.setWidthPercentage(100);
		totals.setWidths(new float[]{1.6f, 1f});
		totals.addCell(totalLabel("Invoice total"));
		totals.addCell(totalValue(DocumentMoneyFormatter.formatAmount(doc.invoiceTotal(), doc.currency())));
		totals.addCell(totalLabel("Previous amount paid"));
		totals.addCell(totalValue(DocumentMoneyFormatter.formatAmount(doc.previousPaidAmount(), doc.currency())));
		totals.addCell(totalLabelBold("Amount received"));
		totals.addCell(totalValueBold(DocumentMoneyFormatter.formatAmount(doc.paymentAmount(), doc.currency())));
		totals.addCell(totalLabel("Remaining balance"));
		totals.addCell(totalValue(DocumentMoneyFormatter.formatAmount(doc.remainingBalanceAfterPayment(), doc.currency())));

		PdfPCell cell = new PdfPCell(totals);
		cell.setBorderColor(LINE);
		cell.setPadding(8f);
		wrap.addCell(cell);
		document.add(wrap);
	}

	private Paragraph contactLines(ReceiptPdfDocument.Party party) {
		Paragraph p = new Paragraph();
		p.setLeading(13f);
		if (notBlank(party.addressLine1())) {
			p.add(new Chunk(safe(party.addressLine1()) + "\n", regular));
		}
		if (notBlank(party.addressLine2())) {
			p.add(new Chunk(safe(party.addressLine2()) + "\n", regular));
		}
		String cityLine = joinNonBlank(", ", party.city(), party.stateRegion(), party.postalCode());
		if (notBlank(cityLine)) {
			p.add(new Chunk(cityLine + "\n", regular));
		}
		if (notBlank(party.countryCode())) {
			p.add(new Chunk(safe(party.countryCode()) + "\n", regular));
		}
		if (notBlank(party.email())) {
			p.add(new Chunk(safe(party.email()) + "\n", small));
		}
		if (notBlank(party.phone())) {
			p.add(new Chunk(safe(party.phone()) + "\n", small));
		}
		if (notBlank(party.taxId())) {
			p.add(new Chunk("Tax ID: " + safe(party.taxId()) + "\n", small));
		}
		return p;
	}

	private PdfPCell boxed(String title) {
		PdfPCell cell = new PdfPCell();
		cell.setBorderColor(LINE);
		cell.setPadding(8f);
		cell.addElement(new Paragraph(title, smallBold));
		return cell;
	}

	private PdfPCell metaCell(String label, String value) {
		PdfPCell cell = new PdfPCell();
		cell.setBorderColor(LINE);
		cell.setPadding(6f);
		cell.addElement(new Paragraph(label, smallBold));
		cell.addElement(new Paragraph(value, regular));
		return cell;
	}

	private PdfPCell totalLabel(String text) {
		PdfPCell cell = new PdfPCell(new Paragraph(text, regular));
		cell.setBorder(Rectangle.NO_BORDER);
		cell.setPadding(3f);
		return cell;
	}

	private PdfPCell totalLabelBold(String text) {
		PdfPCell cell = new PdfPCell(new Paragraph(text, bold));
		cell.setBorder(Rectangle.NO_BORDER);
		cell.setPadding(3f);
		return cell;
	}

	private PdfPCell totalValue(String text) {
		PdfPCell cell = new PdfPCell(new Paragraph(text, regular));
		cell.setBorder(Rectangle.NO_BORDER);
		cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
		cell.setPadding(3f);
		return cell;
	}

	private PdfPCell totalValueBold(String text) {
		PdfPCell cell = new PdfPCell(new Paragraph(text, bold));
		cell.setBorder(Rectangle.NO_BORDER);
		cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
		cell.setPadding(3f);
		return cell;
	}

	private static String humanMethod(String method) {
		if (method == null) {
			return "—";
		}
		return switch (method) {
			case "CASH" -> "Cash";
			case "BANK_TRANSFER" -> "Bank transfer";
			case "UPI_MANUAL" -> "UPI (manual)";
			case "CHEQUE" -> "Cheque";
			case "OTHER" -> "Other";
			default -> method;
		};
	}

	private static String safe(String value) {
		return value == null ? "" : value;
	}

	private static boolean notBlank(String value) {
		return value != null && !value.isBlank();
	}

	private static String joinNonBlank(String sep, String... parts) {
		StringBuilder sb = new StringBuilder();
		for (String part : parts) {
			if (notBlank(part)) {
				if (!sb.isEmpty()) {
					sb.append(sep);
				}
				sb.append(part.trim());
			}
		}
		return sb.toString();
	}

	private static byte[] readFont(String path) throws IOException {
		ClassPathResource resource = new ClassPathResource(path);
		try (InputStream in = resource.getInputStream()) {
			return in.readAllBytes();
		}
	}

	private static final class FooterEvent extends PdfPageEventHelper {
		private final String receiptNumber;
		private final Font font;
		private final boolean showQuoteFlowBranding;

		private FooterEvent(String receiptNumber, Font font, boolean showQuoteFlowBranding) {
			this.receiptNumber = receiptNumber;
			this.font = font;
			this.showQuoteFlowBranding = showQuoteFlowBranding;
		}

		@Override
		public void onEndPage(PdfWriter writer, Document document) {
			String text = showQuoteFlowBranding ? receiptNumber + " · QuoteFlow" : receiptNumber;
			org.openpdf.text.pdf.ColumnText.showTextAligned(
					writer.getDirectContent(),
					Element.ALIGN_CENTER,
					new org.openpdf.text.Phrase(text, font),
					(document.right() + document.left()) / 2,
					document.bottom() - 12,
					0);
		}
	}
}
