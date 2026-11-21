"use client";
import { useEffect, useState } from "react";
import {
  BookOpen,
  FileText,
  Sparkles,
  ShieldCheck,
  Layers,
  Coins,
  Plus,
  LogOut,
  Save,
  Trash2,
  Check,
  PenLine,
} from "lucide-react";
import { api, setToken, resumeSession } from "../services/api";
import GenerationPanel from "../components/GenerationPanel";
import DocumentEditor from "../components/DocumentEditor";
import WalletPanel from "../components/WalletPanel";
import PublicBlocks from "../components/PublicBlocks";
import AccountSettings from "../components/AccountSettings";
const kinds = {
  COURSEWORK: "Կուրսային աշխատանք",
  THESIS: "Դիպլոմային աշխատանք",
  ESSAY: "Ռեֆերատ",
  REPORT: "Զեկույց",
};
export default function Home() {
  const [screen, setScreen] = useState("home"),
    [auth, setAuth] = useState(null),
    [profile, setProfile] = useState(null),
    [docs, setDocs] = useState([]),
    [cms, setCms] = useState({}),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false),
    [edit, setEdit] = useState(null),
    [notice, setNotice] = useState("");
  const [blocks, setBlocks] = useState([]);
  useEffect(() => {
    api("/public/blocks")
      .then(setBlocks)
      .catch(() => setNotice("Բովանդակությունն այժմ հասանելի չէ։"));
  }, []);
  useEffect(() => {
    api("/public/content")
      .then(setCms)
      .catch(() => setNotice("Սերվերի հետ կապը հասանելի չէ։"));
  }, []);
  async function reload() {
    const [p, d] = await Promise.all([api("/me"), api("/documents")]);
    setProfile(p);
    setDocs(d);
  }
  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    setError("");
    const data = Object.fromEntries(new FormData(e.target));
    try {
      const a = await api("/auth/" + auth, {
        method: "POST",
        body: JSON.stringify(data),
      });
      setToken(a.accessToken);
      await reload();
      setAuth(null);
      setScreen("dashboard");
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  async function save() {
    setBusy(true);
    setError("");
    try {
      const d = await api("/documents" + (edit.id ? "/" + edit.id : ""), {
        method: edit.id ? "PUT" : "POST",
        body: JSON.stringify(edit),
      });
      setEdit(d);
      await reload();
      setNotice("Փաստաթուղթը պահպանված է։");
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  async function remove(id) {
    if (!confirm("Ջնջե՞լ այս փաստաթուղթը։")) return;
    try {
      await api("/documents/" + id, { method: "DELETE" });
      await reload();
    } catch (e) {
      setError(e.message);
    }
  }
  async function logout() {
    try {
      await api("/auth/logout", { method: "POST", body: "{}" });
    } catch (e) {
      setError(e.message);
      return;
    }
    setToken("");
    setProfile(null);
    setDocs([]);
    setScreen("home");
    setEdit(null);
  }
  useEffect(() => {
    // Remove the legacy JavaScript-readable credential after upgrading.
    sessionStorage.removeItem("refresh");
    resumeSession()
      .then(async () => {
        await reload();
        setScreen("dashboard");
      })
      .catch(() => {});
  }, []);
  function create() {
    setEdit({ title: "", kind: "COURSEWORK", content: "", version: 0 });
    setNotice("");
  }
  return (
    <>
      <header>
        <a className="brand" href="/">
          {" "}
          {cms.logoUrl ? (
            <img src={cms.logoUrl} alt="" width="38" height="38" />
          ) : (
            <span className="brandmark">
              <Layers size={24} />
            </span>
          )}
          {cms.siteName || "Cognify"}
          <span className="branddot">.</span>
        </a>
        <nav>
          <a href="/tools">Գործիքներ</a>
          {blocks.some((b) => b.key === "how") && (
            <a href="#how">Ինչպես է աշխատում</a>
          )}
          <a href="/pricing">Coins</a>
          <a href="/faq">Հարցեր</a>
        </nav>
        <div className="header-actions">
          {profile ? (
            <button onClick={logout} className="outline">
              <LogOut size={16} />
              Ելք
            </button>
          ) : (
            <>
              <button
                className="plain"
                onClick={() => {
                  setAuth("login");
                  setError("");
                }}
              >
                Մուտք
              </button>
              <button
                onClick={() => {
                  setAuth("register");
                  setError("");
                }}
              >
                Սկսել անվճար
              </button>
            </>
          )}
        </div>
      </header>
      {error && (
        <div role="alert" className="alert">
          {error}
        </div>
      )}
      {notice && (
        <div role="status" className="notice">
          {notice}
          <button className="plain" onClick={() => setNotice("")}>
            ×
          </button>
        </div>
      )}
      {profile ? (
        <div className="workspace">
          <aside>
            <div className="eyebrow">ԱՇԽԱՏԱՆՔԱՅԻՆ ՏԱՐԱԾՔ</div>
            <button
              className={screen === "dashboard" ? "selected" : "plain"}
              onClick={() => {
                setScreen("dashboard");
                setEdit(null);
              }}
            >
              <Layers size={18} />
              Ընդհանուր պատկեր
            </button>
            <button
              className={screen === "documents" ? "selected" : "plain"}
              onClick={() => {
                setScreen("documents");
                setEdit(null);
              }}
            >
              <FileText size={18} />
              Իմ փաստաթղթերը
            </button>
            <button
              className={screen === "generation" ? "selected" : "plain"}
              onClick={() => {
                setScreen("generation");
                setEdit(null);
              }}
            >
              <Sparkles size={18} />
              AI գեներացիա
            </button>
            <button
              className={screen === "wallet" ? "selected" : "plain"}
              onClick={() => {
                setScreen("wallet");
                setEdit(null);
              }}
            >
              <Coins size={18} />
              Դրամապանակ
            </button>
            <button
              className={screen === "settings" ? "selected" : "plain"}
              onClick={() => {
                setScreen("settings");
                setEdit(null);
              }}
            >
              <ShieldCheck size={18} />
              Անվտանգություն
            </button>
            {profile.role === "ADMIN" && (
              <a className="button outline" href="/admin">
                Բովանդակության կառավարում
              </a>
            )}
            {profile.role === "ADMIN" && (
              <a className="button outline" href="/admin/finance">
                Coins-ի կառավարում
              </a>
            )}
            <div className="aside-bottom">
              <ShieldCheck size={24} />
              <p>Ձեր փաստաթղթերը հասանելի են միայն ձեզ։</p>
            </div>
          </aside>
          <main className="desk">
            <div className="desk-head">
              <div>
                <div className="eyebrow">COGNIFY WORKSPACE</div>
                <h1>Բարև, {profile.name}</h1>
                <p>Ձեր հաջորդ աշխատանքը սկսվում է այստեղ։</p>
              </div>
              <div className="generation-actions">
                <button
                  onClick={() => {
                    setScreen("generation");
                    setEdit(null);
                  }}
                >
                  Ստեղծել AI աշխատանք
                </button>
                <button className="outline" onClick={create}>
                  Դատարկ փաստաթուղթ
                </button>
              </div>
            </div>
            {edit ? (
              <DocumentEditor
                document={edit}
                onSaved={async (d) => {
                  setEdit(d);
                  await reload();
                }}
              />
            ) : screen === "generation" ? (
              <GenerationPanel
                onUpdate={reload}
                onOpen={async (id) => {
                  try {
                    const d = await api("/documents/" + id);
                    setEdit(d);
                    setScreen("documents");
                    await reload();
                  } catch (e) {
                    setError(e.message);
                  }
                }}
              />
            ) : screen === "settings" ? (
              <AccountSettings onSessionEnded={logout} />
            ) : screen === "wallet" ? (
              <WalletPanel onUpdate={reload} />
            ) : (
              <>
                <div className="stats">
                  <article>
                    <span>Իմ փաստաթղթերը</span>
                    <strong>{docs.length}</strong>
                    <FileText />
                  </article>
                  <article>
                    <span>Հասանելի Coins</span>
                    <strong>{profile.balance}</strong>
                    <Coins />
                  </article>
                  <article>
                    <span>Աշխատանքային ռեժիմ</span>
                    <strong className="small-strong">Գրել և խմբագրել</strong>
                    <PenLine />
                  </article>
                </div>
                <section className="documents">
                  <div className="row">
                    <h2>Վերջին փաստաթղթերը</h2>
                    <span>{docs.length} փաստաթուղթ</span>
                  </div>
                  {docs.length ? (
                    docs.map((d) => (
                      <article className="document-row" key={d.id}>
                        <div className="file-icon">
                          <FileText />
                        </div>
                        <div>
                          <h3>{d.title}</h3>
                          <p>
                            {kinds[d.kind]} ·{" "}
                            {new Date(d.updatedAt).toLocaleDateString("hy-AM")}
                          </p>
                        </div>
                        <button className="outline" onClick={() => setEdit(d)}>
                          Բացել
                        </button>
                        <button
                          aria-label="Ջնջել"
                          className="plain"
                          onClick={() => remove(d.id)}
                        >
                          <Trash2 size={18} />
                        </button>
                      </article>
                    ))
                  ) : (
                    <div className="empty">
                      <FileText size={42} />
                      <h3>Սկսեք առաջին փաստաթղթից</h3>
                      <p>Ստեղծեք սևագիր և պահպանեք ձեր գաղափարները։</p>
                      <button onClick={create}>
                        <Plus size={18} />
                        Ստեղծել փաստաթուղթ
                      </button>
                    </div>
                  )}
                </section>
              </>
            )}
          </main>
        </div>
      ) : (
        <main>
          <section className="hero">
            <div className="hero-copy">
              <div className="pill">
                <span />
                Ձեր ակադեմիական աշխատանքների համար
              </div>
              <h1>
                {cms.headline || (
                  <>
                    Գաղափարից՝
                    <br />
                    մինչև{" "}
                    <em>
                      պատրաստ
                      <br />
                      աշխատանք։
                    </em>
                  </>
                )}
              </h1>
              <p>
                {cms.subtitle ||
                  "Միասնական տարածք՝ ակադեմիական փաստաթղթեր ստեղծելու, խմբագրելու և ձեր պահանջներին համապատասխան ձևավորելու համար։"}
              </p>
              <div className="hero-actions">
                <button onClick={() => setAuth("register")}>
                  {cms.primaryCta || "Ստեղծել իմ հաշիվը"}
                </button>
                <a className="outline button" href="/tools">
                  {cms.secondaryCta || "Ուսումնասիրել գործիքները"}
                </a>
              </div>
              <div className="trust">
                <span>
                  <Check size={16} />
                  Հայերեն միջավայր
                </span>
                <span>
                  <Check size={16} />
                  Առանց բաժանորդագրության
                </span>
              </div>
            </div>
          </section>
          <PublicBlocks
            blocks={blocks}
            settings={cms}
            onStart={() => setAuth("register")}
          />
        </main>
      )}
      <footer>
        <a className="brand" href="/">
          {cms.siteName || "Cognify"}
        </a>
        <p>{cms.footer || "Ակադեմիական աշխատանքի ձեր անձնական տարածքը։"}</p>
        <a href="/contact">Կոնտակտներ</a>
        <a href="/privacy">Գաղտնիություն</a>
        <a href="/terms">Պայմաններ</a>
        <span>
          © {new Date().getFullYear()} {cms.siteName || "Cognify"}
        </span>
      </footer>
      {auth && (
        <div className="modal-backdrop" onClick={() => setAuth(null)}>
          <section
            className="modal"
            role="dialog"
            aria-modal="true"
            aria-label="Մուտք"
            onClick={(e) => e.stopPropagation()}
          >
            <button
              aria-label="Փակել"
              className="close plain"
              onClick={() => setAuth(null)}
            >
              ×
            </button>
            <div className="brandmark">
              <Layers />
            </div>
            <h2>
              {auth === "register"
                ? "Բարի գալուստ Cognify"
                : "Ուրախ ենք կրկին տեսնել ձեզ"}
            </h2>
            <p>Ձեր գաղափարների անձնական տարածքը։</p>
            <form onSubmit={submit}>
              {auth === "register" && (
                <label>
                  Անուն
                  <input
                    name="name"
                    required
                    maxLength={100}
                    autoComplete="name"
                  />
                </label>
              )}
              <label>
                Էլ․ փոստ
                <input
                  name="email"
                  type="email"
                  required
                  autoComplete="email"
                  maxLength={254}
                />
              </label>
              <label>
                Գաղտնաբառ
                <input
                  name="password"
                  type="password"
                  required
                  minLength={auth === "register" ? 10 : 1}
                  maxLength={72}
                  autoComplete={
                    auth === "register" ? "new-password" : "current-password"
                  }
                />
              </label>
              {auth === "register" && <small>Առնվազն 10 նիշ</small>}
              {error && (
                <p role="alert" className="form-error">
                  {error}
                </p>
              )}
              <button disabled={busy} type="submit">
                {busy
                  ? "Խնդրում ենք սպասել…"
                  : auth === "register"
                    ? "Ստեղծել հաշիվ"
                    : "Մուտք գործել"}
              </button>
            </form>
            {auth === "login" && (
              <a className="plain switch" href="/forgot-password">
                Մոռացե՞լ եք գաղտնաբառը։
              </a>
            )}
            <button
              className="plain switch"
              onClick={() => {
                setAuth(auth === "register" ? "login" : "register");
                setError("");
              }}
            >
              {auth === "register"
                ? "Արդեն ունե՞ք հաշիվ։ Մուտք գործել"
                : "Ստեղծել նոր հաշիվ"}
            </button>
          </section>
        </div>
      )}
    </>
  );
}
