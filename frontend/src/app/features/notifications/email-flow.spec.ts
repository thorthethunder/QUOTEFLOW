import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MatDialog } from '@angular/material/dialog';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { of, Subject } from 'rxjs';
import { QuotationDetailComponent } from '../quotations/quotation-detail/quotation-detail';
import { InvoiceDetailComponent } from '../invoices/invoice-detail/invoice-detail';
import { EntitlementStore } from '../plan/entitlement.store';
import { Quotation } from '../quotations/quotation.models';
import { Invoice } from '../invoices/invoice.models';
import { NotificationItem } from './notification.models';
import { PaymentSummary } from '../payments/payment.models';

const nullAddress = {
  customerCompanyName: null as string | null,
  customerPhone: null as string | null,
  customerAddressLine1: null as string | null,
  customerAddressLine2: null as string | null,
  customerCity: null as string | null,
  customerStateRegion: null as string | null,
  customerPostalCode: null as string | null,
  customerCountryCode: null as string | null,
  customerTaxId: null as string | null,
  businessPhone: null as string | null,
  businessAddressLine1: null as string | null,
  businessAddressLine2: null as string | null,
  businessCity: null as string | null,
  businessStateRegion: null as string | null,
  businessPostalCode: null as string | null,
  businessCountryCode: null as string | null,
  businessTaxId: null as string | null,
};

const quotationFixture: Quotation = {
  id: 'q-1',
  version: 2,
  quotationNumber: 'Q-000001',
  status: 'SENT',
  currency: 'INR',
  issueDate: '2026-09-01',
  validUntil: '2026-09-30',
  customerId: 'c-1',
  customerDisplayName: 'Acme Very Long Customer Name That Should Wrap',
  customerEmail: 'buyer@example.com',
  businessName: 'Notify Co',
  businessEmail: 'biz@example.com',
  notes: null,
  terms: null,
  discountType: 'NONE',
  discountValue: 0,
  taxRate: 0,
  subtotal: 100,
  discountAmount: 0,
  taxAmount: 0,
  totalAmount: 100,
  convertedInvoiceId: null,
  convertedInvoiceNumber: null,
  items: [],
  createdAt: '2026-09-01T00:00:00Z',
  updatedAt: '2026-09-01T00:00:00Z',
  ...nullAddress,
};

const unpaidSummary: PaymentSummary = {
  amountPaid: 0,
  balanceDue: 4000,
  paymentState: 'UNPAID',
};

const paidSummary: PaymentSummary = {
  amountPaid: 4000,
  balanceDue: 0,
  paymentState: 'PAID',
};

const invoiceFixture: Invoice = {
  id: 'inv-1',
  version: 1,
  invoiceNumber: 'INV-000001',
  status: 'SENT',
  currency: 'INR',
  issueDate: '2026-09-01',
  dueDate: '2026-09-30',
  customerId: 'c-1',
  customerDisplayName: 'Acme',
  customerEmail: 'buyer@example.com',
  businessName: 'Notify Co',
  businessEmail: 'biz@example.com',
  notes: null,
  terms: null,
  discountType: 'NONE',
  discountValue: 0,
  taxRate: 0,
  subtotal: 4000,
  discountAmount: 0,
  taxAmount: 0,
  totalAmount: 4000,
  sourceQuotationId: null,
  sourceQuotationNumber: null,
  items: [],
  paymentSummary: unpaidSummary,
  createdAt: '2026-09-01T00:00:00Z',
  updatedAt: '2026-09-01T00:00:00Z',
  ...nullAddress,
};

const sentNotification: NotificationItem = {
  id: 'n-1',
  type: 'QUOTATION_EMAIL',
  channel: 'EMAIL',
  status: 'SENT',
  recipientMasked: 'b***@example.com',
  subject: 'Quotation Q-000001',
  referenceType: 'QUOTATION',
  referenceId: 'q-1',
  attemptCount: 1,
  lastErrorCode: null,
  sentAt: '2026-09-13T10:00:00Z',
  createdAt: '2026-09-13T10:00:00Z',
};

