package com.quoteflow.ai.knowledge;

import com.quoteflow.ai.knowledge.dto.KnowledgeAskRequest;
import com.quoteflow.ai.knowledge.dto.KnowledgeAskResponse;
import com.quoteflow.ai.knowledge.dto.KnowledgeDocumentDto;
import com.quoteflow.ai.knowledge.dto.KnowledgeTextRequest;
import com.quoteflow.security.AuthenticatedUser;
import com.quoteflow.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(path = "/api/v1/knowledge", produces = MediaType.APPLICATION_JSON_VALUE)
public class KnowledgeController {

	private final KnowledgeService knowledgeService;

	public KnowledgeController(KnowledgeService knowledgeService) {
		this.knowledgeService = knowledgeService;
	}

	@PostMapping(path = "/text", consumes = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public KnowledgeDocumentDto createText(@Valid @RequestBody KnowledgeTextRequest request) {
		return knowledgeService.createText(SecurityUtils.requireCurrentUser(), request);
	}

	@PostMapping(path = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public KnowledgeDocumentDto uploadTextFile(
			@RequestParam(required = false) String title,
			@RequestParam("file") MultipartFile file) {
		return knowledgeService.uploadTextFile(SecurityUtils.requireCurrentUser(), title, file);
	}

	@GetMapping("/documents")
	public List<KnowledgeDocumentDto> list() {
		return knowledgeService.list(SecurityUtils.requireCurrentUser());
	}

	@GetMapping("/documents/{id}")
	public KnowledgeDocumentDto get(@PathVariable UUID id) {
		return knowledgeService.get(SecurityUtils.requireCurrentUser(), id);
	}

	@PutMapping(path = "/documents/{id}/text", consumes = MediaType.APPLICATION_JSON_VALUE)
	public KnowledgeDocumentDto replaceText(
			@PathVariable UUID id,
			@Valid @RequestBody KnowledgeTextRequest request) {
		return knowledgeService.replaceText(SecurityUtils.requireCurrentUser(), id, request);
	}

	@DeleteMapping("/documents/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable UUID id) {
		knowledgeService.delete(SecurityUtils.requireCurrentUser(), id);
	}

	@PostMapping(path = "/ask", consumes = MediaType.APPLICATION_JSON_VALUE)
	public KnowledgeAskResponse ask(@Valid @RequestBody KnowledgeAskRequest request) {
		return knowledgeService.ask(SecurityUtils.requireCurrentUser(), request);
	}
}
