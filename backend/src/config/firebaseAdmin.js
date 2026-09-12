import admin from "firebase-admin";
import fs from "fs";
import { env } from "./env.js";

function loadServiceAccount() {
  if (env.FIREBASE_SERVICE_ACCOUNT_PATH) {
    return JSON.parse(fs.readFileSync(env.FIREBASE_SERVICE_ACCOUNT_PATH, "utf-8"));
  }

  return JSON.parse(env.FIREBASE_SERVICE_ACCOUNT_JSON);
}

if (!admin.apps.length) {
  admin.initializeApp({
    credential: admin.credential.cert(loadServiceAccount()),
    projectId: env.FIREBASE_PROJECT_ID
  });
}

export const firebaseAdmin = admin;
