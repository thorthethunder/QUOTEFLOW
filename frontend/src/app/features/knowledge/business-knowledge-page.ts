import { CommonModule } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { finalize } from 'rxjs';
import { BusinessKnowledgeApiService } from './business-knowledge-api.service';
import { KnowledgeAskResponse, KnowledgeDocument } from './business-knowledge.models';

@Component({
  selector: 'app-business-knowledge-page',
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
  ],
  templateUrl: './business-knowledge-page.html',
  styleUrl: './business-knowledge-page.scss',
})
export class BusinessKnowledgePageComponent {
  private readonly api = inject(BusinessKnowledgeApiService);

  readonly title = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, Validators.maxLength(140)],
  });
  readonly text = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required],
  });
  readonly question = new FormControl('What is our quotation validity policy?', {
    nonNullable: true,
    validators: [Validators.required, Validators.maxLength(1000)],
  });
  readonly uploadTitle = new FormControl('', { nonNullable: true, validators: [Validators.maxLength(140)] });

  readonly documents = signal<KnowledgeDocument[]>([]);
  readonly selectedFile = signal<File | null>(null);
  readonly answer = signal<KnowledgeAskResponse | null>(null);
  readonly error = signal('');
  readonly loading = signal(false);
  readonly asking = signal(false);
  readonly saving = signal(false);

  readonly readyCount = computed(() => this.documents().filter((d) => d.status === 'READY').length);

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set('');
    this.api
      .list()
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (docs) => this.documents.set(docs),
        error: (err) => this.error.set(this.message(err)),
      });
  }

  addText(): void {
    if (this.title.invalid || this.text.invalid) {
      this.title.markAsTouched();
      this.text.markAsTouched();
      return;
    }
    this.saving.set(true);
    this.error.set('');
    this.api
      .createText(this.title.value, this.text.value)
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: (doc) => {
          this.documents.update((docs) => [doc, ...docs]);
          this.title.reset('');
          this.text.reset('');
        },
        error: (err) => this.error.set(this.message(err)),
      });
  }

  chooseFile(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.selectedFile.set(input.files?.[0] ?? null);
  }

  upload(): void {
    const file = this.selectedFile();
    if (!file || this.uploadTitle.invalid) {
      this.uploadTitle.markAsTouched();
      return;
    }
    this.saving.set(true);
    this.error.set('');
    this.api
      .uploadText(this.uploadTitle.value, file)
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: (doc) => {
          this.documents.update((docs) => [doc, ...docs]);
          this.selectedFile.set(null);
          this.uploadTitle.reset('');
        },
        error: (err) => this.error.set(this.message(err)),
      });
  }

  delete(doc: KnowledgeDocument): void {
    this.error.set('');
    this.api.delete(doc.id).subscribe({
      next: () => this.documents.update((docs) => docs.filter((d) => d.id !== doc.id)),
      error: (err) => this.error.set(this.message(err)),
    });
  }

  ask(): void {
    if (this.question.invalid) {
      this.question.markAsTouched();
      return;
    }
    this.asking.set(true);
    this.error.set('');
    this.api
      .ask(this.question.value)
      .pipe(finalize(() => this.asking.set(false)))
      .subscribe({
        next: (response) => this.answer.set(response),
        error: (err) => this.error.set(this.message(err)),
      });
  }

  private message(err: unknown): string {
    const body = err as { error?: { message?: string; code?: string } };
    return body.error?.message ?? body.error?.code ?? 'Business Knowledge is unavailable.';
  }
}
