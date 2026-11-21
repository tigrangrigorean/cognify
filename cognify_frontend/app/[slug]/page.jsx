import { notFound } from "next/navigation";
import { readPublic } from "../../lib/public-content.mjs";
import PublicBlocks from "../../components/PublicBlocks";
const pages = {
  tools: "Գործիքներ",
  pricing: "Coins",
  faq: "Հաճախ տրվող հարցեր",
  contact: "Կոնտակտներ",
  privacy: "Գաղտնիության քաղաքականություն",
  terms: "Օգտագործման պայմաններ",
};
export const dynamic = "force-dynamic";
export async function generateMetadata({ params }) {
  const { slug } = await params;
  const cms = await readPublic("content").catch(() => ({}));
  return {
    title: `${pages[slug] || "Cognify"} — ${cms.siteName || "Cognify"}`,
    description: cms.seoDescription,
  };
}
export default async function Page({ params }) {
  const { slug } = await params;
  if (!pages[slug]) notFound();
  let cms, blocks;
  try {
    [cms, blocks] = await Promise.all([
      readPublic("content"),
      readPublic("blocks"),
    ]);
  } catch {
    return (
      <main className="section">
        <h1>{pages[slug]}</h1>
        <p role="alert">Բովանդակությունն այժմ հասանելի չէ։ Փորձեք ավելի ուշ։</p>
        <a href="/">Գլխավոր էջ</a>
      </main>
    );
  }
  const block = blocks.filter((b) => b.key === slug);
  return (
    <>
      <header>
        <a className="brand" href="/">
          {cms.siteName || "Cognify"}
        </a>
        <nav>
          <a href="/tools">Գործիքներ</a>
          <a href="/pricing">Coins</a>
          <a href="/faq">Հարցեր</a>
          <a href="/contact">Կոնտակտներ</a>
        </nav>
      </header>
      {["tools", "pricing", "faq"].includes(slug) ? (
        block.length ? (
          <PublicBlocks blocks={block} settings={cms} />
        ) : (
          <main className="section">
            <h1>{pages[slug]}</h1>
            <p>Այս բաժինն այժմ հրապարակված չէ։</p>
          </main>
        )
      ) : (
        <main className="section info-page">
          <h1>{pages[slug]}</h1>
          <div style={{ whiteSpace: "pre-wrap" }}>
            {cms[slug] || "Տեղեկությունը դեռ չի հրապարակվել։"}
          </div>
        </main>
      )}
      <footer>
        <a href="/">{cms.siteName || "Cognify"}</a>
        <a href="/privacy">Գաղտնիություն</a>
        <a href="/terms">Պայմաններ</a>
      </footer>
    </>
  );
}
