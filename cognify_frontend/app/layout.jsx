import "./globals.css";
import { readPublic } from "../lib/public-content.mjs";
export const dynamic = "force-dynamic";
export async function generateMetadata() {
  const content = await readPublic("content").catch(() => ({}));
  return {
    title: content.seoTitle || "Cognify — ակադեմիական աշխատանքների հարթակ",
    description:
      content.seoDescription || "Ակադեմիական փաստաթղթերի անձնական տարածք։",
  };
}
export default function Layout({ children }) {
  return (
    <html lang="hy">
      <body>{children}</body>
    </html>
  );
}
