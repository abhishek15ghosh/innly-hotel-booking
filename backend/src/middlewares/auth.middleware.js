import { firebaseAdmin } from "../config/firebaseAdmin.js";
import { pool } from "../config/db.js";
import { ApiError } from "../utils/apiError.js";

async function decodeAndAttachUser(req, required) {
  const authorization = req.headers.authorization || "";
  const token = authorization.startsWith("Bearer ") ? authorization.slice(7) : null;

  if (!token) {
    if (required) {
      throw new ApiError(401, "Missing Firebase bearer token");
    }
    req.user = null;
    return;
  }

  const decodedToken = await firebaseAdmin.auth().verifyIdToken(token);
  const result = await pool.query(
    `SELECT id, role, email, display_name AS "displayName", phone_number AS "phoneNumber"
     FROM users
     WHERE firebase_uid = $1`,
    [decodedToken.uid]
  );
  const dbUser = result.rows[0] || null;

  req.user = {
    id: dbUser?.id || null,
    firebaseUid: decodedToken.uid,
    email: decodedToken.email || dbUser?.email || null,
    displayName: dbUser?.displayName || decodedToken.name || null,
    phoneNumber: dbUser?.phoneNumber || decodedToken.phone_number || null,
    role: dbUser?.role || decodedToken.role || "user"
  };
}

export async function authenticate(req, res, next) {
  try {
    await decodeAndAttachUser(req, true);
    next();
  } catch (error) {
    next(error.statusCode ? error : new ApiError(401, "Invalid Firebase token"));
  }
}

export async function optionalAuthenticate(req, res, next) {
  try {
    await decodeAndAttachUser(req, false);
    next();
  } catch (error) {
    next(error.statusCode ? error : new ApiError(401, "Invalid Firebase token"));
  }
}

export function requireAdmin(req, res, next) {
  if (!req.user || req.user.role !== "admin") {
    return next(new ApiError(403, "Admin access required"));
  }
  next();
}
