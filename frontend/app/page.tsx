"use client";

import Composer from "@/components/Composer";
import LogPanel from "@/components/LogPanel";
import MessageList from "@/components/MessageList";
import { useChat } from "@/lib/useChat";

export default function ChatPage() {
  const { items, logs, busy, thinking, send, reset } = useChat();

  return (
    <div className="flex h-full">
      <section className="flex min-w-0 flex-1 flex-col">
        <div className="flex items-center justify-between border-b border-slate-200 px-4 py-2 text-sm dark:border-slate-800">
          <span className="text-slate-500">Chat</span>
          <button className="text-slate-400 hover:text-brand-600" onClick={reset}>
            Clear
          </button>
        </div>
        <MessageList items={items} thinking={thinking} />
        <Composer onSend={send} disabled={busy} />
      </section>

      {/* Desktop-only agent log panel. */}
      <div className="hidden lg:flex">
        <LogPanel logs={logs} />
      </div>
    </div>
  );
}
