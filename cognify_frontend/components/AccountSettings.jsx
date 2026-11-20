"use client";
import { useEffect, useState } from "react";
import { api } from "../services/api";
export default function AccountSettings({ onSessionEnded }) {
  const [sessions, setSessions] = useState([]),
    [busy, setBusy] = useState(false),
    [error, setError] = useState("");
  async function load() {
    setSessions(await api("/account/sessions"));
  }
  useEffect(() => {
    load().catch((e) => setError(e.message));
  }, []);
  async function revoke(session) {
    setBusy(true);
    setError("");
    try {
      await api("/account/sessions/" + session.id, { method: "DELETE" });
      if (session.current) await onSessionEnded();
      else await load();
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  async function revokeAll() {
    setBusy(true);
    try {
      await api("/account/sessions", { method: "DELETE" });
      await onSessionEnded();
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  async function change(e) {
    e.preventDefault();
    const form = e.currentTarget;
    const data = Object.fromEntries(new FormData(form));
    setError("");
    if (data.newPassword !== data.confirmPassword) {
      setError("Նոր գաղտնաբառերը չեն համընկնում։");
      return;
    }
    setBusy(true);
    try {
      await api("/account/password", {
        method: "POST",
        body: JSON.stringify({
          currentPassword: data.currentPassword,
          newPassword: data.newPassword,
        }),
      });
      form.reset();
      await onSessionEnded();
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <section className="editor">
      <h2>Հաշվի անվտանգություն</h2>
      {error && (
        <p className="form-error" role="alert">
          {error}
        </p>
      )}
      <h3>Ակտիվ մուտքեր</h3>
      <p>
        Մուտքն ավարտելուց հետո այդ սեսիայի հասանելիությունն անմիջապես
        դադարեցվում է։
      </p>
      {sessions.map((s) => (
        <article className="document-row" key={s.id}>
          <div>
            <h3>{s.current ? "Ընթացիկ մուտք" : "Այլ մուտք"}</h3>
            <p>{new Date(s.createdAt).toLocaleString("hy-AM")}</p>
          </div>
          <button className="outline" disabled={busy} onClick={() => revoke(s)}>
            Ավարտել մուտքը
          </button>
        </article>
      ))}
      <button
        className="outline"
        disabled={busy || !sessions.length}
        onClick={revokeAll}
      >
        Ավարտել բոլոր մուտքերը
      </button>
      <hr style={{ margin: "32px 0", borderColor: "#e3e9f3" }} />
      <h3>Փոխել գաղտնաբառը</h3>
      <p>
        Փոփոխությունից հետո անհրաժեշտ կլինի կրկին մուտք գործել բոլոր սարքերում։
      </p>
      <form onSubmit={change} style={{ maxWidth: 480 }}>
        <label>
          Ընթացիկ գաղտնաբառ
          <input
            name="currentPassword"
            type="password"
            required
            maxLength={72}
            autoComplete="current-password"
          />
        </label>
        <label>
          Նոր գաղտնաբառ
          <input
            name="newPassword"
            type="password"
            required
            minLength={10}
            maxLength={72}
            autoComplete="new-password"
          />
        </label>
        <label>
          Կրկնել նոր գաղտնաբառը
          <input
            name="confirmPassword"
            type="password"
            required
            minLength={10}
            maxLength={72}
            autoComplete="new-password"
          />
        </label>
        <button disabled={busy}>Փոխել գաղտնաբառը</button>
      </form>
    </section>
  );
}
