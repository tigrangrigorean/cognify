"use client";
import { useEffect, useState } from "react";
import { pendingOperation } from "../services/pending-operation.mjs";
import { api } from "../services/api";
const operationNames = { DOCUMENT_GENERATION: "Փաստաթղթի գեներացիա" };
const labels = {
  ADJUSTMENT: "Ձեռքով ճշգրտում",
  TEST_PAYMENT: "Թեստային համալրում",
  PAYMENT: "Համալրում",
  RESERVE: "Ամրագրում",
  SETTLE: "Ծախս",
  RELEASE: "Ամրագրման վերադարձ",
  REFUND: "Վերադարձ",
};
export default function WalletPanel({ onUpdate }) {
  const [balance, setBalance] = useState(null),
    [entries, setEntries] = useState([]),
    [packages, setPackages] = useState([]),
    [mode, setMode] = useState(null),
    [payments, setPayments] = useState([]),
    [reservations, setReservations] = useState([]),
    [page, setPage] = useState(0),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false);
  async function load(p = 0) {
    const [b, t, c, m, pay, r] = await Promise.all([
      api("/wallet"),
      api("/wallet/transactions?page=" + p),
      api("/public/coin-packages"),
      api("/public/payment-mode"),
      api("/payments"),
      api("/wallet/reservations"),
    ]);
    setBalance(b);
    setEntries(t);
    setPackages(c);
    setMode(m);
    setPayments(pay);
    setReservations(r);
    setPage(p);
  }
  useEffect(() => {
    load().catch((e) => setError(e.message));
  }, []);
  async function buy(pack) {
    if (
      !confirm("Սա թեստային համալրում է։ Իրական գումար չի գանձվի։ Շարունակե՞լ։")
    )
      return;
    setBusy(true);
    setError("");
    try {
      const operation = await pendingOperation("/payments", {
        packageId: pack.id,
      });
      const requestKey = operation.requestKey;
      await api("/payments", {
        method: "POST",
        body: JSON.stringify({ packageId: pack.id, requestKey }),
      });
      await load();
      await onUpdate();
      operation.complete();
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  async function changePage(p) {
    setBusy(true);
    setError("");
    try {
      await load(p);
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <section className="editor">
      <h2>Դրամապանակ</h2>
      {error && (
        <p className="form-error" role="alert">
          {error}
        </p>
      )}
      {!balance ? (
        <p role="status">Բեռնվում է…</p>
      ) : (
        <>
          <div className="wallet-summary">
            <p>
              Հասանելի<strong>{balance.available} Coins</strong>
            </p>
            <p>
              Ամրագրված<strong>{balance.reserved} Coins</strong>
            </p>
            <p>
              Ընդհանուր<strong>{balance.balance} Coins</strong>
            </p>
          </div>
          <h3>Համալրել Coins-ը</h3>
          {mode?.test ? (
            <p className="notice">
              Թեստային ռեժիմ․ իրական վճարում չի կատարվում։
            </p>
          ) : (
            <p>Վճարումները դեռ միացված չեն։ Իրական գումար չի գանձվում։</p>
          )}
          {packages.length === 0 && <p>Ակտիվ փաթեթներ դեռ չկան։</p>}
          <div className="coin-packages">
            {packages.map((p) => (
              <article key={p.id}>
                <h3>{p.name}</h3>
                <strong>{p.coins} Coins</strong>
                <p>
                  {new Intl.NumberFormat("hy-AM", {
                    style: "currency",
                    currency: p.currency,
                  }).format(p.amountMinor / 100)}
                </p>
                {mode?.available && mode?.test && (
                  <button disabled={busy} onClick={() => buy(p)}>
                    Թեստային համալրում
                  </button>
                )}
              </article>
            ))}
          </div>
          <h3>Գործարքների պատմություն</h3>
          {entries.length === 0 ? (
            <p>Գործարքներ դեռ չկան։</p>
          ) : (
            <div className="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>Գործողություն</th>
                    <th>Coins</th>
                    <th>Ամրագրում</th>
                    <th>Ամսաթիվ</th>
                  </tr>
                </thead>
                <tbody>
                  {entries.map((t) => (
                    <tr key={t.id}>
                      <td>
                        {labels[t.kind] || t.kind}
                        <small>
                          {["RESERVE", "SETTLE", "RELEASE"].includes(t.kind)
                            ? operationNames[t.reason] || "Գործողություն"
                            : t.reason}
                        </small>
                      </td>
                      <td>
                        {t.amount > 0 ? "+" : ""}
                        {t.amount}
                      </td>
                      <td>
                        {t.reservedDelta > 0 ? "+" : ""}
                        {t.reservedDelta}
                      </td>
                      <td>{new Date(t.createdAt).toLocaleString("hy-AM")}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          <div className="row">
            <button
              className="outline"
              disabled={busy || page === 0}
              onClick={() => changePage(page - 1)}
            >
              Նախորդը
            </button>
            <span>{page + 1}</span>
            <button
              className="outline"
              disabled={busy || entries.length < 30}
              onClick={() => changePage(page + 1)}
            >
              Հաջորդը
            </button>
          </div>
          <h3>Վերջին ամրագրումները</h3>
          {reservations.length === 0 ? (
            <p>Ամրագրումներ չկան։</p>
          ) : (
            reservations.map((r) => (
              <p key={r.id}>
                {operationNames[r.operation] || "Գործողություն"} · {r.amount}{" "}
                Coins ·{" "}
                {r.status === "HELD"
                  ? "Ամրագրված"
                  : r.status === "RELEASED"
                    ? "Վերադարձված"
                    : "Ավարտված"}
              </p>
            ))
          )}
          <h3>Վերջին համալրումները</h3>
          {payments.length === 0 ? (
            <p>Համալրումներ չկան։</p>
          ) : (
            payments.map((p) => (
              <p key={p.id}>
                {p.coins} Coins · {p.test ? "Թեստային" : "Վճարում"} ·{" "}
                {p.status === "SUCCEEDED" ? "Ավարտված" : "Սպասման մեջ"}
              </p>
            ))
          )}
        </>
      )}
    </section>
  );
}
