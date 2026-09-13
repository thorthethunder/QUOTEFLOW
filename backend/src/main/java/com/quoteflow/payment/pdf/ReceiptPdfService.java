package com.quoteflow.payment.pdf;

import com.quoteflow.payment.Payment;
import com.quoteflow.payment.PaymentRepository;
import com.quoteflow.quotation.pdf.PdfFilename;
import com.quoteflow.security.AuthenticatedUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class ReceiptPdfService {

	private static final Logger log = LoggerFactory.getLogger(ReceiptPdfService.class);

	private final PaymentRepository paymentRepository;
	private final ReceiptPdfRenderer renderer;

	public ReceiptPdfService(PaymentRepository paymentRepository, ReceiptPdfRenderer renderer) {
		this.paymentRepository = paymentRepository;
		this.renderer = renderer;
	}

	@Transactional(readOnly = true)
	public GeneratedPdf generate(AuthenticatedUser principal, UUID paymentId) {
		Payment payment = paymentRepository.findDetailByIdAndBusinessId(paymentId, principal.getBusinessId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"));
		ReceiptPdfDocument document = ReceiptPdfDocument.from(payment, payment.getInvoice());
		byte[] bytes = renderer.render(document);
		String filename = PdfFilename.fromReceiptNumber(payment.getReceiptNumber());
		log.info("payment.event=receipt_pdf_generated paymentId={} businessId={} bytes={}",
				payment.getId(), principal.getBusinessId(), bytes.length);
		return new GeneratedPdf(bytes, filename);
	}

	public record GeneratedPdf(byte[] content, String filename) {
	}
}
