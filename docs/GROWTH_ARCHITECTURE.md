# QuoteFlow Growth Architecture

## Status

**FUTURE.** Phase 1 provides only a professional public landing shell. No advertising trackers, analytics SDKs, or SEO tooling are implemented yet.

## Product loop (target)

```text
Discovery → Marketing → Signup → Activation → Quotation → Invoice
→ Payment → Subscription → Retention → Referral → Growth
```

Technology supports this loop; it is not a substitute for it.

## IMPLEMENTED (Phase 1)

- Public landing with product value messaging (not framework marketing)
- Placeholder CTAs (disabled) for Features / Pricing / Login / Get started

## FUTURE public site

Landing, Features, Pricing, Use Cases, FAQ, Security, Contact, Login, Register, Privacy, Terms, later Blog/Resources.

SEO basics when pages exist: titles, meta, canonical, Open Graph, sitemap, robots, semantic HTML, Core Web Vitals. Authenticated app routes should generally remain non-indexed.

## FUTURE acquisition

- Content (templates, guides) outside core financial services
- Free tools funnel → signup → activation
- Subtle “Generated with QuoteFlow” on free-plan docs where professional
- Referrals only after legal/tax review for rewards

## FUTURE analytics (privacy-conscious)

Events such as `registration_completed`, `quotation_created`, `payment_recorded`, `subscription_upgraded` — **never** send confidential document/customer content to ad platforms.

Separate transactional/product analytics from advertising pixels. Do not load unnecessary marketing scripts inside authenticated financial dashboards.

UTM attribution fields may be stored when useful (`utm_source`, etc.).

## Advertising budget posture (FUTURE)

Prefer ₹0–₹2,000/month organic/SEO/community until activation and billing are measurable; then small paid tests. Scale only with CAC/LTV evidence.
