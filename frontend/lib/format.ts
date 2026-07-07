/** Formats a Rial amount with grouping, e.g. 12000000 -> "12,000,000 IRR". */
export function formatAmount(amount: number, currency = "IRR"): string {
  return `${amount.toLocaleString("en-US")} ${currency}`;
}

/** Human-readable text for an approval reason code from the backend. */
export function reasonText(reason: string): string {
  switch (reason) {
    case "UNKNOWN_DEPOSIT_ACCOUNT":
      return "Deposit account is not in your trusted list";
    case "ABOVE_CAP":
      return "Amount is above your auto-approval cap";
    default:
      return reason;
  }
}
