import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';
import { AiWorkflowsPageComponent } from './ai-workflows-page';
import { AiWorkflow } from './ai-workflows.models';

describe('AiWorkflowsPageComponent', () => {
  let fixture: ComponentFixture<AiWorkflowsPageComponent>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AiWorkflowsPageComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    fixture = TestBed.createComponent(AiWorkflowsPageComponent);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('starts payment follow-up and renders pending approvals', () => {
    fixture.detectChanges();
    http.expectOne((r) => r.url.endsWith('/ai/workflows')).flush([]);
    fixture.detectChanges();

    fixture.debugElement.query(By.css('form')).triggerEventHandler('ngSubmit', {});
    const start = http.expectOne((r) => r.url.endsWith('/ai/workflows') && r.method === 'POST');
    expect(start.request.body.workflowType).toBe('PAYMENT_FOLLOW_UP');
    start.flush(workflowFixture('WAITING_FOR_APPROVAL'));
    http.expectOne((r) => r.url.includes('/ai/actions/11111111-1111-1111-1111-111111111111')).flush(
      proposalFixture(),
    );
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('WAITING_FOR_APPROVAL');
    expect(fixture.nativeElement.textContent).toContain('Send payment reminder');
    expect(fixture.nativeElement.textContent).toContain('buyer@example.com');
    expect(fixture.nativeElement.textContent).toContain('Approve');
  });

  it('approves one proposal through the existing action endpoint then resumes workflow', () => {
    fixture.detectChanges();
    http.expectOne((r) => r.url.endsWith('/ai/workflows')).flush([listItemFixture()]);
    http.expectOne((r) => r.url.endsWith('/ai/workflows/wf-1')).flush(workflowFixture('WAITING_FOR_APPROVAL'));
    http.expectOne((r) => r.url.includes('/ai/actions/11111111-1111-1111-1111-111111111111')).flush(
      proposalFixture(),
    );
    fixture.detectChanges();

    fixture.debugElement.query(By.css('.approval-actions button[color="primary"]')).nativeElement.click();
    http.expectOne((r) => r.url.endsWith('/ai/actions/11111111-1111-1111-1111-111111111111/confirm')).flush({
      proposalId: '11111111-1111-1111-1111-111111111111',
      status: 'EXECUTED',
      resultReferenceType: 'NOTIFICATION',
      resultReferenceId: '22222222-2222-2222-2222-222222222222',
      message: 'Payment reminder queued for delivery.',
    });
    http.expectOne((r) => r.url.endsWith('/ai/workflows/wf-1/resume')).flush(workflowFixture('COMPLETED', 'EXECUTED'));
    http.expectOne((r) => r.url.includes('/ai/actions/11111111-1111-1111-1111-111111111111')).flush({
      ...proposalFixture(),
      status: 'EXECUTED',
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('COMPLETED');
  });

  it('surfaces disabled workflow state', () => {
    fixture.detectChanges();
    http.expectOne((r) => r.url.endsWith('/ai/workflows')).flush(
      { code: 'AI_WORKFLOWS_DISABLED', message: 'disabled' },
      { status: 503, statusText: 'Service Unavailable' },
    );
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('AI workflows are disabled');
  });
});

function listItemFixture() {
  return {
    id: 'wf-1',
    workflowType: 'PAYMENT_FOLLOW_UP',
    status: 'WAITING_FOR_APPROVAL',
    goal: 'Prepare reminders.',
    updatedAt: new Date().toISOString(),
    expiresAt: new Date(Date.now() + 600_000).toISOString(),
  };
}

function workflowFixture(status: AiWorkflow['status'], proposalStatus = 'PENDING'): AiWorkflow {
  return {
    id: 'wf-1',
    workflowType: 'PAYMENT_FOLLOW_UP',
    status,
    goal: 'Prepare reminders.',
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    startedAt: new Date().toISOString(),
    completedAt: status === 'COMPLETED' ? new Date().toISOString() : null,
    expiresAt: new Date(Date.now() + 600_000).toISOString(),
    summary: {
      totalSteps: 3,
      pendingApprovals: proposalStatus === 'PENDING' ? 1 : 0,
      executedActions: proposalStatus === 'EXECUTED' ? 1 : 0,
      failedActions: 0,
      cancelledActions: 0,
      expiredActions: 0,
      partialResults: false,
    },
    steps: [
      {
        id: 'step-1',
        stepNumber: 1,
        stepType: 'READ_OUTSTANDING_INVOICES',
        classification: 'READ_ONLY',
        status: 'COMPLETED',
        actionProposalId: null,
        input: {},
        output: { selectedCount: 1 },
        startedAt: new Date().toISOString(),
        completedAt: new Date().toISOString(),
        failureCode: null,
      },
      {
        id: 'step-2',
        stepNumber: 2,
        stepType: 'PREPARE_PAYMENT_REMINDER',
        classification: 'ACTION_REQUIRES_APPROVAL',
        status: proposalStatus === 'PENDING' ? 'WAITING_FOR_APPROVAL' : 'COMPLETED',
        actionProposalId: '11111111-1111-1111-1111-111111111111',
        input: { invoiceId: 'inv-1' },
        output: {},
        startedAt: new Date().toISOString(),
        completedAt: null,
        failureCode: null,
      },
    ],
  };
}

function proposalFixture() {
  return {
    proposalId: '11111111-1111-1111-1111-111111111111',
    actionType: 'PAYMENT_REMINDER_SEND',
    status: 'PENDING',
    summary: 'Send payment reminder for INV-0001',
    expiresAt: new Date(Date.now() + 600_000).toISOString(),
    confirmButtonLabel: 'Approve & Send Reminder',
    createdAt: new Date().toISOString(),
    payload: {
      invoiceId: 'inv-1',
      invoiceNumber: 'INV-0001',
      customerDisplayName: 'Buyer',
      recipientEmail: 'buyer@example.com',
      currency: 'INR',
      outstandingAmount: '1000.00',
      bodyPlainText: 'Please pay.',
    },
    preview: { sendsEmail: true },
    resultReferenceType: null,
    resultReferenceId: null,
  };
}
