import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { SendQuotationEmailDialog } from '../quotations/quotation-detail/send-quotation-email-dialog';
import { SendReminderDialog } from '../invoices/invoice-detail/send-reminder-dialog';

describe('SendQuotationEmailDialog', () => {
  let fixture: ComponentFixture<SendQuotationEmailDialog>;
  const dialogRef = { close: jasmine.createSpy('close') };

  function setup(data: Record<string, unknown>) {
    TestBed.configureTestingModule({
      imports: [SendQuotationEmailDialog],
      providers: [
        { provide: MAT_DIALOG_DATA, useValue: data },
        { provide: MatDialogRef, useValue: dialogRef },
      ],
    });
    fixture = TestBed.createComponent(SendQuotationEmailDialog);
    fixture.detectChanges();
  }

  beforeEach(() => dialogRef.close.calls.reset());

  it('shows Pro gate when email sending disabled', () => {
    setup({
      quotationNumber: 'Q-000001',
      recipientEmail: 'a@example.com',
      emailSendingEnabled: false,
      mode: 'send',
    });
    expect(fixture.nativeElement.textContent).toContain('Email sending is available on Pro.');
  });

  it('shows missing recipient message', () => {
    setup({
      quotationNumber: 'Q-000001',
      recipientEmail: null,
      emailSendingEnabled: true,
      mode: 'send',
    });
    expect(fixture.nativeElement.textContent).toContain('Add an email address');
  });

  it('submits optional message for first send', () => {
    setup({
      quotationNumber: 'Q-000001',
      recipientEmail: 'buyer@example.com',
      emailSendingEnabled: true,
      mode: 'send',
    });
    const textarea = fixture.nativeElement.querySelector('textarea') as HTMLTextAreaElement;
    textarea.value = 'Hello';
    textarea.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    fixture.componentInstance.submit();
    expect(dialogRef.close).toHaveBeenCalledWith({ message: 'Hello', resend: false });
  });

  it('marks resend when mode is resend', () => {
    setup({
      quotationNumber: 'Q-000001',
      recipientEmail: 'buyer@example.com',
      emailSendingEnabled: true,
      mode: 'resend',
    });
    expect(fixture.nativeElement.textContent).toContain('Resend quotation email');
    fixture.componentInstance.submit();
    expect(dialogRef.close).toHaveBeenCalledWith({ message: '', resend: true });
  });

  it('rejects messages over 500 characters via form validity', () => {
    setup({
      quotationNumber: 'Q-000001',
      recipientEmail: 'buyer@example.com',
      emailSendingEnabled: true,
      mode: 'send',
    });
    fixture.componentInstance.form.controls.message.setValue('x'.repeat(501));
    fixture.detectChanges();
    expect(fixture.componentInstance.form.invalid).toBeTrue();
  });

  it('shows retry title for retry mode', () => {
    setup({
      quotationNumber: 'Q-000001',
      recipientEmail: 'buyer@example.com',
      emailSendingEnabled: true,
      mode: 'retry',
    });
    expect(fixture.nativeElement.textContent).toContain('Retry quotation email');
  });
});

describe('SendReminderDialog', () => {
  let fixture: ComponentFixture<SendReminderDialog>;
  const dialogRef = { close: jasmine.createSpy('close') };

  function setup(data: Record<string, unknown>) {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [SendReminderDialog],
      providers: [
        { provide: MAT_DIALOG_DATA, useValue: data },
        { provide: MatDialogRef, useValue: dialogRef },
      ],
    });
    fixture = TestBed.createComponent(SendReminderDialog);
    fixture.detectChanges();
  }

  beforeEach(() => dialogRef.close.calls.reset());

  it('submits selected tone', () => {
    setup({
      invoiceNumber: 'INV-000001',
      recipientEmail: 'buyer@example.com',
      balanceDue: 4000,
      currency: 'INR',
      emailSendingEnabled: true,
      mode: 'send',
    });
    fixture.componentInstance.form.controls.tone.setValue('FIRM');
    fixture.componentInstance.submit();
    expect(dialogRef.close).toHaveBeenCalledWith({
      tone: 'FIRM',
      message: '',
      resend: false,
    });
  });

  it('shows outstanding balance in dialog copy', () => {
    setup({
      invoiceNumber: 'INV-000001',
      recipientEmail: 'buyer@example.com',
      balanceDue: 4000,
      currency: 'INR',
      emailSendingEnabled: true,
      mode: 'send',
    });
    expect(fixture.nativeElement.textContent).toContain('INV-000001');
    expect(fixture.nativeElement.textContent).toContain('outstanding');
  });

  it('gates FREE and missing recipient', () => {
    setup({
      invoiceNumber: 'INV-000001',
      recipientEmail: 'buyer@example.com',
      balanceDue: 4000,
      currency: 'INR',
      emailSendingEnabled: false,
      mode: 'send',
    });
    expect(fixture.nativeElement.textContent).toContain('Email sending is available on Pro.');

    setup({
      invoiceNumber: 'INV-000001',
      recipientEmail: null,
      balanceDue: 4000,
      currency: 'INR',
      emailSendingEnabled: true,
      mode: 'send',
    });
    expect(fixture.nativeElement.textContent).toContain('Add an email address');
  });

  it('keeps dialog action touch targets at least ~44px', () => {
    setup({
      invoiceNumber: 'INV-000001',
      recipientEmail: 'buyer@example.com',
      balanceDue: 4000,
      currency: 'INR',
      emailSendingEnabled: true,
      mode: 'send',
    });
    const buttons = fixture.nativeElement.querySelectorAll('mat-dialog-actions button') as NodeListOf<HTMLElement>;
    expect(buttons.length).toBeGreaterThan(0);
    buttons.forEach((btn) => {
      expect(getComputedStyle(btn).minHeight).toBe('44px');
    });
  });
});
