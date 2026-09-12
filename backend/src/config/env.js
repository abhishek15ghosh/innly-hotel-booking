import dotenv from "dotenv";

dotenv.config();

const requiredKeys = ["DATABASE_URL", "FIREBASE_PROJECT_ID", "RAZORPAY_KEY_ID", "RAZORPAY_KEY_SECRET"];

for (const key of requiredKeys) {
  if (!process.env[key]) {
    throw new Error(`Missing required environment variable: ${key}`);
  }
}

if (!process.env.FIREBASE_SERVICE_ACCOUNT_JSON && !process.env.FIREBASE_SERVICE_ACCOUNT_PATH) {
  throw new Error(
    "Missing Firebase credentials: set FIREBASE_SERVICE_ACCOUNT_JSON or FIREBASE_SERVICE_ACCOUNT_PATH"
  );
}

// CORS_ALLOWED_ORIGINS may be empty (e.g. native mobile apps only, which send no Origin header).
// When empty in production, browser requests with an Origin header receive HTTP 403 by default.

function parseTrustProxyHops(val) {
  if (val === undefined || val === null || val === "") {
    return 0;
  }
  const parsed = Number(val);
  if (!Number.isInteger(parsed) || parsed < 0) {
    throw new Error(`Invalid TRUST_PROXY_HOPS: '${val}'. Must be a non-negative integer.`);
  }
  return parsed;
}

export const env = {
  PORT: Number(process.env.PORT || 8080),
  NODE_ENV: process.env.NODE_ENV || "development",
  DATABASE_URL: process.env.DATABASE_URL,
  FIREBASE_PROJECT_ID: process.env.FIREBASE_PROJECT_ID,
  FIREBASE_SERVICE_ACCOUNT_JSON: process.env.FIREBASE_SERVICE_ACCOUNT_JSON,
  FIREBASE_SERVICE_ACCOUNT_PATH: process.env.FIREBASE_SERVICE_ACCOUNT_PATH,
  RAZORPAY_KEY_ID: process.env.RAZORPAY_KEY_ID,
  RAZORPAY_KEY_SECRET: process.env.RAZORPAY_KEY_SECRET,
  RAZORPAY_WEBHOOK_SECRET: process.env.RAZORPAY_WEBHOOK_SECRET || "",
  RAZORPAY_WEBHOOK_ENABLED: process.env.RAZORPAY_WEBHOOK_ENABLED === "true",
  CLIENT_APP_NAME: process.env.CLIENT_APP_NAME || "Innly",
  TRUST_PROXY_HOPS: parseTrustProxyHops(process.env.TRUST_PROXY_HOPS),
  CORS_ALLOWED_ORIGINS: process.env.CORS_ALLOWED_ORIGINS || ""
};
