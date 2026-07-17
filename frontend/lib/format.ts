import type { PaymentStatus } from "./types";

/** Formats a Rial amount with grouping, e.g. 12000000 -> "12,000,000 IRR". */
export function formatAmount(amount: number, currency = "IRR"): string {
  return `${amount.toLocaleString("en-US")} ${currency}`;
}

/** Human-readable text for a Railio payment status. */
export function statusText(status: PaymentStatus): string {
  switch (status) {
    case "COMPLETED":
      return "Completed";
    case "FAILED":
      return "Failed";
    case "CANCELLED":
      return "Cancelled";
    case "EXPIRED":
      return "Expired";
    case "AWAITING_APPROVAL":
      return "Awaiting approval";
    case "AWAITING_ACTION":
      return "Awaiting action";
    case "EXECUTING":
      return "Executing";
    case "CREATED":
      return "Submitted";
  }
}
