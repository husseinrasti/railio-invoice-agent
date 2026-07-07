import { formatAmount } from "@/lib/format";
import type { ReceiptView } from "@/lib/types";

const STATUS_STYLE: Record<ReceiptView["status"], string> = {
  SUCCESS: "border-emerald-300 bg-emerald-50 dark:border-emerald-700 dark:bg-emerald-950/40",
  FAILED: "border-rose-300 bg-rose-50 dark:border-rose-700 dark:bg-rose-950/40",
  PENDING: "border-slate-200 bg-white dark:border-slate-700 dark:bg-slate-800",
  AWAITING_APPROVAL: "border-slate-200 bg-white dark:border-slate-700 dark:bg-slate-800",
};

/** Transfer receipt — a preview before execution or the final confirmation after it. */
export default function ReceiptCard({ receipt }: { receipt: ReceiptView }) {
  const isFinal = receipt.kind === "FINAL";
  const title = isFinal
    ? receipt.status === "SUCCESS"
      ? "✅ Payment receipt"
      : receipt.status === "FAILED"
        ? "❌ Payment failed"
        : "Receipt"
    : "📄 Payment preview";

  return (
    <div className={`max-w-md rounded-xl border p-4 shadow-sm ${STATUS_STYLE[receipt.status]}`}>
      <div className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">{title}</div>
      <dl className="grid grid-cols-2 gap-y-1 text-sm">
        <dt className="text-slate-400">Amount</dt>
        <dd className="text-right font-semibold">{formatAmount(receipt.amount)}</dd>
        <dt className="text-slate-400">From</dt>
        <dd className="text-right">{receipt.sourceName}</dd>
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
            <dt className="text-slate-400">Tracking</dt>
            <dd className="text-right font-mono text-xs">{receipt.trackingCode}</dd>
          </>
        )}
      </dl>
      {receipt.message && <p className="mt-2 text-xs text-slate-500">{receipt.message}</p>}
    </div>
  );
}
