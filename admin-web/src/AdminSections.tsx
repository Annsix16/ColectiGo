import { useEffect, useMemo, useState } from "react";
import type { FormEvent } from "react";
import {
  sendAdminMessage,
  subscribeAdminMessages,
  type AdminMessage,
  type MessageAudience,
} from "./messages";
import type { Trip } from "./trips";

type DirectoryKind = "conductores" | "pasajeros";

const audienceLabels: Record<Exclude<MessageAudience, "ADMIN">, string> = {
  TODOS: "Conductores y pasajeros",
  CONDUCTOR: "Conductores",
  PASAJERO: "Pasajeros",
};

function messageAudienceLabel(audience: MessageAudience) {
  if (audience === "ADMIN") return "Mensaje recibido desde la app";
  return audienceLabels[audience];
}

function messageDate(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? "Ahora"
    : new Intl.DateTimeFormat("es-CL", {
        dateStyle: "medium",
        timeStyle: "short",
      }).format(date);
}

function initials(name: string) {
  return name
    .split(" ")
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0])
    .join("")
    .toUpperCase();
}

export function AdminMessages({
  demoMode,
}: {
  demoMode: boolean;
}) {
  const [messages, setMessages] = useState<AdminMessage[]>([]);
  const [audience, setAudience] =
    useState<Exclude<MessageAudience, "ADMIN">>("TODOS");
  const [content, setContent] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [sending, setSending] = useState(false);

  useEffect(() => {
    return subscribeAdminMessages(
      (nextMessages) => {
        setMessages(nextMessages);
        setLoading(false);
        setError("");
      },
      (nextError) => {
        setError(nextError.message);
        setLoading(false);
      },
    );
  }, []);

  const receivedMessages = messages.filter(
    (message) => message.audience === "ADMIN",
  );
  const announcements = messages.filter(
    (message) => message.audience !== "ADMIN",
  );

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSending(true);
    setError("");
    try {
      await sendAdminMessage(audience, content);
      setContent("");
    } catch (sendError) {
      setError(
        sendError instanceof Error
          ? sendError.message
          : "No se pudo enviar el mensaje.",
      );
    } finally {
      setSending(false);
    }
  }

  return (
    <>
      <section className="page-heading">
        <div>
          <p className="eyebrow">COMUNICACIÓN</p>
          <h1>Mensajes</h1>
          <p className="subtitle">
            Envía avisos a los grupos de la app y recibe sus mensajes.
          </p>
        </div>
        <span className="live-indicator"><i /> {demoMode ? "Vista previa" : "En vivo"}</span>
      </section>
      {demoMode && (
        <div className="demo-banner">
          <span className="demo-banner-icon">✦</span>
          <div>
            <strong>Modo demostración</strong>
            <span>Los mensajes se guardan en este navegador; no se sincronizan con el teléfono.</span>
          </div>
          <span className="demo-tag">DEMO</span>
        </div>
      )}
      {error && <div className="error-banner" role="alert">{error}</div>}

      <div className="messaging-layout">
        <section className="table-card compose-card">
          <div className="section-card-heading">
            <span className="compose-icon">✉</span>
            <div><h2>Nuevo mensaje</h2><p>El aviso aparecerá en la pestaña Mensajes del grupo.</p></div>
          </div>
          <form onSubmit={(event) => void handleSubmit(event)} className="compose-form">
            <label>
              Enviar a
              <select value={audience} onChange={(event) => setAudience(event.target.value as Exclude<MessageAudience, "ADMIN">)}>
                <option value="TODOS">Conductores y pasajeros</option>
                <option value="CONDUCTOR">Solo conductores</option>
                <option value="PASAJERO">Solo pasajeros</option>
              </select>
            </label>
            <label>
              Mensaje
              <textarea
                value={content}
                onChange={(event) => setContent(event.target.value)}
                maxLength={500}
                required
                placeholder="Escribe un aviso para la comunidad…"
                rows={5}
              />
            </label>
            <div className="compose-footer">
              <span>{content.length}/500 caracteres</span>
              <button className="primary-button" disabled={sending || !content.trim()}>
                {sending ? "Enviando…" : "Enviar mensaje"}
              </button>
            </div>
          </form>
          <div className="channel-note"><span>ⓘ</span> Los mensajes enviados por la app también aparecen en la bandeja de administración.</div>
        </section>

        <section className="table-card inbox-card">
          <div className="table-heading">
            <div><h2>Bandeja de la comunidad</h2><p>{receivedMessages.length} mensaje{receivedMessages.length === 1 ? "" : "s"} recibido{receivedMessages.length === 1 ? "" : "s"} desde la app</p></div>
            <span className="inbox-count">{receivedMessages.length.toString().padStart(2, "0")}</span>
          </div>
          <div className="message-list">
            {loading ? (
              <div className="inbox-empty">Cargando mensajes…</div>
            ) : receivedMessages.length === 0 ? (
              <div className="inbox-empty"><span>✉</span><strong>La bandeja está vacía</strong><small>Los mensajes que envíen desde la app aparecerán aquí.</small></div>
            ) : receivedMessages.map((message) => (
              <article className="inbox-message" key={message.id}>
                <span className={`message-avatar ${message.senderRole.toLowerCase()}`}>{initials(message.senderName)}</span>
                <div className="inbox-message-body">
                  <div className="inbox-message-top"><strong>{message.senderName}</strong><time>{messageDate(message.createdAt)}</time></div>
                  <span className="audience-chip">{message.senderRole === "CONDUCTOR" ? "Conductor" : "Pasajero"}</span>
                  <p>{message.content}</p>
                </div>
              </article>
            ))}
          </div>
        </section>
      </div>

      <section className="table-card announcement-card">
        <div className="table-heading">
          <div><h2>Avisos enviados</h2><p>Historial de anuncios transmitidos a los grupos.</p></div>
          <span className="inbox-count">{announcements.length.toString().padStart(2, "0")}</span>
        </div>
        <div className="announcement-list">
          {announcements.length === 0 ? (
            <div className="inbox-empty compact">Todavía no se han enviado avisos.</div>
          ) : announcements.map((message) => (
            <article key={message.id} className="announcement-item">
              <span className="announcement-icon">↗</span>
              <div><strong>{message.content}</strong><p>{messageAudienceLabel(message.audience)} <span>·</span> {messageDate(message.createdAt)}</p></div>
            </article>
          ))}
        </div>
      </section>
    </>
  );
}

