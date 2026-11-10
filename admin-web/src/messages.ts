import {
  addDoc,
  collection,
  onSnapshot,
  orderBy,
  query,
  serverTimestamp,
  Timestamp,
  where,
} from "firebase/firestore";
import { auth, db, demoMode } from "./firebase";

export type MessageAudience = "CONDUCTOR" | "PASAJERO" | "TODOS" | "ADMIN";

export interface AdminMessage {
  id: string;
  audience: MessageAudience;
  content: string;
  senderName: string;
  senderRole: "ADMIN" | "CONDUCTOR" | "PASAJERO";
  senderUid: string;
  createdAt: string;
}

const demoStorageKey = "colectigo-demo-mensajes-v1";
const demoEvent = "colectigo-demo-mensajes-changed";

function demoSeed(): AdminMessage[] {
  const now = Date.now();
  return [
    {
      id: "demo-message-1",
      audience: "TODOS",
      content: "¡Bienvenidos a ColectiGo! El canal de atención está activo.",
      senderName: "Administración ColectiGo",
      senderRole: "ADMIN",
      senderUid: "admin-demo",
      createdAt: new Date(now - 60 * 60_000).toISOString(),
    },
    {
      id: "demo-message-2",
      audience: "CONDUCTOR",
      content: "Recuerden confirmar las solicitudes antes de iniciar el recorrido.",
      senderName: "Administración ColectiGo",
      senderRole: "ADMIN",
      senderUid: "admin-demo",
      createdAt: new Date(now - 25 * 60_000).toISOString(),
    },
    {
      id: "demo-message-3",
      audience: "PASAJERO",
      content: "Selecciona un colectivo y un paradero para solicitar tu viaje.",
      senderName: "Administración ColectiGo",
      senderRole: "ADMIN",
      senderUid: "admin-demo",
      createdAt: new Date(now - 10 * 60_000).toISOString(),
    },
  ];
}

function readDemoMessages(): AdminMessage[] {
  const stored = localStorage.getItem(demoStorageKey);
  if (!stored) {
    const seed = demoSeed();
    localStorage.setItem(demoStorageKey, JSON.stringify(seed));
    return seed;
  }
  return JSON.parse(stored) as AdminMessage[];
}

function writeDemoMessages(messages: AdminMessage[]) {
  localStorage.setItem(demoStorageKey, JSON.stringify(messages));
  window.dispatchEvent(new Event(demoEvent));
}

function requireDb() {
  if (!db) throw new Error("Firestore no está configurado.");
  return db;
}

function asMessage(id: string, data: Record<string, unknown>): AdminMessage {
  const audience = String(data.audience ?? "TODOS");
  return {
    id,
    audience:
      audience === "CONDUCTOR" ||
      audience === "PASAJERO" ||
      audience === "ADMIN"
        ? audience
        : "TODOS",
    content: String(data.content ?? ""),
    senderName: String(data.senderName ?? "ColectiGo"),
    senderRole:
      data.senderRole === "CONDUCTOR" || data.senderRole === "PASAJERO"
        ? data.senderRole
        : "ADMIN",
    senderUid: String(data.senderUid ?? ""),
    createdAt: data.createdAt instanceof Timestamp
      ? data.createdAt.toDate().toISOString()
      : typeof data.createdAt === "string"
        ? data.createdAt
        : new Date().toISOString(),
  };
}

export function subscribeAdminMessages(
  onMessages: (messages: AdminMessage[]) => void,
  onError: (error: Error) => void,
) {
  if (demoMode) {
    const publish = () => {
      try {
        onMessages(
          readDemoMessages().sort((a, b) =>
            b.createdAt.localeCompare(a.createdAt),
          ),
        );
      } catch (error) {
        onError(error instanceof Error ? error : new Error(String(error)));
      }
    };
    publish();
    window.addEventListener(demoEvent, publish);
    return () => window.removeEventListener(demoEvent, publish);
  }

  return onSnapshot(
    query(collection(requireDb(), "mensajes"), orderBy("createdAt", "desc")),
    (snapshot) => {
      onMessages(
        snapshot.docs.map((message) =>
          asMessage(message.id, message.data()),
        ),
      );
    },
    onError,
  );
}

