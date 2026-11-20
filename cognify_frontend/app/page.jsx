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
          <span className="brandmark">
            <Layers size={24} />
          </span>
          cognify<span className="branddot">.</span>
        </a>
        <nav>
          <a href="#tools">Գործիքներ</a>
          <a href="#how">Ինչպես է աշխատում</a>
          <a href="#pricing">Coins</a>
          <a href="#faq">Հարցեր</a>
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
              <button onClick={create}>
                <Plus size={18} />
                Նոր փաստաթուղթ
              </button>
            </div>
            {edit ? (
              <section className="editor">
                <div className="row">
                  <h2>Փաստաթղթի խմբագրիչ</h2>
                  <button onClick={save} disabled={busy || !edit.title.trim()}>
                    <Save size={18} />
                    Պահպանել
                  </button>
                </div>
                <label>
                  Վերնագիր
                  <input
                    value={edit.title}
                    maxLength={200}
                    onChange={(e) =>
                      setEdit({ ...edit, title: e.target.value })
                    }
                  />
                </label>
                <label>
                  Աշխատանքի տեսակ
                  <select
                    value={edit.kind}
                    onChange={(e) => setEdit({ ...edit, kind: e.target.value })}
                  >
                    {Object.entries(kinds).map(([k, v]) => (
                      <option key={k} value={k}>
                        {v}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  Բովանդակություն
                  <textarea
                    rows={18}
                    value={edit.content}
                    maxLength={200000}
                    onChange={(e) =>
                      setEdit({ ...edit, content: e.target.value })
                    }
                  />
                </label>
                <small>Ձեռքով խմբագրում • AI գեներացիան դեռ հասանելի չէ</small>
              </section>
            ) : screen === "settings" ? (
              <AccountSettings onSessionEnded={logout} />
            ) : screen === "wallet" ? (
              <section className="editor">
                <Coins size={36} />
                <h2>{profile.balance} Coins</h2>
                <p>Ձեր հասանելի մնացորդը</p>
                <div className="notice">
                  Գնումները դեռ միացված չեն։ Վճարումներ չեն ընդունվում։
                </div>
              </section>
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
                    <strong className="small-strong">Ձեռքով խմբագրում</strong>
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
                  Ստեղծել իմ հաշիվը
                </button>
                <a className="outline button" href="#tools">
                  Ուսումնասիրել գործիքները
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
            <div className="hero-visual">
              <div className="visual-top">
                <span className="mini-logo">
                  <Layers size={18} />
                  cognify
                </span>
                <span>Ձեր աշխատանքային տարածքը</span>
              </div>
              <div className="visual-body">
                <div className="mock-sidebar">
                  <Layers />
                  <FileText />
                  <BookOpen />
                  <Coins />
                </div>
                <div className="paper">
                  <div className="paper-tag">ԱՇԽԱՏԱՆՔԻ ԿԱՌՈՒՑՎԱԾՔ</div>
                  <h3>
                    Գաղափարները՝
                    <br />
                    հստակ կառուցվածքով
                  </h3>
                  <div className="paper-line long" />
                  <div className="paper-line" />
                  <div className="paper-divider" />
                  {[
                    "Ներածություն",
                    "Հիմնական բաժիններ",
                    "Եզրակացություն",
                    "Գրականության ցանկ",
                  ].map((x, i) => (
                    <div className="outline-item" key={x}>
                      <span>{String(i + 1).padStart(2, "0")}</span>
                      {x}
                      <Check size={14} />
                    </div>
                  ))}
                  <div className="paper-lines">
                    <i />
                    <i />
                    <i />
                  </div>
                </div>
              </div>
              <div className="floating-card">
                <div className="file-icon">
                  <ShieldCheck />
                </div>
                <div>
                  <b>Ձեր պահանջներով</b>
                  <span>Ձեր աշխատանքը, ձեր վերահսկողությունը</span>
                </div>
              </div>
            </div>
          </section>
          <div className="intro-strip">
            <span>ՄԵԿ ՀԱՐԹԱԿ, ԱՎԵԼԻ ՀՍՏԱԿ ԱՇԽԱՏԱՆՔ</span>
            <b>Գրեք։</b>
            <b>Կառուցեք։</b>
            <b>Խմբագրեք։</b>
            <b>Պահպանեք։</b>
          </div>
          <section id="tools" className="section">
            <div className="eyebrow">ԱՅՍՏԵՂ Է ՍԿՍՎՈՒՄ ԱՇԽԱՏԱՆՔԸ</div>
            <h2>Տարածք յուրաքանչյուր գաղափարի համար</h2>
            <p className="section-description">
              Սկսեք սևագրից։ Մնացած հնարավորությունները զարգացնում ենք փուլ առ
              փուլ։
            </p>
            <div className="tool-grid">
              {Object.entries(kinds).map(([key, label], i) => (
                <article key={key}>
                  <div className="tool-icon">
                    {i === 1 ? <BookOpen /> : <FileText />}
                  </div>
                  <span className="available">Սևագրի խմբագրիչ</span>
                  <h3>{label}</h3>
                  <p>
                    Ստեղծեք, խմբագրեք և պահպանեք ձեր աշխատանքը անձնական
                    տարածքում։
                  </p>
                  <button
                    className="text-button"
                    onClick={() => setAuth("register")}
                  >
                    Սկսել աշխատանքը
                  </button>
                </article>
              ))}
            </div>
            <div className="upcoming">
              <Sparkles />
              <div>
                <b>Հաջորդը՝ AI գործիքներ և խելացի ձևավորում</b>
                <p>
                  Փուլային գեներացիան, նմուշների վերլուծությունը և DOCX/PDF
                  արտահանումը մշակման փուլում են։
                </p>
              </div>
              <span>Շուտով</span>
            </div>
          </section>
          <section className="how section" id="how">
            <div>
              <div className="eyebrow">ՊԱՐԶ ԱՇԽԱՏԱՆՔԱՅԻՆ ԸՆԹԱՑՔ</div>
              <h2>
                Կենտրոնացեք
                <br />
                ձեր գաղափարի վրա։
              </h2>
              <p>
                Աշխատանքը պահպանվում է ձեր հաշվում՝
                <br />
                հաջորդ անգամ շարունակելու համար։
              </p>
            </div>
            <div className="steps">
              {[
                [
                  "Ստեղծեք հաշիվ",
                  "Գրանցվեք և բացեք ձեր անձնական աշխատանքային տարածքը։",
                ],
                [
                  "Սկսեք փաստաթուղթ",
                  "Ընտրեք աշխատանքի տեսակը և ավելացրեք բովանդակությունը։",
                ],
                [
                  "Խմբագրեք և պահպանեք",
                  "Վերադարձեք ձեր աշխատանքին ցանկացած պահի։",
                ],
              ].map(([t, d], i) => (
                <div key={t}>
                  <span>{i + 1}</span>
                  <article>
                    <h3>{t}</h3>
                    <p>{d}</p>
                  </article>
                </div>
              ))}
            </div>
          </section>
          <section className="section pricing" id="pricing">
            <Coins size={32} />
            <div className="eyebrow">ՎՃԱՐԵՔ ՄԻԱՅՆ ՕԳՏԱԳՈՐԾՄԱՆ ՀԱՄԱՐ</div>
            <h2>Coins։ Առանց բաժանորդագրության։</h2>
            <p>
              Հետագայում Coins-ը կօգտագործվի AI գործիքների համար։
              <br />
              Այժմ սևագրերի ստեղծումն ու խմբագրումն անվճար են։
            </p>
            <span className="pill">Coins-ի գնումները դեռ հասանելի չեն</span>
          </section>
          <section className="section faq" id="faq">
            <h2>Հաճախ տրվող հարցեր</h2>
            {[
              [
                "Ի՞նչ կարող եմ անել հիմա։",
                "Կարող եք ստեղծել հաշիվ, ստեղծել ու խմբագրել փաստաթղթեր և պահպանել դրանք ձեր անձնական տարածքում։",
              ],
              [
                "AI գեներացիան հասանելի՞ է։",
                "Դեռ ոչ։ Այս տարբերակում հասանելի է ձեռքով խմբագրումը։ AI գործիքները կավելացվեն հետագա փուլերում։",
              ],
              [
                "Կա՞ ամսական բաժանորդագրություն։",
                "Ոչ։ Նախատեսված է Coins-ի միանվագ գնում։ Այս պահին վճարումներ չեն ընդունվում։",
              ],
            ].map(([q, a]) => (
              <details key={q}>
                <summary>{q}</summary>
                <p>{a}</p>
              </details>
            ))}
          </section>
          <section className="cta">
            <Layers size={30} />
            <h2>Ձեր հաջորդ գաղափարին՝ նոր սկիզբ։</h2>
            <button onClick={() => setAuth("register")}>
              Ստեղծել անվճար հաշիվ
            </button>
          </section>
        </main>
      )}
      <footer>
        <a className="brand" href="/">
          cognify.
        </a>
        <p>{cms.footer || "Ակադեմիական աշխատանքի ձեր անձնական տարածքը։"}</p>
        <span>© {new Date().getFullYear()} Cognify</span>
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
