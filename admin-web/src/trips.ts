import {
  addDoc,
  collection,
  deleteDoc,
  doc,
  onSnapshot,
  updateDoc,
} from "firebase/firestore";
import { db, demoMode } from "./firebase";

export const tripStatuses = [
  "Solicitado",
  "Aceptado",
  "Rechazado",
  "En curso",
  "Completado",
  "Cancelado",
] as const;

export type TripStatus = (typeof tripStatuses)[number];

export interface Trip {
  id: string;
  passengerName: string;
  driverName: string;
  origin: string;
  destination: string;
  status: TripStatus;
  createdAt: string;
  vehicleId: number;
  patente: string;
  monto: number;
  passengerUid: string;
}

export type TripInput = Omit<Trip, "id">;

const collectionName = "reservas";
const storageKey = "colectigo-demo-viajes-v1";
const demoEvent = "colectigo-demo-viajes-changed";

const firestoreToStatus: Record<string, TripStatus> = {
  PENDIENTE: "Solicitado",
  SOLICITADO: "Solicitado",
  ACEPTADA: "Aceptado",
  ACEPTADO: "Aceptado",
  RECHAZADA: "Rechazado",
  RECHAZADO: "Rechazado",
  EN_CURSO: "En curso",
  COMPLETADA: "Completado",
  COMPLETADO: "Completado",
  CANCELADA: "Cancelado",
  CANCELADO: "Cancelado",
};

const statusToFirestore: Record<TripStatus, string> = {
  Solicitado: "PENDIENTE",
  Aceptado: "ACEPTADA",
  Rechazado: "RECHAZADA",
  "En curso": "EN_CURSO",
  Completado: "COMPLETADA",
  Cancelado: "CANCELADA",
};

const demoTrips: Trip[] = [
  {
    id: "demo-1",
    passengerName: "Valentina Rojas",
    driverName: "Tomás Muñoz",
    origin: "Plaza de Armas",
    destination: "Terminal Alameda",
    status: "En curso",
    createdAt: new Date(Date.now() - 12 * 60_000).toISOString(),
    vehicleId: 1,
    patente: "ABCD12",
    monto: 1200,
    passengerUid: "demo-passenger",
  },
  {
    id: "demo-2",
    passengerName: "Matías Soto",
    driverName: "Camila Fuentes",
    origin: "Hospital Regional",
    destination: "Universidad Central",
    status: "Aceptado",
    createdAt: new Date(Date.now() - 28 * 60_000).toISOString(),
    vehicleId: 1,
    patente: "ABCD12",
    monto: 1200,
    passengerUid: "demo-passenger",
  },
  {
    id: "demo-3",
    passengerName: "Josefina Díaz",
    driverName: "Por asignar",
    origin: "Mall Plaza",
    destination: "Las Condes",
    status: "Solicitado",
    createdAt: new Date(Date.now() - 43 * 60_000).toISOString(),
    vehicleId: 1,
    patente: "ABCD12",
    monto: 1200,
    passengerUid: "demo-passenger",
  },
  {
    id: "demo-4",
    passengerName: "Benjamín Pérez",
    driverName: "Nicolás Silva",
    origin: "Estación Central",
    destination: "Providencia",
    status: "Completado",
    createdAt: new Date(Date.now() - 94 * 60_000).toISOString(),
    vehicleId: 1,
    patente: "ABCD12",
    monto: 1200,
    passengerUid: "demo-passenger",
  },
];

