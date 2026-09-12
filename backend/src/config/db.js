import fs from "fs";
import pg from "pg";
import { env } from "./env.js";

const { Pool } = pg;

export function getPostgresSslConfig(nodeEnv = env.NODE_ENV, processEnv = process.env) {
  if (nodeEnv !== "production") {
    if (processEnv.DATABASE_SSL === "true") {
      return { rejectUnauthorized: true };
    }
    return false;
  }

  // In production, SSL must be verified. Reject blanket disablement.
  if (
    processEnv.DATABASE_SSL_REJECT_UNAUTHORIZED === "false" ||
    processEnv.DATABASE_SSL_ALLOW_INSECURE === "true"
  ) {
    throw new Error(
      "Insecure PostgreSQL TLS configuration rejected in production: rejectUnauthorized must be true. Configure trusted CA certificates via DATABASE_SSL_CA or system trust store."
    );
  }

  const sslConfig = { rejectUnauthorized: true };

  if (processEnv.DATABASE_SSL_CA) {
    sslConfig.ca = processEnv.DATABASE_SSL_CA;
  } else if (processEnv.DATABASE_SSL_CA_PATH) {
    sslConfig.ca = fs.readFileSync(processEnv.DATABASE_SSL_CA_PATH, "utf8");
  }

  if (processEnv.DATABASE_SSL_CERT) {
    sslConfig.cert = processEnv.DATABASE_SSL_CERT;
  }
  if (processEnv.DATABASE_SSL_KEY) {
    sslConfig.key = processEnv.DATABASE_SSL_KEY;
  }

  return sslConfig;
}

export const pool = new Pool({
  connectionString: env.DATABASE_URL,
  ssl: getPostgresSslConfig()
});

export async function withTransaction(work) {
  const client = await pool.connect();
  try {
    await client.query("BEGIN ISOLATION LEVEL SERIALIZABLE");
    const result = await work(client);
    await client.query("COMMIT");
    return result;
  } catch (error) {
    await client.query("ROLLBACK");
    throw error;
  } finally {
    client.release();
  }
}