describe('QuotationDetailComponent email flow', () => {
  let http: HttpTestingController;
  let dialogOpen: jasmine.Spy;
  let entitlements: {
    canSendEmail: jasmine.Spy;
    canCreateInvoice: jasmine.Spy;
    refresh: jasmine.Spy;
    messageFromApiError: jasmine.Spy;
  };

  beforeEach(async () => {
    dialogOpen = jasmine.createSpy('open').and.returnValue({ afterClosed: () => of(null) });
    entitlements = {
      canSendEmail: jasmine.createSpy('canSendEmail').and.returnValue(true),
      canCreateInvoice: jasmine.createSpy('canCreateInvoice').and.returnValue(true),
      refresh: jasmine.createSpy('refresh').and.returnValue(of(null)),
      messageFromApiError: jasmine.createSpy('messageFromApiError').and.returnValue(null),
    };
    await TestBed.configureTestingModule({
      imports: [QuotationDetailComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 'q-1' } } } },
        { provide: EntitlementStore, useValue: entitlements },
      ],
    })
      .overrideProvider(MatDialog, { useValue: { open: dialogOpen } })
      .compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function loadDetail(
    fixture: ComponentFixture<QuotationDetailComponent>,
    notifications: NotificationItem[] = [],
  ) {
    fixture.detectChanges();
    http.expectOne((r) => /\/quotations\/q-1$/.test(r.url) && r.method === 'GET').flush(quotationFixture);
    fixture.detectChanges();
    http.expectOne((r) => r.url.includes('/notifications')).flush(notifications);
    fixture.detectChanges();
  }

  it('opens send dialog for PRO and posts email with double-submit guard', fakeAsync(() => {
    const afterClosed = new Subject<unknown>();
    dialogOpen.and.returnValue({ afterClosed: () => afterClosed.asObservable() });
    const fixture = TestBed.createComponent(QuotationDetailComponent);
    loadDetail(fixture);

    fixture.componentInstance.openSendEmail();
    expect(dialogOpen).toHaveBeenCalled();
    expect(dialogOpen.calls.mostRecent().args[1].data.mode).toBe('send');
    expect(dialogOpen.calls.mostRecent().args[1].data.emailSendingEnabled).toBeTrue();

    afterClosed.next({ message: 'Hi', resend: false });
    tick();
    expect(fixture.componentInstance.emailBusy()).toBeTrue();

    afterClosed.next({ message: 'Hi again', resend: false });
    tick();
    const posts = http.match((r) => r.url.includes('/send-email') && r.method === 'POST');
    expect(posts.length).toBe(1);
    posts[0].flush(sentNotification);
    tick();
    // load() + explicit loadNotifications() may each request history
    const reloads = http.match(() => true);
    expect(reloads.some((r) => /\/quotations\/q-1$/.test(r.request.url))).toBeTrue();
    for (const req of reloads) {
      if (/\/quotations\/q-1$/.test(req.request.url)) {
        req.flush(quotationFixture);
      } else if (req.request.url.includes('/notifications')) {
        req.flush([sentNotification]);
      } else {
        req.flush({});
      }
    }
    tick();
    // nested loadNotifications after quotation GET
    for (const req of http.match((r) => r.url.includes('/notifications'))) {
      req.flush([sentNotification]);
    }
    expect(fixture.componentInstance.emailBusy()).toBeFalse();
    expect(fixture.componentInstance.emailMessage()).toContain('sent');
  }));

  it('uses resend mode when latest notification is SENT', () => {
    const fixture = TestBed.createComponent(QuotationDetailComponent);
    loadDetail(fixture, [sentNotification]);
    fixture.componentInstance.openSendEmail();
    expect(dialogOpen.calls.mostRecent().args[1].data.mode).toBe('resend');
  });

  it('uses retry mode when latest notification is FAILED', () => {
    const fixture = TestBed.createComponent(QuotationDetailComponent);
    loadDetail(fixture, [{ ...sentNotification, status: 'FAILED' }]);
    fixture.componentInstance.openSendEmail();
    expect(dialogOpen.calls.mostRecent().args[1].data.mode).toBe('retry');
  });

  it('passes FREE gate into dialog data', () => {
    entitlements.canSendEmail.and.returnValue(false);
    const fixture = TestBed.createComponent(QuotationDetailComponent);
    loadDetail(fixture);
    fixture.componentInstance.openSendEmail();
    expect(dialogOpen.calls.mostRecent().args[1].data.emailSendingEnabled).toBeFalse();
  });

  it('renders email history and surfaces API failure', fakeAsync(() => {
    const afterClosed = new Subject<unknown>();
    dialogOpen.and.returnValue({ afterClosed: () => afterClosed.asObservable() });
    entitlements.messageFromApiError.and.returnValue('Upgrade to Pro to send email.');
    const fixture = TestBed.createComponent(QuotationDetailComponent);
    loadDetail(fixture, [sentNotification]);
    expect(fixture.nativeElement.textContent).toContain('Email history');

    fixture.componentInstance.openSendEmail();
    afterClosed.next({ message: '', resend: true });
    tick();
    http
      .expectOne((r) => r.url.includes('/send-email'))
      .flush({ code: 'FEATURE_NOT_AVAILABLE' }, { status: 403, statusText: 'Forbidden' });
    tick();
    expect(fixture.componentInstance.emailBusy()).toBeFalse();
    expect(fixture.componentInstance.actionError()).toContain('Upgrade');
    expect(fixture.componentInstance.showViewPlans()).toBeTrue();
  }));
});