function readDemoTrips(): Trip[] {
  const saved = localStorage.getItem(storageKey);
  if (!saved) {
    localStorage.setItem(storageKey, JSON.stringify(demoTrips));
    return demoTrips;
  }
  const parsed = JSON.parse(saved) as Partial<Trip>[];
  return parsed.map((trip) => ({
    id: String(trip.id ?? crypto.randomUUID()),
    passengerName: String(trip.passengerName ?? ""),
    driverName: String(trip.driverName ?? "Por asignar"),
    origin: String(trip.origin ?? ""),
    destination: String(trip.destination ?? ""),
    status: tripStatuses.includes(trip.status as TripStatus)
      ? (trip.status as TripStatus)
      : "Solicitado",
    createdAt: String(trip.createdAt ?? new Date().toISOString()),
    vehicleId: Number(trip.vehicleId ?? 0),
    patente: String(trip.patente ?? ""),
    monto: Number(trip.monto ?? 0),
    passengerUid: String(trip.passengerUid ?? ""),
  }));
}

function writeDemoTrips(trips: Trip[]) {
  localStorage.setItem(storageKey, JSON.stringify(trips));
  window.dispatchEvent(new Event(demoEvent));
}

function requireDb() {
  if (!db) throw new Error("Firestore no está configurado.");
  return db;
}

function statusFromData(data: Record<string, unknown>): TripStatus {
  const rawStatus = String(data.estado ?? data.status ?? "").toUpperCase();
  if (firestoreToStatus[rawStatus]) return firestoreToStatus[rawStatus];
  if (tripStatuses.includes(data.status as TripStatus)) {
    return data.status as TripStatus;
  }
  return "Solicitado";
}

function toFirestore(input: TripInput) {
  return {
    passengerName: input.passengerName,
    passengerUid: input.passengerUid,
    driverName: input.driverName,
    vehicleId: input.vehicleId,
    patente: input.patente,
    origin: input.origin,
    destination: input.destination,
    paradaNombre: input.destination,
    monto: input.monto,
    estado: statusToFirestore[input.status],
    fecha: input.createdAt,
    createdAt: input.createdAt,
  };
}

export function subscribeTrips(
  onTrips: (trips: Trip[]) => void,
  onError: (error: Error) => void,
) {
  if (demoMode) {
    const publish = () => {
      try {
        onTrips(readDemoTrips());
      } catch (error) {
        onError(error instanceof Error ? error : new Error(String(error)));
      }
    };
    publish();
    window.addEventListener(demoEvent, publish);
    return () => window.removeEventListener(demoEvent, publish);
  }

  return onSnapshot(
    collection(requireDb(), collectionName),
    (snapshot) => {
      const trips = snapshot.docs.map((tripDoc) => {
        const data = tripDoc.data();
        return {
          id: tripDoc.id,
          passengerName: String(data.passengerName ?? ""),
          driverName: String(data.driverName ?? "Por asignar"),
          origin: String(data.origin ?? "Ruta ColectiGo"),
          destination: String(data.destination ?? data.paradaNombre ?? ""),
          status: statusFromData(data),
          createdAt:
            typeof data.createdAt === "string" || typeof data.fecha === "string"
              ? String(data.createdAt ?? data.fecha)
              : new Date().toISOString(),
          vehicleId: Number(data.vehicleId ?? 0),
          patente: String(data.patente ?? ""),
          monto: Number(data.monto ?? 0),
          passengerUid: String(data.passengerUid ?? ""),
        } satisfies Trip;
      });
      onTrips(trips);
    },
    onError,
  );
}

export async function createTrip(input: TripInput) {
  if (demoMode) {
    const trips = readDemoTrips();
    writeDemoTrips([{ ...input, id: crypto.randomUUID() }, ...trips]);
    return;
  }
  await addDoc(collection(requireDb(), collectionName), toFirestore(input));
}

export async function updateTrip(id: string, input: TripInput) {
  if (demoMode) {
    writeDemoTrips(
      readDemoTrips().map((trip) =>
        trip.id === id ? { ...input, id } : trip,
      ),
    );
    return;
  }
  await updateDoc(doc(requireDb(), collectionName, id), toFirestore(input));
}

export async function deleteTrip(id: string) {
  if (demoMode) {
    writeDemoTrips(readDemoTrips().filter((trip) => trip.id !== id));
    return;
  }
  await deleteDoc(doc(requireDb(), collectionName, id));
}
