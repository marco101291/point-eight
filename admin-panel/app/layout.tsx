import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "0.8 — El Sistema",
  description: "Panel de administración del compound.",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="es">
      <body>{children}</body>
    </html>
  );
}
