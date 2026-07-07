import type { CardWire } from "@/lib/types";
import ApprovalCard from "./ApprovalCard";
import InvoiceCard from "./InvoiceCard";
import ReceiptCard from "./ReceiptCard";

/** Dispatches an SSE card payload to the right card component. */
export default function ChatCard({
  card,
  runId,
  onApprove,
}: {
  card: CardWire;
  runId: string;
  onApprove: (runId: string, approved: boolean) => void;
}) {
  switch (card.kind) {
    case "invoice":
      return <InvoiceCard invoice={card.invoice} />;
    case "approval":
      return <ApprovalCard approval={card.approval} runId={runId} onApprove={onApprove} />;
    case "receipt":
      return <ReceiptCard receipt={card.receipt} />;
  }
}
