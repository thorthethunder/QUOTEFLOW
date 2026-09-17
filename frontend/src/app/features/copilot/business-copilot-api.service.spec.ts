import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { BusinessCopilotApiService } from './business-copilot-api.service';

describe('BusinessCopilotApiService', () => {
  let api: BusinessCopilotApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), BusinessCopilotApiService],
    });
    api = TestBed.inject(BusinessCopilotApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads capabilities including businessCopilot', () => {
    api.capabilities().subscribe((caps) => {
      expect(caps.businessCopilot).toBeTrue();
    });
    const req = http.expectOne((r) => r.url.endsWith('/ai/capabilities'));
    req.flush({
      enabled: true,
      quoteAssistant: true,
      businessCopilot: true,
      aiActions: false,
      provider: 'OLLAMA',
      model: 'qwen3:8b',
    });
  });

  it('posts ask message', () => {
    api.ask("Who hasn't paid me yet?").subscribe((res) => {
      expect(res.answer).toContain('unpaid');
      expect(res.references.length).toBe(1);
    });
    const req = http.expectOne((r) => r.url.endsWith('/ai/copilot/ask'));
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ message: "Who hasn't paid me yet?" });
    req.flush({
      answer: 'You have 1 unpaid invoice.',
      references: [{ type: 'INVOICE', id: '11111111-1111-1111-1111-111111111111', displayNumber: 'INV-0015', label: 'INV-0015' }],
      warnings: [],
      actionProposal: null,
    });
  });
});
