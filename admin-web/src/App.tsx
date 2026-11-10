import { useEffect, useMemo, useState } from "react";
import type { FormEvent } from "react";
import {
  onAuthStateChanged,
  signInWithEmailAndPassword,
  signOut,
} from "firebase/auth";
import type { User } from "firebase/auth";
import { auth, configError, demoMode } from "./firebase";
import { AdminDirectory, AdminMessages } from "./AdminSections";
import {
  createTrip,
  deleteTrip,
  subscribeTrips,
  tripStatuses,
  updateTrip,
} from "./trips";
import type { Trip, TripInput, TripStatus } from "./trips";

type StatusFilter = "Todos" | TripStatus;
type AdminSection = "viajes" | "conductores" | "pasajeros" | "mensajes";

function formatTime(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? "—"
    : new Intl.DateTimeFormat("es-CL", {
        hour: "2-digit",
        minute: "2-digit",
      }).format(date);
}

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : "Ocurrió un error inesperado.";
}

export default function App() {
  const [user, setUser] = useState<User | null>(null);
  const [authLoading, setAuthLoading] = useState(!demoMode);
  const [trips, setTrips] = useState<Trip[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState<StatusFilter>("Todos");
  const [modalTrip, setModalTrip] = useState<Trip | null | "new">(null);
  const [saving, setSaving] = useState(false);
  const [section, setSection] = useState<AdminSection>("viajes");

  useEffect(() => {
    if (demoMode || !auth) return;
    return onAuthStateChanged(auth, (currentUser) => {
      setUser(currentUser);
      setAuthLoading(false);
    });
  }, []);

  useEffect(() => {
    if (!demoMode && !user) {
      setLoading(false);
      return;
    }
    setLoading(true);
    const unsubscribe = subscribeTrips(
      (nextTrips) => {
        setTrips(nextTrips);
        setLoading(false);
        setError("");
      },
      (nextError) => {
        setError(nextError.message);
        setLoading(false);
      },
    );
    return unsubscribe;
  }, [user]);

  const filteredTrips = useMemo(() => {
    const query = search.trim().toLocaleLowerCase("es");
    return trips
      .filter((trip) => statusFilter === "Todos" || trip.status === statusFilter)
      .filter((trip) =>
        [
          trip.passengerName,
          trip.driverName,
          trip.origin,
          trip.destination,
        ].some((value) => value.toLocaleLowerCase("es").includes(query)),
      )
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  }, [trips, search, statusFilter]);

  const activeTrips = trips.filter(
    (trip) => trip.status === "Aceptado" || trip.status === "En curso",
  ).length;
  const waitingTrips = trips.filter(
    (trip) => trip.status === "Solicitado",
  ).length;
  const completedTrips = trips.filter(
    (trip) => trip.status === "Completado",
  ).length;

  async function handleSave(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!modalTrip) return;
    const formData = new FormData(event.currentTarget);
    const input: TripInput = {
      passengerName: String(formData.get("passengerName")).trim(),
      driverName: String(formData.get("driverName")).trim() || "Por asignar",
      origin: String(formData.get("origin")).trim(),
      destination: String(formData.get("destination")).trim(),
      status: String(formData.get("status")) as TripStatus,
      createdAt:
        modalTrip === "new" ? new Date().toISOString() : modalTrip.createdAt,
      vehicleId: modalTrip === "new" ? 0 : modalTrip.vehicleId,
      patente: modalTrip === "new" ? "" : modalTrip.patente,
      monto: modalTrip === "new" ? 1200 : modalTrip.monto,
      passengerUid: modalTrip === "new" ? "" : modalTrip.passengerUid,
    };
    setSaving(true);
    setError("");
    try {
      if (modalTrip === "new") await createTrip(input);
      else await updateTrip(modalTrip.id, input);
      setModalTrip(null);
    } catch (saveError) {
      setError(errorMessage(saveError));
    } finally {
      setSaving(false);
    }
  }

  async function handleStatusChange(trip: Trip, status: TripStatus) {
    setError("");
    try {
      const { id, ...input } = trip;
      await updateTrip(id, { ...input, status });
    } catch (saveError) {
      setError(errorMessage(saveError));
    }
  }

  async function handleDelete(trip: Trip) {
    if (!window.confirm(`¿Eliminar el viaje de ${trip.passengerName}?`)) return;
    setError("");
    try {
      await deleteTrip(trip.id);
    } catch (deleteError) {
      setError(errorMessage(deleteError));
    }
  }

  if (configError) return <MessageScreen message={configError} />;
  if (authLoading) return <MessageScreen message="Conectando con Firebase…" />;
  if (!demoMode && !user) return <LoginScreen />;

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <a className="brand" href="#" aria-label="ColectiGo inicio">
          <span className="brand-mark">C</span>
          <span>colecti<span className="brand-light">go</span></span>
        </a>
        <div className="workspace-label">GESTIÓN</div>
        <nav className="side-nav" aria-label="Navegación principal">
          <button className={`nav-item ${section === "viajes" ? "active" : ""}`} onClick={() => setSection("viajes")}>
            <span className="nav-icon">↗</span> Viajes
            <span className="nav-count">{trips.length}</span>
          </button>
          <button className={`nav-item ${section === "conductores" ? "active" : ""}`} onClick={() => setSection("conductores")}>
            <span className="nav-icon">◎</span> Conductores
            <span className="nav-count">{new Set(trips.map((trip) => trip.driverName).filter((name) => name && name !== "Por asignar")).size}</span>
          </button>
          <button className={`nav-item ${section === "pasajeros" ? "active" : ""}`} onClick={() => setSection("pasajeros")}>
            <span className="nav-icon">◉</span> Pasajeros
            <span className="nav-count">{new Set(trips.map((trip) => trip.passengerName).filter(Boolean)).size}</span>
          </button>
          <button className={`nav-item ${section === "mensajes" ? "active" : ""}`} onClick={() => setSection("mensajes")}>
            <span className="nav-icon">✉</span> Mensajes
          </button>
        </nav>
        <div className="sidebar-bottom">
          <div className="connection-card">
            <span className={`connection-dot ${demoMode ? "demo" : ""}`} />
            <div>
              <strong>{demoMode ? "Modo demostración" : "Firebase conectado"}</strong>
              <small>{demoMode ? "Datos guardados en este navegador" : "Sincronización en tiempo real"}</small>
            </div>
          </div>
          {!demoMode && (
            <button className="logout-button" onClick={() => auth && signOut(auth)}>
              Cerrar sesión
            </button>
          )}
          <div className="profile">
            <span className="avatar">{demoMode ? "CG" : user?.email?.slice(0, 2).toUpperCase()}</span>
            <div>
              <strong>{demoMode ? "Equipo ColectiGo" : user?.email}</strong>
              <small>{demoMode ? "Administrador de demo" : "Administración"}</small>
            </div>
          </div>
        </div>
      </aside>

      <main className="main-content" id="viajes">
        <header className="topbar">
          <div className="breadcrumbs">Panel de control <span>/</span> <strong>{section[0].toUpperCase() + section.slice(1)}</strong></div>
          <div className="topbar-right">
            <span className="live-indicator"><i /> {demoMode ? "Vista previa" : "En vivo"}</span>
            <span className="today">{new Intl.DateTimeFormat("es-CL", { dateStyle: "medium" }).format(new Date())}</span>
          </div>
        </header>

        {section === "viajes" ? (
        <>
        <section className="page-heading">
          <div>
            <p className="eyebrow">OPERACIONES</p>
            <h1>Viajes</h1>
            <p className="subtitle">Monitorea y administra los viajes de tu comunidad.</p>
          </div>
          <button className="primary-button" onClick={() => setModalTrip("new")}>
            <span>＋</span> Crear viaje
          </button>
        </section>

        {demoMode && (
          <div className="demo-banner">
            <span className="demo-banner-icon">✦</span>
            <div><strong>Estás en modo demostración</strong><span>Prueba el flujo completo; tus cambios se guardan en este navegador.</span></div>
            <span className="demo-tag">DEMO</span>
          </div>
        )}
        {error && <div className="error-banner" role="alert">{error}</div>}

        <section className="stats-grid" aria-label="Resumen de viajes">
          <StatCard label="Total de viajes" value={trips.length} icon="↗" tone="green" foot="En todos los estados" />
          <StatCard label="En operación" value={activeTrips} icon="◷" tone="blue" foot="Aceptados o en curso" />
          <StatCard label="Por asignar" value={waitingTrips} icon="⌁" tone="amber" foot="Esperando conductor" />
          <StatCard label="Completados" value={completedTrips} icon="✓" tone="violet" foot="Viajes finalizados" />
        </section>

        <section className="table-card">
          <div className="table-heading">
            <div><h2>Todos los viajes</h2><p>Gestiona solicitudes y sigue cada recorrido.</p></div>
            <label className="search-box">
              <span>⌕</span>
              <input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Buscar viaje..." aria-label="Buscar viaje" />
              <kbd>⌘ K</kbd>
            </label>
          </div>
          <div className="filters" role="tablist" aria-label="Filtrar por estado">
            {(["Todos", ...tripStatuses] as StatusFilter[]).map((status) => (
              <button
                key={status}
                className={`filter-tab ${statusFilter === status ? "selected" : ""}`}
                onClick={() => setStatusFilter(status)}
                role="tab"
                aria-selected={statusFilter === status}
              >
                {status}<span>{status === "Todos" ? trips.length : trips.filter((trip) => trip.status === status).length}</span>
              </button>
            ))}
          </div>
          <div className="table-scroll">
            <table>
              <thead><tr><th>VIAJE</th><th>PASAJERO</th><th>CONDUCTOR</th><th>ESTADO</th><th>HORA</th><th><span className="sr-only">Acciones</span></th></tr></thead>
              <tbody>
                {loading ? (
                  <tr><td className="empty-row" colSpan={6}>Cargando viajes…</td></tr>
                ) : filteredTrips.length === 0 ? (
                  <tr><td className="empty-row" colSpan={6}>{trips.length ? "No hay viajes que coincidan con la búsqueda." : "Aún no hay viajes. Crea el primero para comenzar."}</td></tr>
                ) : filteredTrips.map((trip) => (
                  <tr key={trip.id}>
                    <td><div className="route"><span className="route-dot" /><span><strong>{trip.origin}</strong><small>{trip.destination}</small></span></div></td>
                    <td><Person name={trip.passengerName} role="Pasajero" /></td>
                    <td><Person name={trip.driverName} role={trip.driverName === "Por asignar" ? "Pendiente de asignación" : "Conductor"} /></td>
                    <td>
                      <select
                        className={`status-select status-${slug(trip.status)}`}
                        value={trip.status}
                        onChange={(event) => void handleStatusChange(trip, event.target.value as TripStatus)}
                        aria-label={`Cambiar estado del viaje de ${trip.passengerName}`}
                      >
                        {tripStatuses.map((status) => <option key={status}>{status}</option>)}
                      </select>
                    </td>
                    <td className="time-cell">{formatTime(trip.createdAt)}</td>
                    <td><div className="row-actions">
                      <button onClick={() => setModalTrip(trip)} aria-label={`Editar viaje de ${trip.passengerName}`} title="Editar">✎</button>
                      <button onClick={() => void handleDelete(trip)} aria-label={`Eliminar viaje de ${trip.passengerName}`} title="Eliminar">⌫</button>
                    </div></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="table-footer"><span>Mostrando <strong>{filteredTrips.length}</strong> de <strong>{trips.length}</strong> viajes</span><span className="sync-label"><i /> Actualizado en tiempo real</span></div>
        </section>
        <footer className="page-footer">ColectiGo <span>·</span> Movilidad compartida, más simple.</footer>
        </>
        ) : section === "mensajes" ? (
          <AdminMessages demoMode={demoMode} />
        ) : (
          <AdminDirectory kind={section} trips={trips} demoMode={demoMode} />
        )}
      </main>

      {modalTrip && (
        <div className="modal-backdrop" onMouseDown={(event) => event.target === event.currentTarget && setModalTrip(null)}>
          <form className="trip-modal" onSubmit={(event) => void handleSave(event)}>
            <div className="modal-header">
              <div><p className="eyebrow">{modalTrip === "new" ? "NUEVA SOLICITUD" : "GESTIÓN DE VIAJE"}</p><h2>{modalTrip === "new" ? "Crear viaje" : "Editar viaje"}</h2></div>
              <button type="button" className="close-button" onClick={() => setModalTrip(null)} aria-label="Cerrar">×</button>
            </div>
            <label>Nombre del pasajero<input name="passengerName" required maxLength={80} defaultValue={modalTrip === "new" ? "" : modalTrip.passengerName} placeholder="Ej. Valentina Rojas" /></label>
            <label>Nombre del conductor<input name="driverName" maxLength={80} defaultValue={modalTrip === "new" ? "" : modalTrip.driverName === "Por asignar" ? "" : modalTrip.driverName} placeholder="Dejar vacío para asignar después" /></label>
            <div className="form-row">
              <label>Origen<input name="origin" required maxLength={120} defaultValue={modalTrip === "new" ? "" : modalTrip.origin} placeholder="Punto de partida" /></label>
              <label>Destino<input name="destination" required maxLength={120} defaultValue={modalTrip === "new" ? "" : modalTrip.destination} placeholder="Punto de llegada" /></label>
            </div>
            <label>Estado<select name="status" defaultValue={modalTrip === "new" ? "Solicitado" : modalTrip.status}>{tripStatuses.map((status) => <option key={status}>{status}</option>)}</select></label>
            <div className="modal-actions"><button type="button" className="secondary-button" onClick={() => setModalTrip(null)}>Cancelar</button><button type="submit" className="primary-button" disabled={saving}>{saving ? "Guardando…" : "Guardar viaje"}</button></div>
          </form>
        </div>
      )}
    </div>
  );
}

function slug(value: string) {
  return value.toLocaleLowerCase("es").replaceAll(" ", "-");
}

function StatCard({ label, value, icon, tone, foot }: { label: string; value: number; icon: string; tone: string; foot: string }) {
  return <article className="stat-card"><div className="stat-top"><span>{label}</span><span className={`stat-icon ${tone}`}>{icon}</span></div><strong className="stat-value">{value.toString().padStart(2, "0")}</strong><span className="stat-foot">{foot}</span></article>;
}

function Person({ name, role }: { name: string; role: string }) {
  return <div className="person"><span className="person-avatar">{name === "Por asignar" ? "—" : name.split(" ").map((part) => part[0]).slice(0, 2).join("").toUpperCase()}</span><span><strong>{name}</strong><small>{role}</small></span></div>;
}

function MessageScreen({ message }: { message: string }) {
  return <main className="message-screen"><span className="brand-mark">C</span><h1>ColectiGo</h1><p>{message}</p></main>;
}

function LoginScreen() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  async function handleLogin(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!auth) return;
    setLoading(true);
    setError("");
    try {
      await signInWithEmailAndPassword(auth, email, password);
    } catch (loginError) {
      setError(errorMessage(loginError));
    } finally {
      setLoading(false);
    }
  }

  return <main className="login-screen"><form className="login-card" onSubmit={(event) => void handleLogin(event)}>
    <a className="brand login-brand" href="#"><span className="brand-mark">C</span><span>colecti<span className="brand-light">go</span></span></a>
    <p className="eyebrow">PANEL ADMINISTRATIVO</p><h1>Qué bueno verte.</h1><p className="login-copy">Inicia sesión con tu cuenta de administración para continuar.</p>
    {error && <div className="error-banner" role="alert">{error}</div>}
    <label>Correo electrónico<input type="email" autoComplete="username" required value={email} onChange={(event) => setEmail(event.target.value)} placeholder="tu@colectigo.cl" /></label>
    <label>Contraseña<input type="password" autoComplete="current-password" required value={password} onChange={(event) => setPassword(event.target.value)} placeholder="••••••••" /></label>
    <button className="primary-button login-submit" disabled={loading}>{loading ? "Ingresando…" : "Ingresar al panel"}</button>
  </form><span className="login-note">Movilidad compartida, más simple.</span></main>;
}
