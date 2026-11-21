"use client";
import { useEffect, useState } from "react";
import { api, resumeSession } from "../services/api";
const labels = {
  siteName: "Կայքի անվանում",
  logoUrl: "Լոգոյի HTTPS հասցե կամ տեղական ուղի",
  headline: "Գլխավոր վերնագիր",
  subtitle: "Ենթավերնագիր",
  primaryCta: "Գլխավոր կոճակ",
  secondaryCta: "Գործիքների հղման տեքստ",
  ctaButton: "Վերջնական կանչի կոճակ",
  toolButton: "Գործիքի կոճակ",
  seoTitle: "SEO վերնագիր",
  seoDescription: "SEO նկարագրություն",
  contact: "Կոնտակտներ",
  footer: "Էջատակի տեքստ",
  privacy: "Գաղտնիության քաղաքականություն",
  terms: "Օգտագործման պայմաններ",
};
export default function AdminCms() {
  const [state, setState] = useState("loading"),
    [settings, setSettings] = useState({}),
    [blocks, setBlocks] = useState([]),
    [audit, setAudit] = useState([]),
    [page, setPage] = useState(0),
    [error, setError] = useState(""),
    [notice, setNotice] = useState(""),
    [busy, setBusy] = useState(false);
  async function logs(p = 0) {
    setAudit(await api("/admin/audit?page=" + p));
    setPage(p);
  }
  useEffect(() => {
    (async () => {
      try {
        await resumeSession();
        const me = await api("/me");
        if (me.role !== "ADMIN") {
          setState("denied");
          return;
        }
        const [s, b] = await Promise.all([
          api("/public/content"),
          api("/admin/blocks"),
        ]);
        setSettings(s);
        setBlocks(b);
        await logs();
        setState("ready");
      } catch (e) {
        setError(e.message);
        setState("denied");
      }
    })();
  }, []);
  async function pageLogs(p) {
    setBusy(true);
    setError("");
    try {
      await logs(p);
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  async function saveSetting(e, key) {
    e.preventDefault();
    await run(async () => {
      await api("/admin/content/" + key, {
        method: "PUT",
        body: JSON.stringify({ value: settings[key] || "" }),
      });
    });
  }
  async function run(task) {
    setBusy(true);
    setError("");
    setNotice("");
    try {
      await task();
      await logs();
      setNotice("Փոփոխությունը պահպանված է։");
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  function update(key, change) {
    setBlocks((old) =>
      old.map((b) => (b.key === key ? { ...b, ...change } : b)),
    );
  }
  async function saveBlock(e, b) {
    e.preventDefault();
    await run(async () => {
      const saved = await api("/admin/blocks/" + b.key, {
        method: "PUT",
        body: JSON.stringify(b),
      });
      update(b.key, saved);
    });
  }
  if (state === "loading")
    return (
      <main className="section">
        <p role="status">Բեռնվում է…</p>
      </main>
    );
  if (state === "denied")
    return (
      <main className="section">
        <h1>Մուտքը սահմանափակված է</h1>
        <p>{error || "Այս բաժինը նախատեսված է ադմինիստրատորի համար։"}</p>
        <a className="button" href="/">
          Գլխավոր էջ
        </a>
      </main>
    );
  return (
    <main className="admin-page">
      <header>
        <a className="brand" href="/">
          Cognify
        </a>
        <h2>Բովանդակության կառավարում</h2>
      </header>
      <div className="section">
        <p>
          Փոփոխությունները հրապարակվում են անմիջապես։ Յուրաքանչյուր պահպանում
          գրանցվում է գործողությունների մատյանում։
        </p>
        {error && (
          <div role="alert" className="alert">
            {error}
          </div>
        )}
        {notice && (
          <div role="status" className="notice">
            {notice}
          </div>
        )}
        <h2>Ընդհանուր կարգավորումներ</h2>
        <div className="admin-settings">
          {Object.entries(labels).map(([key, label]) => (
            <form
              key={key}
              className="editor"
              onSubmit={(e) => saveSetting(e, key)}
            >
              <label>
                {label}
                <textarea
                  required
                  rows={key === "privacy" || key === "terms" ? 6 : 2}
                  maxLength={
                    [
                      "siteName",
                      "primaryCta",
                      "secondaryCta",
                      "ctaButton",
                      "toolButton",
                      "seoTitle",
                    ].includes(key)
                      ? 200
                      : 10000
                  }
                  value={settings[key] || ""}
                  onChange={(e) =>
                    setSettings({ ...settings, [key]: e.target.value })
                  }
                />
              </label>
              {key === "logoUrl" && (
                <small>Լոգոն հեռացնելու համար նշեք /logo.svg։</small>
              )}
              <button disabled={busy}>Պահպանել</button>
            </form>
          ))}
        </div>
        <h2>Գլխավոր էջի բաժիններ</h2>
        <p>Հերթականությունը որոշվում է դիրքի թվով՝ փոքրից մեծ։</p>
        {blocks.map((b) => (
          <form
            key={b.key}
            className="editor cms-block-editor"
            onSubmit={(e) => saveBlock(e, b)}
          >
            <div className="row">
              <h3>{b.key}</h3>
              <label className="check-label">
                <input
                  type="checkbox"
                  checked={b.visible}
                  onChange={(e) => update(b.key, { visible: e.target.checked })}
                />
                Տեսանելի է
              </label>
            </div>
            <label>
              Դիրք
              <input
                type="number"
                min={0}
                max={1000}
                required
                value={b.position}
                onChange={(e) =>
                  update(b.key, { position: Number(e.target.value) })
                }
              />
            </label>
            <label>
              Վերնագիր
              <input
                required
                maxLength={200}
                value={b.title}
                onChange={(e) => update(b.key, { title: e.target.value })}
              />
            </label>
            <label>
              Տեքստ
              <textarea
                maxLength={10000}
                value={b.body}
                onChange={(e) => update(b.key, { body: e.target.value })}
              />
            </label>
            {b.items.map((item, i) => (
              <div className="cms-item" key={i}>
                <label>
                  Տարրի վերնագիր
                  <input
                    required
                    maxLength={200}
                    value={item.title}
                    onChange={(e) =>
                      update(b.key, {
                        items: b.items.map((x, j) =>
                          i === j ? { ...x, title: e.target.value } : x,
                        ),
                      })
                    }
                  />
                </label>
                <label>
                  Տարրի տեքստ
                  <textarea
                    required
                    maxLength={3000}
                    value={item.body}
                    onChange={(e) =>
                      update(b.key, {
                        items: b.items.map((x, j) =>
                          i === j ? { ...x, body: e.target.value } : x,
                        ),
                      })
                    }
                  />
                </label>
                <button
                  type="button"
                  className="outline"
                  onClick={() =>
                    update(b.key, { items: b.items.filter((_, j) => i !== j) })
                  }
                >
                  Հեռացնել տարրը
                </button>
              </div>
            ))}
            {["benefits", "tools", "how", "examples", "faq"].includes(
              b.key,
            ) && (
              <button
                type="button"
                className="outline"
                disabled={b.items.length >= 20}
                onClick={() =>
                  update(b.key, {
                    items: [...b.items, { title: "", body: "" }],
                  })
                }
              >
                Ավելացնել տարր
              </button>
            )}
            <button disabled={busy}>Պահպանել բաժինը</button>
          </form>
        ))}
        <h2>Գործողությունների մատյան</h2>
        <p>Մատյանը չի պարունակում օգտատերերի անձնական փաստաթղթերը։</p>
        {audit.map((a) => (
          <details key={a.id}>
            <summary>
              {a.target} · {new Date(a.createdAt).toLocaleString("hy-AM")}
            </summary>
            <p>Օգտատեր՝ {a.actorId}</p>
            <h3>Նախորդ արժեքը</h3>
            <pre>{a.beforeValue || "—"}</pre>
            <h3>Նոր արժեքը</h3>
            <pre>{a.afterValue}</pre>
          </details>
        ))}
        <div className="row">
          <button
            className="outline"
            disabled={busy || page === 0}
            onClick={() => pageLogs(page - 1)}
          >
            Նախորդ էջ
          </button>
          <span>{page + 1}</span>
          <button
            className="outline"
            disabled={busy || audit.length < 50}
            onClick={() => pageLogs(page + 1)}
          >
            Հաջորդ էջ
          </button>
        </div>
      </div>
    </main>
  );
}
