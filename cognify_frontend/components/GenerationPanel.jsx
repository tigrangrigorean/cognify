"use client";
import { useEffect, useState } from "react";
import { api } from "../services/api";
import { pendingOperation } from "../services/pending-operation.mjs";
const statuses = {
  QUEUED: "Հերթում է",
  RUNNING: "Գեներացվում է",
  AWAITING_PLAN: "Հաստատեք պլանը",
  PAUSED: "Դադարեցված է",
  FAILED: "Չհաջողվեց",
  CANCELLED: "Չեղարկված է",
  COMPLETED: "Պատրաստ է",
};
const errors = {
  PROVIDER_UNAVAILABLE: "AI ծառայությունն այժմ հասանելի չէ։",
  PROVIDER_ERROR: "AI ծառայությունը չկարողացավ կատարել հարցումը։",
  PROVIDER_RATE_LIMIT:
    "AI ծառայության սահմանաչափը սպառված է։ Փորձեք ավելի ուշ։",
  PROVIDER_TIMEOUT: "AI ծառայությունը ժամանակին չպատասխանեց։",
  OUTPUT_INCOMPLETE: "Պատասխանը կիսատ է։ Փորձեք կրկին կամ նվազեցրեք ծավալը։",
  INVALID_PLAN: "Ստացված պլանը չհաջողվեց մշակել։",
  RETRY_LIMIT: "Կրկնման սահմանաչափը սպառված է։ Ստեղծեք նոր աշխատանք։",
};
export default function GenerationPanel({ onOpen, onUpdate }) {
  const [config, setConfig] = useState(null),
    [pages, setPages] = useState(3),
    [quote, setQuote] = useState(null),
    [quoteError, setQuoteError] = useState(""),
    [jobs, setJobs] = useState([]),
    [selectedId, setSelectedId] = useState(null),
    [plan, setPlan] = useState(""),
    [busy, setBusy] = useState(false),
    [error, setError] = useState("");
  const selected = jobs.find((j) => j.id === selectedId) || jobs[0];
  async function refresh() {
    setJobs(await api("/generation/jobs"));
  }
  useEffect(() => {
    api("/generation/config")
      .then(setConfig)
      .catch((e) => setError(e.message));
    refresh().catch((e) => setError(e.message));
    const timer = setInterval(
      () => refresh().catch((e) => setError(e.message)),
      3000,
    );
    return () => clearInterval(timer);
  }, []);
  useEffect(() => {
    let alive = true;
    setQuote(null);
    setQuoteError("");
    if (pages >= 1 && pages <= 40)
      api("/generation/quote?pages=" + pages)
        .then((q) => {
          if (alive) setQuote(q);
        })
        .catch(() => {
          if (alive) setQuoteError("Գեներացիայի սակագինը դեռ կարգավորված չէ։");
        });
    return () => {
      alive = false;
    };
  }, [pages]);
  useEffect(() => {
    if (selected?.status === "AWAITING_PLAN") setPlan(selected.plan.join("\n"));
    if (["COMPLETED", "FAILED", "CANCELLED"].includes(selected?.status))
      onUpdate().catch(() => {});
  }, [selected?.id, selected?.status]);
  async function start(e) {
    e.preventDefault();
    if (!quote || !config?.available) return;
    setBusy(true);
    setError("");
    const form = Object.fromEntries(new FormData(e.currentTarget));
    const payload = {
      ...form,
      pages: Number(pages),
      acceptedQuote: quote.coins,
    };
    try {
      const operation = await pendingOperation("/generation/jobs", payload);
      const job = await api("/generation/jobs", {
        method: "POST",
        body: JSON.stringify({ ...payload, requestKey: operation.requestKey }),
      });
      setSelectedId(job.id);
      await refresh();
      await onUpdate();
      operation.complete();
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  async function action(name) {
    setBusy(true);
    setError("");
    try {
      await api("/generation/jobs/" + selected.id + "/" + name, {
        method: "POST",
        body: "{}",
      });
      await refresh();
      await onUpdate();
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  async function approve() {
    setBusy(true);
    setError("");
    try {
      await api("/generation/jobs/" + selected.id + "/plan", {
        method: "PUT",
        body: JSON.stringify({
          headings: plan
            .split("\n")
            .map((x) => x.trim())
            .filter(Boolean),
          version: selected.version,
        }),
      });
      await refresh();
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <section className="editor generation-panel">
      <h2>Ստեղծել AI աշխատանք</h2>
      {error && (
        <p role="alert" className="form-error">
          {error}
        </p>
      )}
      {config?.mock && (
        <p className="notice">
          ԹԵՍՏԱՅԻՆ ՌԵԺԻՄ․ ստեղծվող տեքստն իրական AI արդյունք չէ։
        </p>
      )}
      {config && !config.available && (
        <p className="notice">
          AI ծառայությունը դեռ միացված չէ։ Գեներացիան հասանելի կլինի backend-ում
          API բանալին կարգավորելուց հետո։
        </p>
      )}
      <form onSubmit={start}>
        <label>
          Թեմա
          <input
            name="topic"
            required
            maxLength={200}
            placeholder="Օրինակ՝ թվային կրթության զարգացումը Հայաստանում"
          />
        </label>
        <div className="generation-fields">
          <label>
            Աշխատանքի տեսակ
            <select name="kind">
              <option value="COURSEWORK">Կուրսային աշխատանք</option>
              <option value="THESIS">Դիպլոմային աշխատանք</option>
              <option value="ESSAY">Ռեֆերատ</option>
              <option value="REPORT">Զեկույց</option>
            </select>
          </label>
          <label>
            Լեզու
            <select name="language">
              <option value="hy">Հայերեն</option>
              <option value="ru">Русский</option>
              <option value="en">English</option>
            </select>
          </label>
          <label>
            Մոտավոր էջերի քանակ
            <input
              type="number"
              min={1}
              max={40}
              value={pages}
              onChange={(e) => setPages(Number(e.target.value))}
              required
            />
          </label>
          <label>
            Առարկա
            <input name="subject" maxLength={200} defaultValue="" />
          </label>
          <label>
            Համալսարան
            <input name="university" maxLength={200} defaultValue="" />
          </label>
        </div>
        <label>
          Պահանջներ և լրացուցիչ ցանկություններ
          <textarea
            name="requirements"
            maxLength={5000}
            rows={4}
            defaultValue=""
          />
        </label>
        <p>
          Սկզբում կստանաք խմբագրվող պլան։ Այն հաստատելուց հետո կստեղծվեն
          բաժինները։ Ծավալը մոտավոր է՝ մեկ հաշվարկային էջը 350 բառ։
        </p>
        {quote ? (
          <p>
            <strong>Ամրագրման առավելագույն չափը՝ {quote.coins} Coins</strong>
            <br />
            Ավարտին գանձվում է փաստացի ծավալի արժեքը՝ այս սահմանում։ Սխալի կամ
            չեղարկման դեպքում ամրագրումը վերադարձվում է։
          </p>
        ) : (
          <p role="status">{quoteError || "Արժեքը հաշվարկվում է…"}</p>
        )}
        <button disabled={busy || !quote || !config?.available}>
          Ամրագրել Coins և ստեղծել պլանը
        </button>
      </form>
      <p className="small-note">
        Աղբյուրների արտաքին որոնումը դեռ միացված չէ։ Ստուգեք փաստերն ու
        հղումները՝ աշխատանքն օգտագործելուց առաջ։
      </p>
      <hr />
      <h3>Իմ գեներացիաները</h3>
      {jobs.length === 0 ? (
        <p>Գեներացիաներ դեռ չկան։</p>
      ) : (
        <>
          <label>
            Աշխատանք
            <select
              value={selected?.id || ""}
              onChange={(e) => setSelectedId(e.target.value)}
            >
              {jobs.map((j) => (
                <option key={j.id} value={j.id}>
                  {j.topic} — {statuses[j.status]}
                </option>
              ))}
            </select>
          </label>
          {selected && (
            <div className="generation-status">
              <h3>{selected.topic}</h3>
              <p role="status">
                {statuses[selected.status]}{" "}
                {selected.totalSections > 0 &&
                  `· ${selected.completedSections}/${selected.totalSections}`}
              </p>
              {selected.provider === "MOCK" && (
                <p className="notice">Թեստային գեներացիա</p>
              )}
              {selected.totalSections > 0 && (
                <progress
                  value={selected.completedSections}
                  max={selected.totalSections}
                  aria-label="Գեներացիայի առաջընթաց"
                />
              )}
              <p>
                Ամրագրված սահման՝ {selected.quotedCoins} Coins · Գանձված՝{" "}
                {selected.chargedCoins} Coins
              </p>
              {selected.status === "AWAITING_PLAN" && (
                <>
                  <label>
                    Պլան՝ յուրաքանչյուր վերնագիրը նոր տողում (3–12 բաժին)
                    <textarea
                      rows={8}
                      value={plan}
                      onChange={(e) => setPlan(e.target.value)}
                    />
                  </label>
                  <button disabled={busy} onClick={approve}>
                    Հաստատել պլանը և գեներացնել
                  </button>
                </>
              )}
              {selected.status === "FAILED" && (
                <p role="alert">
                  {errors[selected.errorCode] ||
                    "Գեներացիան չհաջողվեց։ Ամրագրումը վերադարձվել է։"}{" "}
                  Կարող եք կրկնել փորձը՝ նորից ամրագրելով {selected.quotedCoins}{" "}
                  Coins։
                </p>
              )}
              <div className="row generation-actions">
                {["QUEUED", "RUNNING"].includes(selected.status) && (
                  <button
                    className="outline"
                    disabled={busy}
                    onClick={() => action("pause")}
                  >
                    Դադարեցնել
                  </button>
                )}
                {selected.status === "PAUSED" && (
                  <button disabled={busy} onClick={() => action("resume")}>
                    Շարունակել
                  </button>
                )}
                {selected.status === "FAILED" && (
                  <button disabled={busy} onClick={() => action("retry")}>
                    Կրկնել փորձը
                  </button>
                )}
                {!["COMPLETED", "CANCELLED"].includes(selected.status) && (
                  <button
                    className="outline"
                    disabled={busy}
                    onClick={() => action("cancel")}
                  >
                    Չեղարկել և վերադարձնել ամրագրումը
                  </button>
                )}
                {selected.status === "COMPLETED" && selected.documentId && (
                  <button onClick={() => onOpen(selected.documentId)}>
                    Բացել խմբագրիչում
                  </button>
                )}
              </div>
            </div>
          )}
        </>
      )}
    </section>
  );
}
