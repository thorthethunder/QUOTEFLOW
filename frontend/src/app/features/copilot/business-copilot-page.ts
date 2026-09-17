import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ActionApprovalPanelComponent } from './action-approval-panel';
import {
  ActionProposalSummary,
  BusinessCopilotApiService,
  BusinessCopilotReference,
  BusinessCopilotResponse,
} from './business-copilot-api.service';

@Component({
  selector: 'app-business-copilot-page',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatIconModule,
    MatProgressSpinnerModule,
    ActionApprovalPanelComponent,
  ],
  templateUrl: './business-copilot-page.html',
  styleUrl: './business-copilot-page.scss',
})
export class BusinessCopilotPageComponent implements OnInit {
  private readonly api = inject(BusinessCopilotApiService);

  readonly available = signal(false);
  readonly actionsEnabled = signal(false);
  readonly loadingCaps = signal(true);
  readonly asking = signal(false);
  readonly error = signal<string | null>(null);
  readonly result = signal<BusinessCopilotResponse | null>(null);
  readonly activeProposal = signal<ActionProposalSummary | null>(null);

  readonly examples = [
    "Who hasn't paid me yet?",
    'How much have I collected this month?',
    'Show partially paid invoices.',
    'Find quotations for Raj Electrical.',
  ] as const;

  readonly message = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, Validators.maxLength(2000)],
  });

  ngOnInit(): void {
    this.api.capabilities().subscribe({
      next: (caps) => {
        this.available.set(!!caps.businessCopilot);
        this.actionsEnabled.set(!!caps.aiActions);
        this.loadingCaps.set(false);
      },
      error: () => {
        this.available.set(false);
        this.actionsEnabled.set(false);
        this.loadingCaps.set(false);
      },
    });
  }

  useExample(text: string): void {
    this.message.setValue(text);
    this.message.markAsDirty();
  }

  ask(): void {
    if (this.asking() || this.message.invalid || !this.available()) {
      return;
    }
    const text = this.message.value.trim();
    if (!text) {
      return;
    }
    this.asking.set(true);
    this.error.set(null);
    this.result.set(null);
    this.activeProposal.set(null);
    this.api.ask(text).subscribe({
      next: (res) => {
        this.result.set(res);
        this.activeProposal.set(res.actionProposal ?? null);
        this.asking.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.asking.set(false);
        this.error.set(this.mapError(err));
      },
    });
  }

  clearProposal(): void {
    this.activeProposal.set(null);
  }

  routeFor(ref: BusinessCopilotReference): string | null {
    if (!ref?.id || !ref.type) {
      return null;
    }
    switch (ref.type) {
      case 'CUSTOMER':
        return `/app/customers/${ref.id}`;
      case 'QUOTATION':
        return `/app/quotations/${ref.id}`;
      case 'INVOICE':
        return `/app/invoices/${ref.id}`;
      default:
        return null;
    }
  }

  refTitle(ref: BusinessCopilotReference): string {
    if (ref.displayNumber) {
      return `${ref.type} ${ref.displayNumber}`;
    }
    if (ref.label) {
      return `${ref.type} ${ref.label}`;
    }
    return ref.type;
  }

  private mapError(err: HttpErrorResponse): string {
    const code = err.error?.code as string | undefined;
    if (err.status === 429 || code === 'AI_RATE_LIMITED') {
      return 'Too many Copilot requests. Please wait a moment and try again.';
    }
    if (err.status === 503 || code === 'AI_DISABLED' || code === 'AI_UNAVAILABLE') {
      return 'Business Copilot is temporarily unavailable. Your QuoteFlow data and normal workflows are still available.';
    }
    if (err.status === 504 || code === 'AI_TIMEOUT') {
      return 'Business Copilot timed out. Your QuoteFlow data and normal workflows are still available.';
    }
    return 'Business Copilot could not complete that request. Please try again or use the normal QuoteFlow screens.';
  }
}
