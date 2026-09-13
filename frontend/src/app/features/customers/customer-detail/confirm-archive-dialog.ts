import { Component, inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';

export interface ConfirmArchiveData {
  displayName: string;
}

@Component({
  selector: 'app-confirm-archive-dialog',
  imports: [MatDialogModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>Archive customer?</h2>
    <mat-dialog-content>
      <p>
        Archive <strong>{{ data.displayName }}</strong>? They will no longer appear in the active
        customer list. You can still find them under Archived.
      </p>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" mat-dialog-close>Cancel</button>
      <button mat-flat-button color="warn" type="button" [mat-dialog-close]="true">Archive</button>
    </mat-dialog-actions>
  `,
})
export class ConfirmArchiveDialogComponent {
  readonly data = inject<ConfirmArchiveData>(MAT_DIALOG_DATA);
  readonly ref = inject(MatDialogRef<ConfirmArchiveDialogComponent>);
}