describe('InvoiceDetailComponent reminder flow', () => {
  let http: HttpTestingController;
  let dialogOpen: jasmine.Spy;

  beforeEach(async () => {
    dialogOpen = jasmine.createSpy('open').and.returnValue({ afterClosed: () => of(null) });
    await TestBed.configureTestingModule({
      imports: [InvoiceDetailComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 'inv-1' } } } },
        {
          provide: EntitlementStore,
          useValue: {
            canSendEmail: () => true,
            refresh: () => of(null),
            messageFromApiError: () => null,
          },
        },
      ],
    })
      .overrideProvider(MatDialog, { useValue: { open: dialogOpen } })
      .compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function loadInvoice(summary: PaymentSummary) {
    const fixture = TestBed.createComponent(InvoiceDetailComponent);
    fixture.detectChanges();
    const invoiceReq = http.expectOne((r) => /\/invoices\/inv-1$/.test(r.url) && r.method === 'GET');
    const paymentsReq = http.expectOne((r) => /\/invoices\/inv-1\/payments$/.test(r.url) && r.method === 'GET');
    invoiceReq.flush({ ...invoiceFixture, paymentSummary: summary });
    paymentsReq.flush({ summary, payments: [] });
    fixture.detectChanges();
    http.expectOne((r) => r.url.includes('/notifications')).flush([]);
    fixture.detectChanges();
    return fixture;
  }

  it('allows reminder on outstanding invoice and posts with loading', fakeAsync(() => {
    const afterClosed = new Subject<unknown>();
    dialogOpen.and.returnValue({ afterClosed: () => afterClosed.asObservable() });
    const fixture = loadInvoice(unpaidSummary);
    expect(fixture.componentInstance.canSendReminder()).toBeTrue();

    fixture.componentInstance.openSendReminder();
    expect(dialogOpen.calls.mostRecent().args[1].data.mode).toBe('send');
    afterClosed.next({ tone: 'STANDARD', message: '', resend: false });
    tick();
    expect(fixture.componentInstance.reminderBusy()).toBeTrue();
    http.expectOne((r) => r.url.includes('/send-reminder')).flush({
      ...sentNotification,
      type: 'INVOICE_REMINDER',
      referenceType: 'INVOICE',
      referenceId: 'inv-1',
    });
    tick();
    http.expectOne((r) => r.url.includes('/notifications')).flush([]);
    expect(fixture.componentInstance.reminderBusy()).toBeFalse();
    expect(fixture.componentInstance.reminderMessage()).toContain('Reminder sent');
  }));

  it('does not open reminder for paid invoice', () => {
    const fixture = loadInvoice(paidSummary);
    expect(fixture.componentInstance.canSendReminder()).toBeFalse();
    fixture.componentInstance.openSendReminder();
    expect(dialogOpen).not.toHaveBeenCalled();
  });
});
