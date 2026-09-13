package com.quoteflow.quotation.pdf;

import com.quoteflow.quotation.Quotation;
import com.quoteflow.quotation.QuotationRepository;
import com.quoteflow.quotation.QuotationStatus;
import com.quoteflow.security.AuthenticatedUser;
import com.quoteflow.subscription.EntitlementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class QuotationPdfService {

	private static final Logger log = LoggerFactory.getLogger(QuotationPdfService.class);

	private final QuotationRepository quotationRepository;
	private final QuotationPdfDocumentFactory documentFactory;
	private final QuotationPdfRenderer renderer;
	private final EntitlementService entitlementService;

	public QuotationPdfService(
			QuotationRepository quotationRepository,
			QuotationPdfDocumentFactory documentFactory,
			QuotationPdfRenderer renderer,
			EntitlementService entitlementService) {
		this.quotationRepository = quotationRepository;
		this.documentFactory = documentFactory;
		this.renderer = renderer;
		this.entitlementService = entitlementService;
	}

	@Transactional(readOnly = true)
	public GeneratedPdf generate(AuthenticatedUser principal, UUID quotationId) {
		return generateForBusiness(principal.getBusinessId(), quotationId);
	}

	/**
	 * Tenant-scoped PDF generation for outbox delivery (no authenticated user on worker thread).
	 */
	@Transactional(readOnly = true)
	public GeneratedPdf generateForBusiness(UUID businessId, UUID quotationId) {
		Quotation quotation = quotationRepository.findDetailByIdAndBusinessId(quotationId, businessId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Quotation not found"));

		boolean branding = quotation.getStatus() == QuotationStatus.DRAFT
				? entitlementService.showQuoteFlowBranding(businessId)
				: quotation.isShowQuoteFlowBranding();
		QuotationPdfDocument document = documentFactory.from(quotation, branding);
		byte[] bytes = renderer.render(document);
		String filename = PdfFilename.fromQuotationNumber(quotation.getQuotationNumber());
		log.info("quotation.event=pdf_generated quotationId={} businessId={} bytes={}",
				quotation.getId(), businessId, bytes.length);
		return new GeneratedPdf(bytes, filename);
	}

	public record GeneratedPdf(byte[] content, String filename) {
	}
}
