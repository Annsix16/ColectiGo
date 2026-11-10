import { initializeApp } from "firebase/app";
import { getAuth } from "firebase/auth";
import { getFirestore } from "firebase/firestore";

const config = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY,
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN,
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID,
  storageBucket: import.meta.env.VITE_FIREBASE_STORAGE_BUCKET,
  messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID,
  appId: import.meta.env.VITE_FIREBASE_APP_ID,
};

const values = Object.values(config);
const hasFirebaseConfig = values.some(Boolean);
const hasCompleteFirebaseConfig = values.every(Boolean);

export const configError =
  hasFirebaseConfig && !hasCompleteFirebaseConfig
    ? "La configuración de Firebase está incompleta. Revisa las variables VITE_FIREBASE_* de .env.local."
    : null;
export const demoMode =
  import.meta.env.VITE_DEMO_MODE === "true" || !hasFirebaseConfig;

const app =
  hasCompleteFirebaseConfig && !demoMode ? initializeApp(config) : null;

export const auth = app ? getAuth(app) : null;
export const db = app ? getFirestore(app) : null;