export async function sendAdminMessage(
  audience: Exclude<MessageAudience, "ADMIN">,
  content: string,
) {
  const cleanContent = content.trim();
  if (!cleanContent) throw new Error("Escribe un mensaje antes de enviarlo.");

  if (demoMode) {
    const message: AdminMessage = {
      id: crypto.randomUUID(),
      audience,
      content: cleanContent,
      senderName: "Administración ColectiGo",
      senderRole: "ADMIN",
      senderUid: "admin-demo",
      createdAt: new Date().toISOString(),
    };
    writeDemoMessages([message, ...readDemoMessages()]);
    return;
  }

  const currentUser = auth?.currentUser;
  if (!currentUser) throw new Error("Inicia sesión para enviar mensajes.");
  await addDoc(collection(requireDb(), "mensajes"), {
    audience,
    content: cleanContent,
    senderName: currentUser.email ?? "Administración ColectiGo",
    senderRole: "ADMIN",
    senderUid: currentUser.uid,
    createdAt: serverTimestamp(),
  });
}

export async function sendMobileMessage(
  audience: "CONDUCTOR" | "PASAJERO",
  senderName: string,
  content: string,
) {
  const cleanContent = content.trim();
  if (!cleanContent) throw new Error("Escribe un mensaje antes de enviarlo.");

  if (demoMode) {
    const message: AdminMessage = {
      id: crypto.randomUUID(),
      audience: "ADMIN",
      content: cleanContent,
      senderName,
      senderRole: audience,
      senderUid: "demo-mobile",
      createdAt: new Date().toISOString(),
    };
    writeDemoMessages([message, ...readDemoMessages()]);
    return;
  }

  const currentUser = auth?.currentUser;
  if (!currentUser) throw new Error("La sesión de Firebase todavía no está lista.");
  await addDoc(collection(requireDb(), "mensajes"), {
    audience: "ADMIN",
    content: cleanContent,
    senderName,
    senderRole: audience,
    senderUid: currentUser.uid,
    createdAt: new Date().toISOString(),
  });
}

export function subscribeMobileMessages(
  audience: "CONDUCTOR" | "PASAJERO",
  onMessages: (messages: AdminMessage[]) => void,
  onError: (error: Error) => void,
) {
  if (demoMode) {
    const publish = () => {
      try {
        const messages = readDemoMessages().filter(
          (message) =>
            message.audience === audience ||
            message.audience === "TODOS" ||
            (message.audience === "ADMIN" &&
              message.senderUid === "demo-mobile"),
        );
        onMessages(messages.sort((a, b) => a.createdAt.localeCompare(b.createdAt)));
      } catch (error) {
        onError(error instanceof Error ? error : new Error(String(error)));
      }
    };
    publish();
    window.addEventListener(demoEvent, publish);
    return () => window.removeEventListener(demoEvent, publish);
  }

  const firestore = requireDb();
  const messages = new Map<string, AdminMessage>();
  const unsubscribers = [
    onSnapshot(
      query(
        collection(firestore, "mensajes"),
        where("audience", "==", audience),
      ),
      (snapshot) => {
        snapshot.docs.forEach((message) =>
          messages.set(message.id, asMessage(message.id, message.data())),
        );
        onMessages(
          [...messages.values()].sort((a, b) =>
            a.createdAt.localeCompare(b.createdAt),
          ),
        );
      },
      onError,
    ),
    onSnapshot(
      query(collection(firestore, "mensajes"), where("audience", "==", "TODOS")),
      (snapshot) => {
        snapshot.docs.forEach((message) =>
          messages.set(message.id, asMessage(message.id, message.data())),
        );
        onMessages(
          [...messages.values()].sort((a, b) =>
            a.createdAt.localeCompare(b.createdAt),
          ),
        );
      },
      onError,
    ),
  ];
  return () => unsubscribers.forEach((unsubscribe) => unsubscribe());
}
