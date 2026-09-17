import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { By } from '@angular/platform-browser';
import { BusinessCopilotPageComponent } from './business-copilot-page';

describe('BusinessCopilotPageComponent', () => {
  let fixture: ComponentFixture<BusinessCopilotPageComponent>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [BusinessCopilotPageComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    fixture = TestBed.createComponent(BusinessCopilotPageComponent);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function flushCaps(enabled: boolean): void {
    fixture.detectChanges();
    const req = http.expectOne((r) => r.url.endsWith('/ai/capabilities'));
    req.flush({
      enabled,
      quoteAssistant: enabled,
      businessCopilot: enabled,
      provider: enabled ? 'OLLAMA' : 'DISABLED',
      model: enabled ? 'qwen3:8b' : '',
    });
    fixture.detectChanges();
  }

  it('hides ask UI when Copilot unavailable', () => {
    flushCaps(false);
    expect(fixture.nativeElement.textContent).toContain('not available');
    expect(fixture.nativeElement.querySelector('.ask-form')).toBeNull();
  });

  it('renders examples and submits a question', () => {
    flushCaps(true);
    expect(fixture.nativeElement.textContent).toContain("Who hasn't paid me yet?");
    const exampleBtn = fixture.debugElement.query(By.css('.example'));
    exampleBtn.nativeElement.click();
    fixture.detectChanges();

    const form = fixture.debugElement.query(By.css('.ask-form'));
    form.triggerEventHandler('ngSubmit', {});
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Checking your QuoteFlow data');

    const ask = http.expectOne((r) => r.url.endsWith('/ai/copilot/ask'));
    ask.flush({
      answer: 'Raj Electrical owes INR 1000.',
      references: [
        {
          type: 'INVOICE',
          id: 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee',
          displayNumber: 'INV-0015',
          label: 'INV-0015',
        },
      ],
      warnings: [],
    });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Raj Electrical owes INR 1000.');
    expect(fixture.nativeElement.textContent).toContain('INV-0015');
    const view = fixture.debugElement.query(By.css('.refs a'));
    expect(view.attributes['href'] || view.nativeElement.getAttribute('href')).toContain(
      '/app/invoices/aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee',
    );
  });

  it('renders answer as text only (no HTML execution)', () => {
    flushCaps(true);
    const component = fixture.componentInstance;
    component.message.setValue('test');
    component.ask();
    fixture.detectChanges();
    http.expectOne((r) => r.url.endsWith('/ai/copilot/ask')).flush({
      answer: '<img src=x onerror=alert(1)> unpaid',
      references: [],
      warnings: [],
    });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.answer-text').textContent).toContain('<img');
    expect(fixture.nativeElement.querySelector('.answer-text img')).toBeNull();
  });

  it('surfaces rate limit errors', () => {
    flushCaps(true);
    fixture.componentInstance.message.setValue('hello');
    fixture.componentInstance.ask();
    fixture.detectChanges();
    http.expectOne((r) => r.url.endsWith('/ai/copilot/ask')).flush(
      { code: 'AI_RATE_LIMITED', message: 'slow down' },
      { status: 429, statusText: 'Too Many Requests' },
    );
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Too many Copilot requests');
  });
});
