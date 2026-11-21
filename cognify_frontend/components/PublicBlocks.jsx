"use client";
import { FileText, Layers, Coins } from "lucide-react";
export default function PublicBlocks({ blocks, onStart, settings = {} }) {
  const start = (label) =>
    onStart ? (
      <button onClick={onStart}>{label}</button>
    ) : (
      <a className="button" href="/">
        {label}
      </a>
    );
  return blocks.map((block) => {
    if (block.key === "faq")
      return (
        <section className="section faq" id="faq" key={block.key}>
          <h2>{block.title}</h2>
          <p>{block.body}</p>
          {block.items.map((item, i) => (
            <details key={i}>
              <summary>{item.title}</summary>
              <p>{item.body}</p>
            </details>
          ))}
        </section>
      );
    if (block.key === "cta")
      return (
        <section className="cta" id="cta" key={block.key}>
          <Layers size={30} />
          <h2>{block.title}</h2>
          <p>{block.body}</p>
          {start(settings.ctaButton || "Ստեղծել հաշիվ")}
        </section>
      );
    if (block.key === "pricing")
      return (
        <section className="section pricing" id="pricing" key={block.key}>
          <Coins size={32} />
          <h2>{block.title}</h2>
          <p>{block.body}</p>
        </section>
      );
    if (block.key === "how")
      return (
        <section className="how section" id="how" key={block.key}>
          <div>
            <h2>{block.title}</h2>
            <p>{block.body}</p>
          </div>
          <div className="steps">
            {block.items.map((item, i) => (
              <div key={i}>
                <span>{i + 1}</span>
                <article>
                  <h3>{item.title}</h3>
                  <p>{item.body}</p>
                </article>
              </div>
            ))}
          </div>
        </section>
      );
    return (
      <section className="section" id={block.key} key={block.key}>
        <h2>{block.title}</h2>
        <p className="section-description">{block.body}</p>
        {block.items.length > 0 && (
          <div className="tool-grid">
            {block.items.map((item, i) => (
              <article key={i}>
                <div className="tool-icon">
                  <FileText />
                </div>
                <h3>{item.title}</h3>
                <p>{item.body}</p>
                {block.key === "tools" &&
                  start(settings.toolButton || "Սկսել աշխատանքը")}
              </article>
            ))}
          </div>
        )}
      </section>
    );
  });
}
