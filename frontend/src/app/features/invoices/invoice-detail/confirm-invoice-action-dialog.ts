import { Component, inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';

@Component({
  selector: 'app-confirm-invoice-action-dialog',
  imports: [MatDialogModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>{{ data.title }}</h2>
    <mat-dialog-content><p>{{ data.body }}</p></mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" mat-dialog-close>Close</button>
      <button mat-flat-button color="primary" type="button" [mat-dialog-close]="true">{{ data.confirmLabel }}</button>
    </mat-dialog-actions>
  `,
})
export class ConfirmInvoiceActionDialog {
  readonly data = inject<{ title: string; body: string; confirmLabel: string }>(MAT_DIALOG_DATA);
}
