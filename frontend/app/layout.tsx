import type { Metadata } from "next";
import Link from "next/link";
import "./globals.css";

export const metadata: Metadata = {
  title: "Invoice Agent",
  description: "Chat-first agent that reads invoices and pays them via a mocked Iranian transfer flow.",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>
        <div className="flex h-full flex-col">
          <header className="flex items-center justify-between border-b border-slate-200 bg-white px-4 py-3 dark:border-slate-800 dark:bg-slate-900">
            <Link href="/" className="text-lg font-semibold">
              💸 Invoice Agent
            </Link>
            <nav className="flex gap-4 text-sm">
              <Link href="/" className="hover:text-brand-600">Chat</Link>
              <Link href="/config" className="hover:text-brand-600">Config</Link>
            </nav>
          </header>
          <main className="min-h-0 flex-1">{children}</main>
        </div>
      </body>
    </html>
  );
}
