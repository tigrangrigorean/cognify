"use client";
import { useEffect, useState } from "react";
import { api } from "../services/api";
export default function PasswordRecovery({ reset = false }) {
  const [token, setToken] = useState(""),
    [error, setError] = useState(""),
    [message, setMessage] = useState(""),
    [busy, setBusy] = useState(false);
  useEffect(() => {
    if (reset) {
      const incoming = new URLSearchParams(location.hash.slice(1)).get("token");
      if (incoming) setToken(incoming);
      history.replaceState(null, "", location.pathname);
    }
  }, [reset]);
  async function submit(e) {
    e.preventDefault();
    const values = Object.fromEntries(new FormData(e.currentTarget));
    setError("");
    if (reset && values.newPassword !== values.confirmPassword) {
      setError("Գաղտնաբառերը չեն համընկնում։");
      return;
    }
    setBusy(true);
    try {
      await api("/auth/" + (reset ? "reset-password" : "forgot-password"), {
        method: "POST",
        body: JSON.stringify(
          reset
            ? { token, newPassword: values.newPassword }
            : { email: values.email },
        ),
      });
      setMessage(
        reset
          ? "Գաղտնաբառը փոխված է։ Կարող եք կրկին մուտք գործել։"
          : "Եթե այդ էլ․ հասցեով հաշիվ կա, վերականգնման հղումը կուղարկվի։",
      );
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <main style={{ maxWidth: 520, margin: "70px auto", padding: 24 }}>
      <a className="brand" href="/">
        cognify.
      </a>
      <h1 style={{ fontSize: 30 }}>
        {reset ? "Նոր գաղտնաբառ" : "Վերականգնել գաղտնաբառը"}
      </h1>
      <p>
        {reset
          ? "Սահմանեք նոր գաղտնաբառ։ Բոլոր ակտիվ մուտքերը կավարտվեն։"
          : "Մուտքագրեք ձեր հաշվի էլ․ հասցեն։ Հղումը գործում է 30 րոպե։"}
      </p>
      {error && (
        <p className="form-error" role="alert">
          {error}
        </p>
      )}
      {message ? (
        <div role="status">
          <p>{message}</p>
          <a className="button" href="/">
            Վերադառնալ գլխավոր էջ
          </a>
        </div>
      ) : (
        <form onSubmit={submit}>
          {reset ? (
            <>
              <label>
                Նոր գաղտնաբառ
                <input
                  name="newPassword"
                  type="password"
                  autoComplete="new-password"
                  minLength={10}
                  maxLength={72}
                  required
                />
              </label>
              <label>
                Կրկնել գաղտնաբառը
                <input
                  name="confirmPassword"
                  type="password"
                  autoComplete="new-password"
                  minLength={10}
                  maxLength={72}
                  required
                />
              </label>
              {!token && <p role="alert">Բացեք էլ․ փոստով ստացված հղումը։</p>}
            </>
          ) : (
            <label>
              Էլ․ փոստ
              <input
                name="email"
                type="email"
                autoComplete="email"
                maxLength={254}
                required
              />
            </label>
          )}
          <button disabled={busy || (reset && !token)}>
            {busy
              ? "Խնդրում ենք սպասել…"
              : reset
                ? "Պահպանել գաղտնաբառը"
                : "Ուղարկել հղումը"}
          </button>
        </form>
      )}
      <p>
        <a href="/">Գլխավոր էջ</a>
      </p>
    </main>
  );
}
