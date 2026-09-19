import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AiUsagePageComponent } from './ai-usage-page';
import { AiUsageSummaryResponse } from './ai-usage.models';

describe('AiUsagePageComponent', () => {
  let fixture: ComponentFixture<AiUsagePageComponent>;
  let http: HttpTestingController;

  const response: AiUsageSummaryResponse = {
    features: [
      {
        feature: 'QUOTE_DRAFT',
        entitled: true,
        used: 20,
        limit: 20,
        remaining: 0,
        period: '2026-09',
        resetAt: '2026-10-01T00:00:00Z',
      },
      {
        feature: 'AGENT_WORKFLOW',
        entitled: false,
        used: 0,
        limit: 0,
        remaining: 0,
        period: '2026-09',
        resetAt: '2026-10-01T00:00:00Z',
      },
    ],
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AiUsagePageComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    fixture = TestBed.createComponent(AiUsagePageComponent);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('renders usage rows with limit and disabled states', () => {
    fixture.detectChanges();
    http.expectOne((r) => r.url.endsWith('/ai/usage')).flush(response);
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('2026-09');
    expect(text).toContain('Quote drafts');
    expect(text).toContain('20 of 20');
    expect(text).toContain('Limit reached');
    expect(text).toContain('Agent workflows');
    expect(text).toContain('Disabled on this plan');
    expect(text).not.toContain('cost');
  });

  it('surfaces load failures without leaking backend details', () => {
    fixture.detectChanges();
    http.expectOne((r) => r.url.endsWith('/ai/usage')).flush(
      { message: 'sql failed' },
      { status: 500, statusText: 'Server Error' },
    );
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Could not load AI usage');
    expect(text).not.toContain('sql failed');
  });
});
