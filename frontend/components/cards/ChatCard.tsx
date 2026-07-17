import type { CardWire } from "@/lib/types";
import ActionPendingCard from "./ActionPendingCard";
import ApprovalPendingCard from "./ApprovalPendingCard";
import InvoiceCard from "./InvoiceCard";
import ReceiptCard from "./ReceiptCard";

/** Dispatches an SSE card payload to the right card component. */
export default function ChatCard({ card }: { card: CardWire }) {
  switch (card.kind) {
    case "invoice":
      return <InvoiceCard invoice={card.invoice} />;
    case "approval_pending":
      return <ApprovalPendingCard awaiting={card.approvalPending} />;
    case "action_pending":
      return <ActionPendingCard awaiting={card.actionPending} />;
    case "receipt":
      return <ReceiptCard receipt={card.receipt} />;
  }
}
