import { Component } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatToolbarModule } from '@angular/material/toolbar';
import { RouterLink } from '@angular/router';
import { PLAN_DISPLAY } from '../plan/plan.models';

@Component({
  selector: 'app-landing',
  imports: [MatToolbarModule, MatButtonModule, MatIconModule, RouterLink],
  templateUrl: './landing.html',
  styleUrl: './landing.scss',
})
export class LandingComponent {
  readonly title = 'QuoteFlow';
  readonly subtitle = 'Quotation and Invoice Management for Small Businesses';

  readonly benefits = [
    {
      icon: 'request_quote',
      tone: 'primary',
      title: 'Create quotations quickly',
      body: 'Build professional quotes with line items, tax, and discounts — calculated for you.',
    },
    {
      icon: 'sync_alt',
      tone: 'secondary',
      title: 'Convert to invoices',
      body: 'Turn an accepted quote into an invoice without retyping amounts or customer details.',
    },
    {
      icon: 'payments',
      tone: 'accent',
      title: 'Track partial and full payments',
      body: 'Record what you are owed and what has been paid, with a clear remaining balance.',
    },
    {
      icon: 'picture_as_pdf',
      tone: 'primary',
      title: 'Professional PDF documents',
      body: 'Download quotation and receipt PDFs that stay consistent with your saved totals.',
    },
    {
      icon: 'dashboard',
      tone: 'success',
      title: 'Business overview',
      body: 'See invoiced, collected, and outstanding amounts by period — currencies stay separate.',
    },
    {
      icon: 'lock',
      tone: 'secondary',
      title: 'Secure tenant workspace',
      body: 'Your business data is isolated per account. Sign in securely and work from any device.',
    },
  ] as const;

  readonly workflow = [
    { label: 'Customer', icon: 'person' },
    { label: 'Quote', icon: 'request_quote' },
    { label: 'Invoice', icon: 'receipt_long' },
    { label: 'Payment', icon: 'payments' },
    { label: 'Receipt', icon: 'receipt' },
  ] as const;

  readonly trust = [
    'Secure tenant isolation',
    'Professional PDFs',
    'Automatic calculations',
    'Works on phone and desktop',
    'Your business data stays separated',
  ] as const;

  readonly plans = [
    {
      id: 'FREE',
      name: PLAN_DISPLAY.FREE.name,
      price: PLAN_DISPLAY.FREE.monthly,
      period: null as string | null,
      alt: null as string | null,
      highlight: false,
      badge: null as string | null,
      cta: 'Create free account',
      features: [
        'Up to 5 active customers',
        '5 quotations / month',
        '5 invoices / month',
        'Basic PDF with QuoteFlow branding',
      ],
    },
    {
      id: 'PRO',
      name: PLAN_DISPLAY.PRO.name,
      price: '₹199',
      period: '/month' as string | null,
      alt: PLAN_DISPLAY.PRO.yearly,
      highlight: true,
      badge: 'Best for growing businesses',
      cta: 'Start free — upgrade later',
      features: [
        'Unlimited core customers, quotations, invoices',
        'Remove QuoteFlow branding',
        'Single-user workspace',
        'Room to grow your pipeline',
      ],
    },
    {
      id: 'BUSINESS',
      name: PLAN_DISPLAY.BUSINESS.name,
      price: '₹499',
      period: '/month' as string | null,
      alt: null as string | null,
      highlight: false,
      badge: null as string | null,
      cta: 'Start free — upgrade later',
      features: [
        'Everything in Pro',
        'Multi-user seats (coming later)',
        'Room for advanced ops later',
      ],
    },
  ] as const;
}
