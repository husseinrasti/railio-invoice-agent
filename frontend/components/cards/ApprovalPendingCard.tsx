import { formatAmount } from "@/lib/format";
import type { ApprovalPendingView } from "@/lib/types";

/**
 * A payment a Railio policy parked for a human.
 *
 * Deliberately read-only: approval happens in the Railio dashboard by someone with the authority to
 * give it. The agent has no approve scope, so offering a button here would be a lie — the agent
 * polls and reports whatever the human decides.
 */
export default function ApprovalPendingCard({ awaiting }: { awaiting: ApprovalPendingView }) {
  return (
    <div className="max-w-md rounded-xl border border-amber-300 bg-amber-50 p-4 shadow-sm dark:border-amber-700 dark:bg-amber-950/40">
      <div className="mb-2 flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-amber-600 dark:text-amber-400">
        <span className="inline-block h-2 w-2 animate-pulse rounded-full bg-amber-500" />
        Awaiting approval in Railio
      </div>
      <p className="text-sm">
        <span className="font-semibold">{formatAmount(awaiting.amount)}</span> to{" "}
        <span className="font-semibold">{awaiting.depositAccountName}</span> (deposit{" "}
        <span className="font-mono text-xs">{awaiting.depositId}</span>).
      </p>
      <p className="mt-2 text-sm text-amber-700 dark:text-amber-300">
        A policy requires a person to approve this payment. Approve or reject it in the Railio
        dashboard — the agent cannot approve its own payments. The outcome will appear here.
      </p>
      {awaiting.approvalId && (
        <p className="mt-2 text-xs text-slate-500">
          Approval <span className="font-mono">{awaiting.approvalId}</span>
        </p>
      )}
    </div>
  );
}
