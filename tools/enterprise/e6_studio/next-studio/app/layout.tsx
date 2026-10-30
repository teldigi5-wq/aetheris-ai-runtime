import type { Metadata } from "next";
import "./globals.css";
export const metadata: Metadata = {
  title: "Aetheris E6 Studio — Cinematic Architecture Research",
  description: "Human-authored, local-only Next.js + React Three Fiber cinematic research. Stage45 NO_GO.",
  robots: { index: false, follow: false },
};
export default function RootLayout({children}:Readonly<{children:React.ReactNode}>) {
  return <html lang="en"><body>{children}</body></html>;
}
