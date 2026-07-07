import { formatAmount } from "@/lib/format";
import type { InvoiceView } from "@/lib/types";

/** Shows the invoice the agent extracted from the user's input. */
export default function InvoiceCard({ invoice }: { invoice: InvoiceView }) {
  return (
    <div className="max-w-md rounded-xl border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-700 dark:bg-slate-800">
      <div className="mb-2 flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-slate-400">
        🧾 Invoice
      </div>
      <p className="text-sm font-medium">{invoice.detail}</p>
      <dl className="mt-3 grid grid-cols-2 gap-y-1 text-sm">
        <dt className="text-slate-400">Amount</dt>
        <dd className="text-right font-semibold">{formatAmount(invoice.amount, invoice.currency)}</dd>
        <dt className="text-slate-400">Deposit to</dt>
        <dd className="text-right">{invoice.depositAccountName}</dd>
        <dt className="text-slate-400">Deposit ID</dt>
        <dd className="text-right font-mono text-xs">{invoice.depositId}</dd>
        {invoice.expiresAt && (
          <>
            <dt className="text-slate-400">Due</dt>
            <dd className="text-right">{invoice.expiresAt.slice(0, 10)}</dd>
          </>
        )}
      </dl>
    </div>
  );
}
