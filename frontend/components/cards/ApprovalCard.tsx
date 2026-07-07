"use client";

import { useState } from "react";
import { formatAmount, reasonText } from "@/lib/format";
import type { ApprovalView } from "@/lib/types";

/** Asks the user to approve or reject a payment that fell outside the auto-pay policy. */
export default function ApprovalCard({
  approval,
  runId,
  onApprove,
}: {
  approval: ApprovalView;
  runId: string;
  onApprove: (runId: string, approved: boolean) => void;
}) {
  const [decided, setDecided] = useState<null | boolean>(null);

  const decide = (approved: boolean) => {
    setDecided(approved);
    onApprove(runId, approved);
  };

  return (
    <div className="max-w-md rounded-xl border border-amber-300 bg-amber-50 p-4 shadow-sm dark:border-amber-700 dark:bg-amber-950/40">
      <div className="mb-2 text-xs font-semibold uppercase tracking-wide text-amber-600 dark:text-amber-400">
        ⚠️ Approval required
      </div>
      <p className="text-sm">
        Pay <span className="font-semibold">{formatAmount(approval.amount)}</span> to{" "}
        <span className="font-semibold">{approval.depositAccountName}</span> (deposit{" "}
        <span className="font-mono text-xs">{approval.depositId}</span>).
      </p>
      <ul className="mt-2 list-inside list-disc text-sm text-amber-700 dark:text-amber-300">
        {approval.reasons.map((r) => (
          <li key={r}>{reasonText(r)}</li>
        ))}
      </ul>
      {decided === null ? (
        <div className="mt-3 flex gap-2">
          <button
            className="rounded-lg bg-emerald-600 px-4 py-1.5 text-sm font-medium text-white hover:bg-emerald-700"
            onClick={() => decide(true)}
          >
            Approve
          </button>
          <button
            className="rounded-lg bg-rose-600 px-4 py-1.5 text-sm font-medium text-white hover:bg-rose-700"
            onClick={() => decide(false)}
          >
            Reject
          </button>
        </div>
      ) : (
        <p className="mt-3 text-sm font-medium text-slate-500">
          {decided ? "You approved this payment." : "You rejected this payment."}
        </p>
      )}
    </div>
  );
}
