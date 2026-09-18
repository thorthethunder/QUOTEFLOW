import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { By } from '@angular/platform-browser';
import { ActionApprovalPanelComponent } from './action-approval-panel';
import { ActionProposalSummary } from './business-copilot-api.service';

describe('ActionApprovalPanelComponent', () => {
  let fixture: ComponentFixture<ActionApprovalPanelComponent>;
  let http: HttpTestingController;

  const summary: ActionProposalSummary = {
    proposalId: '11111111-1111-1111-1111-111111111111',
    actionType: 'QUOTATION_CREATE_DRAFT',
    status: 'PENDING',
    summary: 'Create draft quotation for Raj Electrical',
    expiresAt: new Date(Date.now() + 600_000).toISOString(),
    confirmButtonLabel: 'Create Draft Quotation',
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ActionApprovalPanelComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    fixture = TestBed.createComponent(ActionApprovalPanelComponent);
    fixture.componentRef.setInput('summary', summary);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads proposal details and confirms without auto-confirm', () => {
    fixture.detectChanges();
    const req = http.expectOne((r) => r.url.includes('/ai/actions/11111111-1111-1111-1111-111111111111'));
    expect(req.request.method).toBe('GET');
    req.flush({
      ...summary,
      createdAt: new Date().toISOString(),
      payload: {
        customerDisplayName: 'Raj Electrical',
        currency: 'INR',
        items: [{ description: 'Fans', quantity: 2, unitPrice: 3000 }],
      },
      preview: { currency: 'INR', subtotal: 6000, discountAmount: 0, taxAmount: 0, total: 6000 },
      resultReferenceType: null,
      resultReferenceId: null,
    });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Raj Electrical');
    expect(fixture.nativeElement.textContent).toContain('Create Draft Quotation');
    expect(http.match((r) => r.url.includes('/confirm')).length).toBe(0);

    const confirmBtn = fixture.debugElement.query(By.css('.confirm'));
    confirmBtn.nativeElement.click();
    fixture.detectChanges();
    const confirm = http.expectOne((r) => r.url.endsWith('/confirm'));
    expect(confirm.request.method).toBe('POST');
    expect(confirm.request.body).toEqual({});
    confirm.flush({
      proposalId: summary.proposalId,
      status: 'EXECUTED',
      resultReferenceType: 'QUOTATION',
      resultReferenceId: 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee',
      message: 'Draft quotation Q-0001 created.',
    });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Draft quotation Q-0001 created.');
  });

  it('renders reminder body as text only', () => {
    fixture.detectChanges();
    http.expectOne((r) => r.url.includes('/ai/actions/')).flush({
      ...summary,
      actionType: 'REMINDER_PREPARE',
      confirmButtonLabel: 'Accept Prepared Reminder',
      createdAt: new Date().toISOString(),
      payload: {
        invoiceNumber: 'INV-0015',
        subject: 'Reminder',
        bodyPlainText: '<img src=x onerror=alert(1)> Please pay',
        currency: 'INR',
        balanceDue: 1000,
      },
      preview: { sendsEmail: false },
      resultReferenceType: null,
      resultReferenceId: null,
    });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('pre').textContent).toContain('<img');
    expect(fixture.nativeElement.querySelector('pre img')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Email will not be sent');
  });

  it('renders payment reminder send details and approve label', () => {
    fixture.componentRef.setInput('summary', {
      ...summary,
      actionType: 'PAYMENT_REMINDER_SEND',
      confirmButtonLabel: 'Approve & Send Reminder',
      summary: 'Send payment reminder for INV-0015',
    });
    fixture.detectChanges();
    http.expectOne((r) => r.url.includes('/ai/actions/')).flush({
      ...summary,
      actionType: 'PAYMENT_REMINDER_SEND',
      confirmButtonLabel: 'Approve & Send Reminder',
      createdAt: new Date().toISOString(),
      payload: {
        invoiceNumber: 'INV-0015',
        customerDisplayName: 'Raj Electrical',
        recipientEmail: 'buyer@example.com',
        currency: 'INR',
        outstandingAmount: '1000.00',
        paymentState: 'UNPAID',
        subject: 'Payment reminder',
        bodyPlainText: 'Please pay when convenient.',
      },
      preview: { sendsEmail: true },
      resultReferenceType: null,
      resultReferenceId: null,
    });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('buyer@example.com');
    expect(fixture.nativeElement.textContent).toContain('Approve & Send Reminder');
    expect(fixture.nativeElement.textContent).toContain('queued');
  });
});
