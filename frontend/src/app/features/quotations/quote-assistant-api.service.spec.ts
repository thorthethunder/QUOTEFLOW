import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { QuoteAssistantApiService } from './quote-assistant-api.service';

describe('QuoteAssistantApiService', () => {
  let api: QuoteAssistantApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), QuoteAssistantApiService],
    });
    api = TestBed.inject(QuoteAssistantApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads capabilities', () => {
    api.capabilities().subscribe((caps) => {
      expect(caps.quoteAssistant).toBeTrue();
    });
    const req = http.expectOne((r) => r.url.endsWith('/ai/capabilities'));
    expect(req.request.method).toBe('GET');
    req.flush({ enabled: true, quoteAssistant: true, businessCopilot: true, provider: 'OLLAMA', model: 'qwen3:8b' });
  });

  it('posts draft prompt without persisting quotation path', () => {
    api.draft('Raj Electrical ceiling fans').subscribe((p) => {
      expect(p.items.length).toBe(1);
    });
    const req = http.expectOne((r) => r.url.endsWith('/ai/quote-assistant/draft'));
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ prompt: 'Raj Electrical ceiling fans' });
    req.flush({
      customer: { matched: false, ambiguous: false, id: null, name: null, candidates: [] },
      proposedCustomerName: 'Raj Electrical',
      items: [{ description: 'Fans', quantity: 2, unitPrice: 3000, needsReview: false }],
      notes: null,
      discountType: 'NONE',
      discountValue: 0,
      taxRate: 0,
      warnings: [],
      calculation: null,
      calculationAvailable: false,
    });
  });

  it('surfaces unavailable error', () => {
    api.draft('x').subscribe({
      next: () => fail('expected error'),
      error: (err: HttpErrorResponse) => expect(err.status).toBe(503),
    });
    http.expectOne((r) => r.url.endsWith('/ai/quote-assistant/draft')).flush(
      { code: 'AI_UNAVAILABLE', message: 'down' },
      { status: 503, statusText: 'Unavailable' },
    );
  });
});
