package com.quoteflow.quotation.pdf;

import com.quoteflow.quotation.QuotationStatus;
import org.openpdf.text.Chunk;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
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
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

@Component
public class QuotationPdfRenderer {

	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy");
	private static final float MARGIN = 48f;
	private static final Color HEADER_BG = new Color(245, 247, 250);
	private static final Color LINE = new Color(210, 214, 220);
	private static final Color MUTED = new Color(90, 98, 110);

	private final Font regular;
	private final Font bold;
	private final Font small;
	private final Font smallBold;
	private final Font title;
	private final Font statusFont;

	public QuotationPdfRenderer() {
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
			this.statusFont = new Font(bfBold, 11f, Font.NORMAL, new Color(140, 50, 50));
		} catch (Exception ex) {
			throw new IllegalStateException("Failed to load PDF fonts", ex);
		}
	}

	public byte[] render(QuotationPdfDocument doc) {
		Objects.requireNonNull(doc, "doc");
		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			Document document = new Document(PageSize.A4, MARGIN, MARGIN, MARGIN + 18f, MARGIN + 28f);
			PdfWriter writer = PdfWriter.getInstance(document, out);
			writer.setPageEvent(new FooterEvent(doc.quotationNumber(), small, doc.showQuoteFlowBranding()));
			document.addTitle(doc.quotationNumber());
			document.addCreator("QuoteFlow");
			document.open();

			addHeader(document, doc);
			addParties(document, doc);
			addMeta(document, doc);
			addItemsTable(document, doc);
			addTotals(document, doc);
			addNotesTerms(document, doc);

			document.close();
			return out.toByteArray();
		} catch (DocumentException | IOException ex) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to generate PDF", ex);
		}
	}

	private void addHeader(Document document, QuotationPdfDocument doc) throws DocumentException {
		PdfPTable header = new PdfPTable(2);
		header.setWidthPercentage(100);
		header.setWidths(new float[]{3f, 2f});

		PdfPCell left = new PdfPCell();
		left.setBorder(Rectangle.NO_BORDER);
		left.addElement(new Paragraph(safe(doc.business().name()), title));
		left.addElement(addressBlock(doc.business().email(), doc.business().phone(),
				doc.business().addressLine1(), doc.business().addressLine2(),
				doc.business().city(), doc.business().stateRegion(),
				doc.business().postalCode(), doc.business().countryCode(),
				doc.business().taxId()));
		header.addCell(left);

		PdfPCell right = new PdfPCell();
		right.setBorder(Rectangle.NO_BORDER);
		right.setHorizontalAlignment(Element.ALIGN_RIGHT);
		Paragraph label = new Paragraph("QUOTATION", bold);
		label.setAlignment(Element.ALIGN_RIGHT);
		right.addElement(label);
		Paragraph number = new Paragraph(safe(doc.quotationNumber()), title);
		number.setAlignment(Element.ALIGN_RIGHT);
		right.addElement(number);
		if (doc.status() == QuotationStatus.DRAFT || doc.status() == QuotationStatus.CANCELLED) {
			Paragraph status = new Paragraph(doc.status().name(), statusFont);
			status.setAlignment(Element.ALIGN_RIGHT);
			right.addElement(status);
		} else {
			Paragraph status = new Paragraph(doc.status().name(), smallBold);
			status.setAlignment(Element.ALIGN_RIGHT);
			right.addElement(status);
		}
		header.addCell(right);
		document.add(header);
		document.add(Chunk.NEWLINE);
	}

	private void addParties(Document document, QuotationPdfDocument doc) throws DocumentException {
		PdfPTable parties = new PdfPTable(2);
		parties.setWidthPercentage(100);
		parties.setWidths(new float[]{1f, 1f});

		PdfPCell billTo = sectionCell("Bill to / Customer");
		billTo.addElement(new Paragraph(safe(doc.customer().displayName()), bold));
		if (notBlank(doc.customer().companyName())) {
			billTo.addElement(new Paragraph(safe(doc.customer().companyName()), regular));
		}
		billTo.addElement(addressBlock(doc.customer().email(), doc.customer().phone(),
				doc.customer().addressLine1(), doc.customer().addressLine2(),
				doc.customer().city(), doc.customer().stateRegion(),
				doc.customer().postalCode(), doc.customer().countryCode(),
				doc.customer().taxId()));
		parties.addCell(billTo);

		PdfPCell from = sectionCell("From");
		from.addElement(new Paragraph(safe(doc.business().name()), bold));
		from.addElement(addressBlock(doc.business().email(), doc.business().phone(),
				doc.business().addressLine1(), doc.business().addressLine2(),
				doc.business().city(), doc.business().stateRegion(),
				doc.business().postalCode(), doc.business().countryCode(),
				doc.business().taxId()));
		parties.addCell(from);
		document.add(parties);
		document.add(Chunk.NEWLINE);
	}

	private void addMeta(Document document, QuotationPdfDocument doc) throws DocumentException {
		PdfPTable meta = new PdfPTable(3);
		meta.setWidthPercentage(100);
		meta.addCell(metaCell("Issue date", doc.issueDate() == null ? "—" : DATE_FORMAT.format(doc.issueDate())));
		meta.addCell(metaCell("Valid until", doc.validUntil() == null ? "—" : DATE_FORMAT.format(doc.validUntil())));
		meta.addCell(metaCell("Currency", safe(doc.currency())));
		document.add(meta);
		document.add(Chunk.NEWLINE);
	}

	private void addItemsTable(Document document, QuotationPdfDocument doc) throws DocumentException {
		PdfPTable table = new PdfPTable(4);
		table.setWidthPercentage(100);
		table.setWidths(new float[]{5.2f, 1.2f, 1.8f, 1.8f});
		table.setHeaderRows(1);
		table.addCell(headerCell("Description", Element.ALIGN_LEFT));
		table.addCell(headerCell("Qty", Element.ALIGN_RIGHT));
		table.addCell(headerCell("Unit price", Element.ALIGN_RIGHT));
		table.addCell(headerCell("Amount", Element.ALIGN_RIGHT));

		for (QuotationPdfDocument.LineItem line : doc.lines()) {
			table.addCell(bodyCell(safe(line.description()), Element.ALIGN_LEFT));
			table.addCell(bodyCell(strip(line.quantity()), Element.ALIGN_RIGHT));
			table.addCell(bodyCell(DocumentMoneyFormatter.formatAmount(line.unitPrice(), doc.currency()), Element.ALIGN_RIGHT));
			table.addCell(bodyCell(DocumentMoneyFormatter.formatAmount(line.lineSubtotal(), doc.currency()), Element.ALIGN_RIGHT));
		}
		document.add(table);
		document.add(Chunk.NEWLINE);
	}

	private void addTotals(Document document, QuotationPdfDocument doc) throws DocumentException {
		PdfPTable wrap = new PdfPTable(1);
		wrap.setWidthPercentage(45);
		wrap.setHorizontalAlignment(Element.ALIGN_RIGHT);

		PdfPTable totals = new PdfPTable(2);
		totals.setWidthPercentage(100);
		totals.setWidths(new float[]{1.4f, 1f});
		totals.addCell(totalLabel("Subtotal"));
		totals.addCell(totalValue(DocumentMoneyFormatter.formatAmount(doc.subtotal(), doc.currency())));
		totals.addCell(totalLabel(DocumentMoneyFormatter.discountLabel(doc.discountType(), doc.discountValue())));
		totals.addCell(totalValue(DocumentMoneyFormatter.formatAmount(doc.discountAmount(), doc.currency())));
		totals.addCell(totalLabel(DocumentMoneyFormatter.taxLabel(doc.taxRate())));
		totals.addCell(totalValue(DocumentMoneyFormatter.formatAmount(doc.taxAmount(), doc.currency())));
		totals.addCell(totalLabelBold("Total"));
		totals.addCell(totalValueBold(DocumentMoneyFormatter.formatAmount(doc.totalAmount(), doc.currency())));

		PdfPCell cell = new PdfPCell(totals);
		cell.setBorderColor(LINE);
		cell.setPadding(8f);
		wrap.addCell(cell);
		document.add(wrap);
	}

	private void addNotesTerms(Document document, QuotationPdfDocument doc) throws DocumentException {
		if (notBlank(doc.notes())) {
			document.add(Chunk.NEWLINE);
			document.add(new Paragraph("Notes", bold));
			document.add(plainParagraph(doc.notes()));
		}
		if (notBlank(doc.terms())) {
			document.add(Chunk.NEWLINE);
			document.add(new Paragraph("Terms", bold));
			document.add(plainParagraph(doc.terms()));
		}
	}

	private Paragraph plainParagraph(String text) {
		Paragraph p = new Paragraph(safe(text), regular);
		p.setLeading(14f);
		return p;
	}

	private Paragraph addressBlock(
			String email,
			String phone,
			String line1,
			String line2,
			String city,
			String state,
			String postal,
			String country,
			String taxId) {
		Paragraph p = new Paragraph();
		p.setLeading(13f);
		if (notBlank(line1)) {
			p.add(new Chunk(safe(line1) + "\n", regular));
		}
		if (notBlank(line2)) {
			p.add(new Chunk(safe(line2) + "\n", regular));
		}
		String cityLine = joinNonBlank(", ", city, state, postal);
		if (notBlank(cityLine)) {
			p.add(new Chunk(cityLine + "\n", regular));
		}
		if (notBlank(country)) {
			p.add(new Chunk(safe(country) + "\n", regular));
		}
		if (notBlank(email)) {
			p.add(new Chunk(safe(email) + "\n", small));
		}
		if (notBlank(phone)) {
			p.add(new Chunk(safe(phone) + "\n", small));
		}
		if (notBlank(taxId)) {
			p.add(new Chunk("Tax ID: " + safe(taxId) + "\n", small));
		}
		return p;
	}

	private PdfPCell sectionCell(String title) {
		PdfPCell cell = new PdfPCell();
		cell.setBorderColor(LINE);
		cell.setPadding(8f);
		cell.addElement(new Paragraph(title, smallBold));
		return cell;
	}

	private PdfPCell metaCell(String label, String value) {
		PdfPCell cell = new PdfPCell();
		cell.setBorderColor(LINE);
		cell.setBackgroundColor(HEADER_BG);
		cell.setPadding(6f);
		cell.addElement(new Paragraph(label, small));
		cell.addElement(new Paragraph(safe(value), bold));
		return cell;
	}

	private PdfPCell headerCell(String text, int align) {
		PdfPCell cell = new PdfPCell(new Phrase(text, smallBold));
		cell.setBackgroundColor(HEADER_BG);
		cell.setBorderColor(LINE);
		cell.setPadding(6f);
		cell.setHorizontalAlignment(align);
		return cell;
	}

	private PdfPCell bodyCell(String text, int align) {
		PdfPCell cell = new PdfPCell(new Phrase(safe(text), regular));
		cell.setBorderColor(LINE);
		cell.setPadding(6f);
		cell.setHorizontalAlignment(align);
		cell.setVerticalAlignment(Element.ALIGN_TOP);
		return cell;
	}

	private PdfPCell totalLabel(String text) {
		PdfPCell cell = new PdfPCell(new Phrase(text, regular));
		cell.setBorder(Rectangle.NO_BORDER);
		cell.setPadding(3f);
		return cell;
	}

	private PdfPCell totalLabelBold(String text) {
		PdfPCell cell = new PdfPCell(new Phrase(text, bold));
		cell.setBorder(Rectangle.NO_BORDER);
		cell.setPadding(3f);
		return cell;
	}

	private PdfPCell totalValue(String text) {
		PdfPCell cell = new PdfPCell(new Phrase(text, regular));
		cell.setBorder(Rectangle.NO_BORDER);
		cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
		cell.setPadding(3f);
		return cell;
	}

	private PdfPCell totalValueBold(String text) {
		PdfPCell cell = new PdfPCell(new Phrase(text, bold));
		cell.setBorder(Rectangle.NO_BORDER);
		cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
		cell.setPadding(3f);
		return cell;
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

	private static String strip(BigDecimal value) {
		return value == null ? "" : value.stripTrailingZeros().toPlainString();
	}

	private static byte[] readFont(String classpath) throws IOException {
		ClassPathResource resource = new ClassPathResource(classpath);
		try (InputStream in = resource.getInputStream()) {
			return in.readAllBytes();
		}
	}

	private static final class FooterEvent extends PdfPageEventHelper {
		private final String quotationNumber;
		private final Font font;
		private final boolean showQuoteFlowBranding;
		private int page;

		private FooterEvent(String quotationNumber, Font font, boolean showQuoteFlowBranding) {
			this.quotationNumber = quotationNumber;
			this.font = font;
			this.showQuoteFlowBranding = showQuoteFlowBranding;
		}

		@Override
		public void onEndPage(PdfWriter writer, Document document) {
			page++;
			try {
				PdfPTable footer = new PdfPTable(2);
				footer.setTotalWidth(document.right() - document.left());
				footer.setWidths(new float[]{3f, 2f});
				String leftText = showQuoteFlowBranding
						? safe(quotationNumber) + "  ·  Generated with QuoteFlow"
						: safe(quotationNumber);
				PdfPCell left = new PdfPCell(new Phrase(leftText, font));
				left.setBorder(Rectangle.NO_BORDER);
				PdfPCell right = new PdfPCell(new Phrase("Page " + page, font));
				right.setBorder(Rectangle.NO_BORDER);
				right.setHorizontalAlignment(Element.ALIGN_RIGHT);
				footer.addCell(left);
				footer.addCell(right);
				footer.writeSelectedRows(0, -1, document.left(), document.bottom() - 8f, writer.getDirectContent());
			} catch (DocumentException ignored) {
				// Footer failure must not corrupt the document mid-render; page number is best-effort.
			}
		}
	}
}
