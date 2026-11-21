"use client";
import { useEffect, useState } from "react";
import { api, downloadBlob } from "../services/api";
const kinds = {
  COURSEWORK: "Կուրսային աշխատանք",
  THESIS: "Դիպլոմային աշխատանք",
  ESSAY: "Ռեֆերատ",
  REPORT: "Զեկույց",
};
export default function DocumentEditor({ document: initial, onSaved }) {
  const [doc, setDoc] = useState(initial),
    [title, setTitle] = useState(initial.title),
    [kind, setKind] = useState(initial.kind),
    [content, setContent] = useState(initial.content),
    [sections, setSections] = useState([]),
    [selected, setSelected] = useState(null),
    [sectionTitle, setSectionTitle] = useState(""),
    [sectionBody, setSectionBody] = useState(""),
    [versions, setVersions] = useState(null),
    [busy, setBusy] = useState(false),
    [error, setError] = useState(""),
    [notice, setNotice] = useState("");
  const original = sections.find((s) => s.id === selected);
  const dirty =
    original &&
    (original.title !== sectionTitle || original.content !== sectionBody);
  function choose(s) {
    if (dirty && !confirm("Չպահպանված փոփոխությունները կկորչեն։ Շարունակե՞լ։"))
      return;
    setSelected(s.id);
    setSectionTitle(s.title);
    setSectionBody(s.content);
  }
  async function load(id, pick) {
    const all = await api("/documents/" + id + "/sections");
    setSections(all);
    const s = all.find((s) => s.id === pick) || all[0];
    setSelected(s?.id || null);
    setSectionTitle(s?.title || "");
    setSectionBody(s?.content || "");
  }
  useEffect(() => {
    setDoc(initial);
    setTitle(initial.title);
    setKind(initial.kind);
    setContent(initial.content);
    setVersions(null);
    if (initial.id) load(initial.id, null).catch((e) => setError(e.message));
    else setSections([]);
  }, [initial.id]);
  async function accept(d) {
    setDoc(d);
    setContent(d.content);
    await load(d.id, selected);
    await onSaved(d);
    setVersions(null);
    setNotice("Փաստաթուղթը պահպանված է։");
  }
  async function run(task) {
    setBusy(true);
    setError("");
    setNotice("");
    try {
      await task();
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  async function saveMetadata() {
    if (
      dirty &&
      !confirm("Չպահպանված բաժնի փոփոխությունները կկորչեն։ Շարունակե՞լ։")
    )
      return;
    await run(async () => {
      const d = await api("/documents" + (doc.id ? "/" + doc.id : ""), {
        method: doc.id ? "PUT" : "POST",
        body: JSON.stringify({
          title,
          kind,
          content: sections.length ? doc.content : content,
          version: doc.version,
        }),
      });
      await accept(d);
    });
  }
  async function saveSection() {
    await run(async () => {
      const d = await api("/documents/" + doc.id + "/sections/" + selected, {
        method: "PUT",
        body: JSON.stringify({
          title: sectionTitle,
          content: sectionBody,
          version: doc.version,
        }),
      });
      await accept(d);
    });
  }
  async function add() {
    if (
      (dirty || (!sections.length && content !== doc.content)) &&
      !confirm("Չպահպանված փոփոխությունները կկորչեն։ Շարունակե՞լ։")
    )
      return;
    await run(async () => {
      const d = await api("/documents/" + doc.id + "/sections", {
        method: "POST",
        body: JSON.stringify({
          title: "Նոր բաժին",
          content: "",
          version: doc.version,
        }),
      });
      await accept(d);
    });
  }
  async function remove() {
    if (!confirm("Ջնջե՞լ այս բաժինը։")) return;
    await run(async () => {
      const d = await api(
        "/documents/" +
          doc.id +
          "/sections/" +
          selected +
          "?version=" +
          doc.version,
        { method: "DELETE" },
      );
      await accept(d);
    });
  }
  async function move(direction) {
    if (dirty && !confirm("Չպահպանված փոփոխությունները կկորչեն։ Շարունակե՞լ։"))
      return;
    const ids = sections.map((s) => s.id);
    const index = ids.indexOf(selected),
      next = index + direction;
    if (next < 0 || next >= ids.length) return;
    [ids[index], ids[next]] = [ids[next], ids[index]];
    await run(async () => {
      const d = await api("/documents/" + doc.id + "/section-order", {
        method: "PUT",
        body: JSON.stringify({ ids, version: doc.version }),
      });
      await accept(d);
    });
  }
  async function exportDoc() {
    if (
      (dirty ||
        title !== doc.title ||
        kind !== doc.kind ||
        (!sections.length && content !== doc.content)) &&
      !confirm("Կարտահանվի վերջին պահպանված տարբերակը։ Շարունակե՞լ։")
    )
      return;
    await run(async () => {
      const blob = await downloadBlob("/documents/" + doc.id + "/export.docx");
      const url = URL.createObjectURL(blob);
      const a = window.document.createElement("a");
      a.href = url;
      a.download = "cognify-" + doc.id + ".docx";
      a.click();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    });
  }
  return (
    <section className="editor">
      <div className="row">
        <h2>Փաստաթղթի խմբագրիչ</h2>
        <button disabled={busy || !doc.id} onClick={exportDoc}>
          Ներբեռնել DOCX
        </button>
      </div>
      {doc.generationMode === "MOCK" && (
        <p className="notice">
          ԹԵՍՏԱՅԻՆ ՓԱՍՏԱԹՈՒՂԹ․ ոչ իրական AI արդյունք։ Նշումը պահպանվում է նաև
          DOCX-ում։
        </p>
      )}
      {error && (
        <p className="form-error" role="alert">
          {error}
        </p>
      )}
      {notice && <p role="status">{notice}</p>}
      <label>
        Վերնագիր
        <input
          value={title}
          maxLength={200}
          onChange={(e) => setTitle(e.target.value)}
        />
      </label>
      <label>
        Աշխատանքի տեսակ
        <select value={kind} onChange={(e) => setKind(e.target.value)}>
          {Object.entries(kinds).map(([k, v]) => (
            <option key={k} value={k}>
              {v}
            </option>
          ))}
        </select>
      </label>
      {sections.length === 0 && (
        <label>
          Բովանդակություն
          <textarea
            rows={18}
            maxLength={200000}
            value={content}
            onChange={(e) => setContent(e.target.value)}
          />
        </label>
      )}
      <button disabled={busy || !title.trim()} onClick={saveMetadata}>
        Պահպանել {sections.length ? "վերնագիրն ու տեսակը" : "փաստաթուղթը"}
      </button>
      {sections.length > 0 && (
        <div className="section-editor">
          <label>
            Բաժին
            <select
              value={selected || ""}
              onChange={(e) =>
                choose(sections.find((s) => s.id === e.target.value))
              }
            >
              {sections.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.position + 1}. {s.title}
                </option>
              ))}
            </select>
          </label>
          <label>
            Բաժնի վերնագիր
            <input
              value={sectionTitle}
              maxLength={200}
              onChange={(e) => setSectionTitle(e.target.value)}
            />
          </label>
          <label>
            Բաժնի տեքստ
            <textarea
              rows={18}
              maxLength={16000}
              value={sectionBody}
              onChange={(e) => setSectionBody(e.target.value)}
            />
          </label>
          <div className="generation-actions">
            <button
              disabled={busy || !sectionTitle.trim()}
              onClick={saveSection}
            >
              Պահպանել բաժինը
            </button>
            <button
              className="outline"
              disabled={busy || sections[0]?.id === selected}
              onClick={() => move(-1)}
            >
              Տեղափոխել վերև
            </button>
            <button
              className="outline"
              disabled={busy || sections.at(-1)?.id === selected}
              onClick={() => move(1)}
            >
              Տեղափոխել ներքև
            </button>
            <button className="outline" disabled={busy} onClick={remove}>
              Ջնջել բաժինը
            </button>
          </div>
        </div>
      )}
      {doc.id && (
        <div className="generation-actions">
          <button className="outline" disabled={busy} onClick={add}>
            Ավելացնել բաժին
          </button>
          <button
            className="outline"
            disabled={busy}
            onClick={() =>
              run(async () =>
                setVersions(await api("/documents/" + doc.id + "/versions")),
              )
            }
          >
            Տարբերակների պատմություն
          </button>
        </div>
      )}
      {versions && (
        <div>
          <h3>Վերջին պահպանված տարբերակները</h3>
          {versions.length === 0 ? (
            <p>Նախորդ տարբերակներ դեռ չկան։</p>
          ) : (
            versions.map((v) => (
              <details key={v.id}>
                <summary>
                  Տարբերակ {v.revision} ·{" "}
                  {new Date(v.createdAt).toLocaleString("hy-AM")}
                </summary>
                <h3>{v.title}</h3>
                <textarea readOnly rows={10} value={v.content} />
              </details>
            ))
          )}
        </div>
      )}
      <p className="small-note">
        DOCX-ը խմբագրվող է։ Այժմ օգտագործվում են A4, Sylfaen և հիմնական
        ակադեմիական ձևավորում։ PDF արտահանումը դեռ հասանելի չէ։
      </p>
    </section>
  );
}
