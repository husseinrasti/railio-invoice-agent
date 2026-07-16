import { formatAmount, statusText } from "@/lib/format";
import type { PaymentStatus, ReceiptView } from "@/lib/types";

const STATUS_STYLE: Record<PaymentStatus, string> = {
  COMPLETED: "border-emerald-300 bg-emerald-50 dark:border-emerald-700 dark:bg-emerald-950/40",
  FAILED: "border-rose-300 bg-rose-50 dark:border-rose-700 dark:bg-rose-950/40",
  CANCELLED: "border-rose-300 bg-rose-50 dark:border-rose-700 dark:bg-rose-950/40",
  EXPIRED: "border-rose-300 bg-rose-50 dark:border-rose-700 dark:bg-rose-950/40",
  AWAITING_APPROVAL: "border-amber-300 bg-amber-50 dark:border-amber-700 dark:bg-amber-950/40",
  AWAITING_ACTION: "border-sky-300 bg-sky-50 dark:border-sky-700 dark:bg-sky-950/40",
  AWAITING_OTP: "border-sky-300 bg-sky-50 dark:border-sky-700 dark:bg-sky-950/40",
  CREATED: "border-slate-200 bg-white dark:border-slate-700 dark:bg-slate-800",
  POLICY_CHECKING: "border-slate-200 bg-white dark:border-slate-700 dark:bg-slate-800",
  EXECUTING: "border-slate-200 bg-white dark:border-slate-700 dark:bg-slate-800",
};

function title(receipt: ReceiptView): string {
  if (receipt.kind !== "FINAL") return "📄 Payment proposed";
  switch (receipt.status) {
    case "COMPLETED":
      return "✅ Payment receipt";
    case "FAILED":
      return "❌ Payment failed";
    case "CANCELLED":
      return "🚫 Payment cancelled";
    case "EXPIRED":
      return "⌛ Payment expired";
    default:
      return `⏳ ${statusText(receipt.status)}`;
  }
}

/**
 * Transfer receipt — a preview of what was proposed, or the final outcome once Railio settled it.
 *
 * A preview is explicitly not a payment: the money only moves when the status says COMPLETED.
 */
export default function ReceiptCard({ receipt }: { receipt: ReceiptView }) {
  return (
    <div className={`max-w-md rounded-xl border p-4 shadow-sm ${STATUS_STYLE[receipt.status]}`}>
      <div className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">
        {title(receipt)}
      </div>
      <dl className="grid grid-cols-2 gap-y-1 text-sm">
        <dt className="text-slate-400">Amount</dt>
        <dd className="text-right font-semibold">{formatAmount(receipt.amount)}</dd>
        <dt className="text-slate-400">Status</dt>
        <dd className="text-right">{statusText(receipt.status)}</dd>
        <dt className="text-slate-400">From</dt>
        <dd className="truncate text-right">{receipt.sourceLabel}</dd>
        <dt className="text-slate-400">To</dt>
        <dd className="text-right">{receipt.depositName}</dd>
        <dt className="text-slate-400">Deposit ID</dt>
        <dd className="text-right font-mono text-xs">{receipt.depositId}</dd>
        {receipt.depositAccount && (
          <>
            <dt className="text-slate-400">Account</dt>
            <dd className="truncate text-right font-mono text-xs">{receipt.depositAccount}</dd>
          </>
        )}
        {receipt.trackingCode && (
          <>
            <dt className="text-slate-400">Reference</dt>
            <dd className="text-right font-mono text-xs">{receipt.trackingCode}</dd>
          </>
        )}
      </dl>
      {receipt.message && <p className="mt-2 text-xs text-slate-500">{receipt.message}</p>}
    </div>
  );
}
