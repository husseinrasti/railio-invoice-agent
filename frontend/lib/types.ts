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

export interface ReceiptView {
  paymentId: string;
  kind: "PREVIEW" | "FINAL";
  status: "PENDING" | "AWAITING_APPROVAL" | "SUCCESS" | "FAILED";
  amount: number;
  sourceName: string;
  sourceAccount: string;
  depositName: string;
  depositId: string;
  depositAccount?: string | null;
  issuedAt: string;
  trackingCode?: string | null;
  message?: string | null;
}

export interface ApprovalView {
  paymentId: string;
  invoice: InvoiceView;
  amount: number;
  depositAccountName: string;
  depositId: string;
  reasons: string[];
}

export type CardWire =
  | { kind: "invoice"; invoice: InvoiceView }
  | { kind: "approval"; approval: ApprovalView }
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

export interface ConfigView {
  sourceAccount: SourceAccountView;
  depositAccounts: DepositAccountView[];
  autoApprovalCap: number;
  ollama: OllamaView;
  apiUrl?: string | null;
  hasSecret: boolean;
}
