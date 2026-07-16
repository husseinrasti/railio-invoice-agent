import type { ActionPendingView } from "@/lib/types";

/**
 * A payment parked because the provider needs an interactive step (OTP, redirect).
 *
 * The step belongs to a person: the agent relays the request, it never invents an OTP.
 */
export default function ActionPendingCard({ awaiting }: { awaiting: ActionPendingView }) {
  return (
    <div className="max-w-md rounded-xl border border-sky-300 bg-sky-50 p-4 shadow-sm dark:border-sky-700 dark:bg-sky-950/40">
      <div className="mb-2 flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-sky-600 dark:text-sky-400">
        <span className="inline-block h-2 w-2 animate-pulse rounded-full bg-sky-500" />
        Action needed{awaiting.actionType ? `: ${awaiting.actionType}` : ""}
      </div>
      <p className="text-sm text-sky-800 dark:text-sky-200">
        The payment provider needs a step completed before this transfer can continue. Complete it in
        Railio; the outcome will appear here.
      </p>
      {awaiting.actionContext && (
        <pre className="mt-2 overflow-x-auto rounded bg-white/60 p-2 text-xs dark:bg-black/20">
          {awaiting.actionContext}
        </pre>
      )}
      <p className="mt-2 text-xs text-slate-500">
        Payment <span className="font-mono">{awaiting.paymentId}</span>
      </p>
    </div>
  );
}
