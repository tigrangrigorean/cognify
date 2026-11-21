"use client";
import { useEffect, useState } from "react";
import { pendingOperation } from "../services/pending-operation.mjs";
import { api, resumeSession } from "../services/api";
const units = {
  OPERATION: "Գործողություն",
  WORD: "Բառ",
  PAGE: "Էջ",
  CHAPTER: "Գլուխ",
  ANALYSIS: "Վերլուծություն",
  EXPORT: "Արտահանում",
};
export default function AdminFinance() {
  const [access, setAccess] = useState(false),
    [loading, setLoading] = useState(true),
    [packages, setPackages] = useState([]),
    [rules, setRules] = useState([]),
    [user, setUser] = useState(null),
    [reservations, setReservations] = useState([]),
    [error, setError] = useState(""),
    [notice, setNotice] = useState(""),
    [busy, setBusy] = useState(false);
  async function catalog() {
    const [p, r] = await Promise.all([
      api("/admin/coin-packages"),
      api("/admin/pricing"),
    ]);
    setPackages(p);
    setRules(r);
  }
  useEffect(() => {
    (async () => {
      try {
        await resumeSession();
        const me = await api("/me");
        if (me.role !== "ADMIN") throw Error("Մուտքը սահմանափակված է։");
        setAccess(true);
        await catalog();
      } catch (e) {
        setError(e.message);
      } finally {
        setLoading(false);
      }
    })();
  }, []);
  async function run(task) {
    setBusy(true);
    setError("");
    setNotice("");
    try {
      await task();
      setNotice("Պահպանված է։");
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  async function lookup(e) {
    e.preventDefault();
    const email = new FormData(e.currentTarget).get("email");
    setUser(null);
    setReservations([]);
    await run(async () => {
      const u = await api(
        "/admin/wallets/lookup?email=" + encodeURIComponent(email),
      );
      setUser(u);
      setReservations(await api("/admin/wallets/" + u.id + "/reservations"));
    });
  }
  async function refreshUser() {
    const wallet = await api("/admin/wallets/" + user.id);
    setUser((u) => ({ ...u, wallet }));
    setReservations(await api("/admin/wallets/" + user.id + "/reservations"));
  }
  async function adjust(e) {
    e.preventDefault();
    const data = Object.fromEntries(new FormData(e.currentTarget));
    const action = "/admin/wallets/" + user.id + "/adjustments";
    await run(async () => {
      const operation = await pendingOperation(action, data);
      const requestKey = operation.requestKey;
      await api(action, {
        method: "POST",
        body: JSON.stringify({
          amount: Number(data.amount),
          reason: data.reason,
          requestKey,
        }),
      });
      await refreshUser();
      operation.complete();
    });
  }
  async function refund(e, id) {
    e.preventDefault();
    const data = Object.fromEntries(new FormData(e.currentTarget));
    const action =
      "/admin/wallets/" + user.id + "/reservations/" + id + "/refunds";
    await run(async () => {
      const operation = await pendingOperation(action, data);
      const requestKey = operation.requestKey;
      await api(action, {
        method: "POST",
        body: JSON.stringify({
          amount: Number(data.amount),
          reason: data.reason,
          requestKey,
        }),
      });
      await refreshUser();
      operation.complete();
    });
  }
  async function savePackage(e, p) {
    e.preventDefault();
    const d = Object.fromEntries(new FormData(e.currentTarget));
    const body = {
      name: d.name,
      coins: Number(d.coins),
      amountMinor: Number(d.amountMinor),
      currency: "AMD",
      active: d.active === "on",
      version: p?.version || 0,
    };
    await run(async () => {
      await api("/admin/coin-packages" + (p ? "/" + p.id : ""), {
        method: p ? "PUT" : "POST",
        body: JSON.stringify(body),
      });
      await catalog();
    });
  }
  async function saveRule(e, r) {
    e.preventDefault();
    const d = Object.fromEntries(new FormData(e.currentTarget));
    await run(async () => {
      await api("/admin/pricing/" + encodeURIComponent(d.operation), {
        method: "PUT",
        body: JSON.stringify({
          unit: d.unit,
          unitCost: Number(d.unitCost),
          active: d.active === "on",
          version: r?.version || 0,
        }),
      });
      await catalog();
    });
  }
  function packForm(p) {
    return (
      <form
        key={p ? `${p.id}:${p.version}` : "new"}
        className="editor"
        onSubmit={(e) => savePackage(e, p)}
      >
        <h3>{p ? "Փաթեթ" : "Նոր փաթեթ"}</h3>
        <label>
          Անվանում
          <input
            name="name"
            required
            maxLength={100}
            defaultValue={p?.name || ""}
          />
        </label>
        <label>
          Coins
          <input
            name="coins"
            type="number"
            min={1}
            max={1000000000}
            required
            defaultValue={p?.coins || 100}
          />
        </label>
        <label>
          Գին՝ լումաներով (100 = 1 դրամ)
          <input
            name="amountMinor"
            type="number"
            min={1}
            max={100000000000}
            required
            defaultValue={p?.amountMinor || 100000}
          />
        </label>
        <label className="check-label">
          <input
            name="active"
            type="checkbox"
            defaultChecked={p?.active || false}
          />
          Ակտիվ է
        </label>
        <button disabled={busy}>Պահպանել</button>
      </form>
    );
  }
  function ruleForm(r) {
    return (
      <form
        key={r ? `${r.operation}:${r.version}` : "new"}
        className="editor"
        onSubmit={(e) => saveRule(e, r)}
      >
        <h3>{r ? "Սակագին" : "Նոր սակագին"}</h3>
        <label>
          Գործողության կոդ
          <input
            name="operation"
            required
            readOnly={!!r}
            pattern="[A-Z][A-Z0-9_]{1,79}"
            defaultValue={r?.operation || ""}
          />
        </label>
        <label>
          Չափման միավոր
          <select name="unit" defaultValue={r?.unit || "PAGE"}>
            {Object.entries(units).map(([k, v]) => (
              <option key={k} value={k}>
                {v}
              </option>
            ))}
          </select>
        </label>
        <label>
          Coins մեկ միավորի համար
          <input
            name="unitCost"
            type="number"
            min={1}
            max={1000000}
            required
            defaultValue={r?.unitCost || 10}
          />
        </label>
        <label className="check-label">
          <input
            name="active"
            type="checkbox"
            defaultChecked={r?.active || false}
          />
          Ակտիվ է
        </label>
        <button disabled={busy}>Պահպանել</button>
      </form>
    );
  }
  return (
    <main className="section admin-page">
      <a href="/">Գլխավոր էջ</a> ·{" "}
      <a href="/admin">CMS և գործողությունների մատյան</a>
      <h1>Coins-ի կառավարում</h1>
      {error && (
        <p role="alert" className="form-error">
          {error}
        </p>
      )}
      {notice && <p role="status">{notice}</p>}
      {loading ? (
        <p>Բեռնվում է…</p>
      ) : (
        access && (
          <>
            <h2>Օգտատիրոջ դրամապանակ</h2>
            <form onSubmit={lookup}>
              <label>
                Էլ․ փոստ
                <input type="email" name="email" required maxLength={254} />
              </label>
              <button disabled={busy}>Գտնել</button>
            </form>
            {user && (
              <section className="editor">
                <h3>
                  {user.name} · {user.email}
                </h3>
                <p>
                  Հասանելի՝ {user.wallet.available}, ամրագրված՝{" "}
                  {user.wallet.reserved} Coins
                </p>
                <form onSubmit={adjust}>
                  <label>
                    Փոփոխություն (+ կամ − Coins)
                    <input
                      type="number"
                      name="amount"
                      min={-1000000000}
                      max={1000000000}
                      required
                    />
                  </label>
                  <label>
                    Պարտադիր պատճառ
                    <textarea name="reason" maxLength={500} required />
                  </label>
                  <button disabled={busy}>Կատարել ճշգրտում</button>
                </form>
                {reservations
                  .filter(
                    (r) =>
                      r.status === "SETTLED" &&
                      r.settledAmount > r.refundedAmount,
                  )
                  .map((r) => (
                    <form key={r.id} onSubmit={(e) => refund(e, r.id)}>
                      <h3>Վերադարձ՝ {r.operation}</h3>
                      <label>
                        Coins
                        <input
                          type="number"
                          name="amount"
                          required
                          min={1}
                          max={r.settledAmount - r.refundedAmount}
                        />
                      </label>
                      <label>
                        Պատճառ
                        <textarea name="reason" required maxLength={500} />
                      </label>
                      <button disabled={busy}>Վերադարձնել Coins-ը</button>
                    </form>
                  ))}
              </section>
            )}
            <h2>Փաթեթներ</h2>
            <p>Փաթեթի ակտիվացումը չի միացնում բանկային վճարումները։</p>
            <div className="admin-settings">
              {packages.map(packForm)}
              {packForm(null)}
            </div>
            <h2>Սակագներ</h2>
            <div className="admin-settings">
              {rules.map(ruleForm)}
              {ruleForm(null)}
            </div>
          </>
        )
      )}
    </main>
  );
}
