export type NotificationStatus = 'PENDING' | 'SENDING' | 'SENT' | 'FAILED' | 'CANCELLED';
export type NotificationType = 'QUOTATION_EMAIL' | 'INVOICE_REMINDER' | 'RECEIPT_EMAIL' | 'PLATFORM_NOTICE';
export type ReminderTone = 'FRIENDLY' | 'STANDARD' | 'FIRM';

export interface NotificationItem {
  id: string;
  type: NotificationType;
  channel: string;
  status: NotificationStatus;
  recipientMasked: string;
  subject: string;
  referenceType: string;
  referenceId: string;
  attemptCount: number;
  lastErrorCode: string | null;
  createdAt: string;
  sentAt: string | null;
}

export interface SendQuotationEmailPayload {
  message?: string;
  version?: number;
  resend?: boolean;
}

export interface SendInvoiceReminderPayload {
  tone?: ReminderTone;
  message?: string;
  resend?: boolean;
}