interface DirectoryEntry {
  name: string;
  trips: number;
  lastTrip: Trip;
  status: string;
  detail: string;
}

export function AdminDirectory({
  kind,
  trips,
  demoMode,
}: {
  kind: DirectoryKind;
  trips: Trip[];
  demoMode: boolean;
}) {
  const [search, setSearch] = useState("");
  const isDriver = kind === "conductores";
  const title = isDriver ? "Conductores" : "Pasajeros";
  const entries = useMemo(() => {
    const grouped = new Map<string, Trip[]>();
    for (const trip of trips) {
      const name = isDriver ? trip.driverName : trip.passengerName;
      if (!name || name === "Por asignar") continue;
      grouped.set(name, [...(grouped.get(name) ?? []), trip]);
    }
    return [...grouped.entries()]
      .map(([name, personTrips]): DirectoryEntry => {
        const latest = [...personTrips].sort((a, b) =>
          b.createdAt.localeCompare(a.createdAt),
        )[0];
        return {
          name,
          trips: personTrips.length,
          lastTrip: latest,
          status: latest.status,
          detail: isDriver
            ? latest.patente || "Patente no registrada"
            : latest.passengerUid
              ? `ID ${latest.passengerUid.slice(0, 8)}…`
              : "Cuenta de pasajero",
        };
      })
      .sort((a, b) => a.name.localeCompare(b.name, "es"));
  }, [isDriver, trips]);
  const filtered = entries.filter((entry) =>
    `${entry.name} ${entry.detail}`.toLocaleLowerCase("es").includes(search.toLocaleLowerCase("es")),
  );

  return (
    <>
      <section className="page-heading">
        <div>
          <p className="eyebrow">COMUNIDAD</p>
          <h1>{title}</h1>
          <p className="subtitle">
            Contactos identificados a partir de las reservas compartidas.
          </p>
        </div>
        <span className="directory-total">{entries.length} registrados</span>
      </section>
      {demoMode && (
        <div className="demo-banner">
          <span className="demo-banner-icon">✦</span>
          <div><strong>Directorio de demostración</strong><span>Se completa automáticamente a medida que se registran reservas en Firestore.</span></div>
          <span className="demo-tag">DEMO</span>
        </div>
      )}
      <div className="directory-info"><span>ⓘ</span><p>La app actual no tiene una colección compartida de perfiles. Esta vista muestra participantes de reservas; la administración de cuentas y perfiles requiere integrar esos datos en Firebase.</p></div>
      <section className="table-card directory-card">
        <div className="table-heading">
          <div><h2>Directorio de {title.toLocaleLowerCase("es")}</h2><p>{entries.length} persona{entries.length === 1 ? "" : "s"} detectada{entries.length === 1 ? "" : "s"} en los viajes</p></div>
          <label className="search-box"><span>⌕</span><input value={search} onChange={(event) => setSearch(event.target.value)} placeholder={`Buscar ${title.toLocaleLowerCase("es")}…`} aria-label={`Buscar ${title.toLocaleLowerCase("es")}`} /></label>
        </div>
        {filtered.length === 0 ? (
          <div className="inbox-empty"><span>◎</span><strong>{entries.length ? "No hay coincidencias" : "Aún no hay personas registradas"}</strong><small>{entries.length ? "Prueba con otro nombre." : "Envía una reserva desde la app para que aparezcan aquí."}</small></div>
        ) : (
          <div className="directory-list">
            {filtered.map((entry) => (
              <article className="directory-person" key={entry.name}>
                <span className="person-avatar">{initials(entry.name)}</span>
                <div className="directory-person-name"><strong>{entry.name}</strong><small>{entry.detail}</small></div>
                <span className={`status-select status-${entry.status.toLocaleLowerCase("es").replaceAll(" ", "-")}`}>{entry.status}</span>
                <div className="directory-last"><strong>{entry.trips} viaje{entry.trips === 1 ? "" : "s"}</strong><small>Último: {entry.lastTrip.destination}</small></div>
              </article>
            ))}
          </div>
        )}
      </section>
    </>
  );
}
