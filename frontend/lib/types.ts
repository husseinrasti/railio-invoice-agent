// Wire types mirroring the backend API/SSE payloads.

export interface InvoiceView {
  id: string;
  detail: string;
  amount: number;
  currency: string;
  expiresAt?: string | null;
  depositAccountName: string;
  depositId: string;
}

/** Lifecycle states Railio reports. A created payment is not a paid one. */
export type PaymentStatus =
  | "CREATED"
  | "POLICY_CHECKING"
  | "EXECUTING"
  | "AWAITING_APPROVAL"
  | "AWAITING_ACTION"
  | "AWAITING_OTP"
  | "COMPLETED"
  | "FAILED"
  | "CANCELLED"
  | "EXPIRED";

export interface ReceiptView {
  paymentId: string;
  kind: "PREVIEW" | "FINAL";
  status: PaymentStatus;
  amount: number;
  sourceLabel: string;
  depositName: string;
  depositId: string;
  depositAccount?: string | null;
  issuedAt: string;
  trackingCode?: string | null;
  message?: string | null;
}

/**
 * A payment parked for a human's approval. Read-only: approval happens in the
 * Railio dashboard, so this card has no action buttons.
 */
export interface ApprovalPendingView {
  paymentId: string;
  approvalId?: string | null;
  invoice: InvoiceView;
  amount: number;
  depositAccountName: string;
  depositId: string;
}

/** A payment parked on an interactive provider step a human must complete. */
export interface ActionPendingView {
  paymentId: string;
  actionType?: string | null;
  actionContext?: string | null;
}

export type CardWire =
  | { kind: "invoice"; invoice: InvoiceView }
  | { kind: "approval_pending"; approvalPending: ApprovalPendingView }
  | { kind: "action_pending"; actionPending: ActionPendingView }
  | { kind: "receipt"; receipt: ReceiptView };

export interface LogEntry {
  level: "DEBUG" | "INFO" | "WARN" | "ERROR";
  source: string;
  message: string;
}

// Config

export interface SourceAccountView {
  name: string;
  accountNumber: string;
  balance: number;
}

export interface DepositAccountView {
  name: string;
  accountNumber: string;
}

export interface OllamaView {
  baseUrl: string;
  model: string;
}

/**
 * Railio connection settings. There is no secret field: the client secret lives
 * in the backend environment and is reported only as `hasSecret`.
 */
export interface RailioView {
  baseUrl: string;
  clientId: string;
  sourceBankAccountId: string;
  hasSecret: boolean;
}

/**
 * Note the absence of a spending cap: limits and approval thresholds are Railio
 * policies set by a human in the Railio dashboard, not app settings.
 */
export interface ConfigView {
  sourceAccount: SourceAccountView;
  depositAccounts: DepositAccountView[];
  railio: RailioView;
  ollama: OllamaView;
  apiUrl?: string | null;
  hasSecret: boolean;
}
