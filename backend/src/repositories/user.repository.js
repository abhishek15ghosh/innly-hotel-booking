import { pool } from "../config/db.js";

export async function findByFirebaseUid(client, firebaseUid) {
  const db = client?.query ? client : pool;
  const result = await db.query(
    `SELECT id, firebase_uid AS "firebaseUid", email, display_name AS "displayName",
            phone_number AS "phoneNumber", role
     FROM users
     WHERE firebase_uid = $1`,
    [firebaseUid]
  );
  return result.rows[0] || null;
}

export async function upsertUser(client, user) {
  const result = await client.query(
    `INSERT INTO users (firebase_uid, email, display_name, phone_number)
     VALUES ($1, $2, $3, $4)
     ON CONFLICT (firebase_uid)
     DO UPDATE SET
       email = EXCLUDED.email,
       display_name = COALESCE(EXCLUDED.display_name, users.display_name),
       phone_number = COALESCE(EXCLUDED.phone_number, users.phone_number),
       updated_at = NOW()
     RETURNING id, firebase_uid AS "firebaseUid", email, display_name AS "displayName",
               phone_number AS "phoneNumber", role`,
    [user.firebaseUid, user.email, user.displayName || null, user.phoneNumber || null]
  );
  return result.rows[0];
}
