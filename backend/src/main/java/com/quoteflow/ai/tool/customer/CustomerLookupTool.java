package com.quoteflow.ai.tool.customer;

import com.quoteflow.ai.copilot.dto.BusinessCopilotReference;
import com.quoteflow.ai.tool.AiToolCategory;
import com.quoteflow.ai.tool.CopilotToolContext;
import com.quoteflow.ai.tool.QuoteFlowAiTool;
import com.quoteflow.customer.CustomerService;
import com.quoteflow.customer.CustomerStatus;
import com.quoteflow.customer.dto.CustomerSummaryResponse;
import com.quoteflow.customer.dto.PagedCustomerResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class CustomerLookupTool implements QuoteFlowAiTool {

	public static final String NAME = "customer_lookup";

	private final CustomerService customerService;

	public CustomerLookupTool(CustomerService customerService) {
		this.customerService = customerService;
	}

	@Override
	public String name() {
		return NAME;
	}

	@Override
	public String description() {
		return "Find customers in the authenticated business by name or company. "
				+ "Use for questions like 'Find Raj Electrical' or 'Do I have a customer called…'. "
				+ "Returns a bounded list; never dumps all customers.";
	}

	@Override
	public AiToolCategory category() {
		return AiToolCategory.READ_ONLY;
	}

	@Override
	public Class<?> inputType() {
		return CustomerLookupInput.class;
	}

	@Override
	public Object execute(Object input, CopilotToolContext context) {
		CustomerLookupInput args = (CustomerLookupInput) input;
		String query = StringUtils.hasText(args.query()) ? args.query().trim() : null;
		int limit = Math.min(args.limit() == null ? 10 : args.limit(), 20);

		PagedCustomerResponse page = customerService.list(
				context.principal(),
				query,
				CustomerStatus.ACTIVE,
				0,
				limit,
				"displayName,asc");

		List<Map<String, Object>> customers = new ArrayList<>();
		for (CustomerSummaryResponse c : page.content()) {
			context.addReference(BusinessCopilotReference.customer(c.id(), c.displayName()));
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("id", c.id().toString());
			row.put("displayName", c.displayName());
			row.put("companyName", c.companyName());
			row.put("status", c.status() == null ? null : c.status().name());
			// Intentionally omit email/phone/tax to reduce unnecessary PII in model context.
			customers.add(row);
		}

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("totalMatched", page.totalElements());
		result.put("returned", customers.size());
		result.put("bounded", true);
		result.put("customers", customers);
		if (query == null) {
			result.put("note", "No query provided; returned a bounded sample of active customers only.");
		}
		return result;
	}
}
