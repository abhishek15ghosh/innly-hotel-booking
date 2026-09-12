import crypto from "crypto";
import fs from "fs";
import path from "path";
import pg from "pg";
import { fileURLToPath } from "url";
import { getPostgresSslConfig } from "../config/db.js";
import { env } from "../config/env.js";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const DEFAULT_MIGRATIONS_DIR = path.resolve(__dirname, "../migrations");
const LOCK_KEY_NAME = "innly_migrations_lock";

/**
 * Computes SHA-256 hash of file content.
 */
export function computeChecksum(content) {
  return crypto.createHash("sha256").update(content, "utf8").digest("hex");
}

/**
 * Robust lexical tokenizer for PostgreSQL SQL that strips exactly one outer BEGIN; and COMMIT; pair
 * while safely ignoring statements inside line comments, block comments, single-quoted strings,
 * bare dollar-quoted blocks ($$...$$), and tagged dollar-quoted blocks ($tag$...$tag$).
 */
export function sanitizeMigrationSql(rawSql) {
  let i = 0;
  const len = rawSql.length;
  let masked = "";

  while (i < len) {
    // 1. Line comment (-- ...)
    if (rawSql[i] === "-" && rawSql[i + 1] === "-") {
      const end = rawSql.indexOf("\n", i + 2);
      const commentEnd = end === -1 ? len : end;
      masked += " ".repeat(commentEnd - i);
      i = commentEnd;
      continue;
    }

    // 2. Block comment (/* ... */)
    if (rawSql[i] === "/" && rawSql[i + 1] === "*") {
      const end = rawSql.indexOf("*/", i + 2);
      if (end === -1) {
        throw new Error("Unclosed block comment in migration SQL");
      }
      masked += " ".repeat(end + 2 - i);
      i = end + 2;
      continue;
    }

    // 3. Dollar-quoted blocks ($tag$...$tag$ or $$...$$)
    if (rawSql[i] === "$") {
      const tagMatch = rawSql.slice(i).match(/^\$([a-zA-Z0-9_]*)\$/);
      if (tagMatch) {
        const fullTag = tagMatch[0];
        const closeIndex = rawSql.indexOf(fullTag, i + fullTag.length);
        if (closeIndex === -1) {
          throw new Error(`Unclosed dollar quote ${fullTag} in migration SQL`);
        }
        const blockEnd = closeIndex + fullTag.length;
        masked += " ".repeat(blockEnd - i);
        i = blockEnd;
        continue;
      }
    }

    // 4. Single-quoted strings ('...')
    if (rawSql[i] === "'") {
      let j = i + 1;
      let closed = false;
      while (j < len) {
        if (rawSql[j] === "'") {
          if (rawSql[j + 1] === "'") {
            j += 2; // Escaped quote
          } else {
            j += 1;
            closed = true;
            break;
          }
        } else {
          j++;
        }
      }
      if (!closed) {
        throw new Error("Unclosed single quote in migration SQL");
      }
      masked += " ".repeat(j - i);
      i = j;
      continue;
    }

    masked += rawSql[i];
    i++;
  }

  const nonMaskedTrimmed = masked.trim();
  if (nonMaskedTrimmed.length === 0) {
    return rawSql.trim();
  }

  const disallowedKeywords = /\b(rollback|savepoint|release\s+savepoint)\b/i;
  if (disallowedKeywords.test(masked)) {
    throw new Error("Disallowed transaction control statement (ROLLBACK/SAVEPOINT) found in migration SQL");
  }

  const beginMatch = masked.match(/^\s*begin\s*;/i);
  const commitMatch = masked.match(/;\s*commit\s*;?\s*$/i);

  let cleaned = rawSql;

  if (beginMatch && commitMatch) {
    const beginIndex = beginMatch.index + beginMatch[0].length;
    const commitIndex = commitMatch.index + 1;
    cleaned = rawSql.slice(beginIndex, commitIndex) + rawSql.slice(commitMatch.index + commitMatch[0].length);
  } else if (beginMatch && !commitMatch) {
    throw new Error("Invalid transaction structure: Migration contains top-level BEGIN without matching outer COMMIT");
  } else if (!beginMatch && commitMatch) {
    throw new Error("Invalid transaction structure: Migration contains top-level COMMIT without matching outer BEGIN");
  }

  const remainingMasked = sanitizeMaskedOuter(masked);
  if (/\b(begin|commit)\b/i.test(remainingMasked)) {
    throw new Error("Invalid transaction structure: Multiple or nested top-level BEGIN/COMMIT statements found");
  }

  return cleaned.trim();
}

function sanitizeMaskedOuter(masked) {
  let res = masked.replace(/^\s*begin\s*;/i, "");
  res = res.replace(/;\s*commit\s*;?\s*$/i, ";");
  return res;
}

/**
 * Maps specific migration artifacts to their owning migration file.
 */
const MIGRATION_ARTIFACT_MAP = {
  "001": [
    { type: "table", name: "webhook_events" },
    { type: "index", name: "idx_webhook_events_status" },
    { type: "column", table: "webhook_events", name: "id" },
    { type: "column", table: "webhook_events", name: "event_id" },
    { type: "column", table: "webhook_events", name: "event_type" },
    { type: "column", table: "webhook_events", name: "status" },
    { type: "column", table: "webhook_events", name: "created_at" }
  ],
  "002": [
    { type: "constraint", table: "webhook_events", name: "webhook_events_status_check" }
  ],
  "003": [
    { type: "column", table: "hotels", name: "free_cancellation_hours" },
    { type: "enum_value", typeName: "booking_status", value: "cancellation_pending" },
    { type: "enum_value", typeName: "payment_status", value: "refund_pending" },
    { type: "enum", name: "refund_status" },
    { type: "table", name: "refunds" },
    { type: "constraint", table: "refunds", name: "refunds_amount_check" },
    { type: "constraint", table: "refunds", name: "refunds_booking_id_key" },
    { type: "constraint", table: "refunds", name: "refunds_idempotency_key_key" },
    { type: "constraint", table: "refunds", name: "refunds_razorpay_refund_id_key" },
    { type: "index", name: "idx_refunds_booking" },
    { type: "index", name: "idx_refunds_idempotency" },
    { type: "index", name: "idx_refunds_status" }
  ],
  "004": [
    { type: "column", table: "refunds", name: "attempt_count" },
    { type: "column", table: "refunds", name: "last_attempt_at" },
    { type: "column", table: "refunds", name: "next_attempt_at" },
    { type: "column", table: "refunds", name: "lease_until" },
    { type: "column", table: "refunds", name: "leased_by" },
    { type: "index", name: "idx_refunds_lease_claim" }
  ],
  "005": [
    { type: "column_default", table: "reviews", column: "status", pattern: "pending" },
    { type: "index", name: "idx_reviews_booking_id_unique" },
    { type: "index", name: "idx_reviews_hotel_approved_pagination" },
    { type: "index", name: "idx_reviews_user_hotel" }
  ]
};

/**
 * Detects untracked migration-owned artifacts for a given list of pending migrations.
 */
export async function detectUntrackedMigrationArtifacts(client, pendingFiles) {
  const found = [];

  const checkTable = async (tableName) => {
    const res = await client.query(
      "SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = $1",
      [tableName]
    );
    return res.rowCount > 0;
  };

  const checkColumn = async (tableName, columnName) => {
    const res = await client.query(
      "SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = $1 AND column_name = $2",
      [tableName, columnName]
    );
    return res.rowCount > 0;
  };

  const checkEnum = async (enumName) => {
    const res = await client.query("SELECT 1 FROM pg_type WHERE typname = $1", [enumName]);
    return res.rowCount > 0;
  };

  const checkEnumValue = async (enumName, label) => {
    const res = await client.query(`
      SELECT 1 FROM pg_enum e
      JOIN pg_type t ON e.enumtypid = t.oid
      WHERE t.typname = $1 AND e.enumlabel = $2
    `, [enumName, label]);
    return res.rowCount > 0;
  };

  const checkConstraint = async (tableName, constraintName) => {
    const res = await client.query(`
      SELECT 1 FROM pg_constraint c
      JOIN pg_class t ON c.conrelid = t.oid
      JOIN pg_namespace n ON t.relnamespace = n.oid
      WHERE n.nspname = 'public' AND t.relname = $1 AND c.conname = $2
    `, [tableName, constraintName]);
    return res.rowCount > 0;
  };

  const checkIndex = async (indexName) => {
    const res = await client.query(
      "SELECT 1 FROM pg_indexes WHERE schemaname = 'public' AND indexname = $1",
      [indexName]
    );
    return res.rowCount > 0;
  };

  const checkColumnDefault = async (tableName, columnName, pattern) => {
    const res = await client.query(
      "SELECT column_default FROM information_schema.columns WHERE table_schema = 'public' AND table_name = $1 AND column_name = $2",
      [tableName, columnName]
    );
    return res.rows[0]?.column_default?.includes(pattern) || false;
  };

  for (const file of pendingFiles) {
    const prefix = file.slice(0, 3);
    const artifacts = MIGRATION_ARTIFACT_MAP[prefix] || [];

    for (const art of artifacts) {
      let exists = false;
      if (art.type === "table") exists = await checkTable(art.name);
      else if (art.type === "column") exists = await checkColumn(art.table, art.name);
      else if (art.type === "enum") exists = await checkEnum(art.name);
      else if (art.type === "enum_value") exists = await checkEnumValue(art.typeName, art.value);
      else if (art.type === "constraint") exists = await checkConstraint(art.table, art.name);
      else if (art.type === "index") exists = await checkIndex(art.name);
      else if (art.type === "column_default") exists = await checkColumnDefault(art.table, art.column, art.pattern);

      if (exists) {
        found.push({ migration: file, artifact: art });
      }
    }
  }

  return found;
}

/**
 * Normalizes PostgreSQL default expressions for strict comparison.
 */
function normalizeDefault(def) {
  if (!def) return null;
  let s = def.trim();
  // Strip trailing typecast like ::character varying, ::bpchar, ::integer, etc.
  s = s.replace(/::[a-zA-Z0-9_\s]+(\[\])?$/g, "").trim();
  while (s.startsWith("(") && s.endsWith(")") && s.length > 2) {
    s = s.slice(1, -1).trim();
  }
  if (s.startsWith("'") && s.endsWith("'")) {
    s = s.slice(1, -1);
  }
  return s;
}

/**
 * Deep structural baseline verification for migrations 001-005 using exact metadata.
 */
export async function verifyBaselineStructure(client) {
  const diagnostics = [];

  const tableExists = async (tableName) => {
    const res = await client.query(
      "SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = $1",
      [tableName]
    );
    return res.rowCount > 0;
  };

  const columnDetails = async (tableName, columnName) => {
    const res = await client.query(`
      SELECT data_type, udt_name, is_nullable, column_default, character_maximum_length
      FROM information_schema.columns
      WHERE table_schema = 'public' AND table_name = $1 AND column_name = $2
    `, [tableName, columnName]);
    return res.rows[0] || null;
  };

  const enumValues = async (enumName) => {
    const res = await client.query(`
      SELECT e.enumlabel
      FROM pg_enum e
      JOIN pg_type t ON e.enumtypid = t.oid
      WHERE t.typname = $1
      ORDER BY e.enumsortorder
    `, [enumName]);
    return res.rows.map((r) => r.enumlabel);
  };

  const getConstraintDef = async (tableName, constraintName) => {
    const res = await client.query(`
      SELECT pg_get_constraintdef(c.oid) AS def, c.contype
      FROM pg_constraint c
      JOIN pg_class t ON c.conrelid = t.oid
      JOIN pg_namespace n ON t.relnamespace = n.oid
      WHERE n.nspname = 'public' AND t.relname = $1 AND c.conname = $2
    `, [tableName, constraintName]);
    return res.rows[0] || null;
  };

  const parsePgArray = (val) => {
    if (!val) return [];
    if (Array.isArray(val)) return val;
    if (typeof val === "string") {
      const trimmed = val.replace(/^\{|\}$/g, "").trim();
      return trimmed.length > 0 ? trimmed.split(",").map((s) => s.trim().replace(/^"|"$/g, "")) : [];
    }
    return [];
  };

  const getIndexInfo = async (indexName) => {
    const res = await client.query(`
      SELECT
        pg_get_indexdef(i.indexrelid) AS def,
        t.relname AS tablename,
        i.indisunique AS is_unique,
        am.amname AS access_method,
        pg_get_expr(i.indpred, i.indrelid) AS predicate,
        i.indnatts AS total_atts,
        i.indnkeyatts AS key_atts,
        (i.indexprs IS NOT NULL) AS has_expressions,
        ARRAY(
          SELECT a.attname
          FROM unnest(string_to_array(i.indkey::text, ' ')::int[]) WITH ORDINALITY AS k(attnum, ord)
          JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = k.attnum
          ORDER BY k.ord
        ) AS key_columns
      FROM pg_index i
      JOIN pg_class c ON c.oid = i.indexrelid
      JOIN pg_class t ON t.oid = i.indrelid
      JOIN pg_am am ON c.relam = am.oid
      JOIN pg_namespace n ON n.oid = c.relnamespace
      WHERE n.nspname = 'public' AND c.relname = $1
    `, [indexName]);
    if (!res.rows[0]) return null;
    const row = res.rows[0];
    return {
      ...row,
      key_columns: parsePgArray(row.key_columns)
    };
  };

  const singleColumnUnique = async (tableName, columnName) => {
    const res = await client.query(`
      SELECT 1
      FROM pg_index i
      JOIN pg_class c ON c.oid = i.indexrelid
      JOIN pg_class t ON t.oid = i.indrelid
      JOIN pg_namespace n ON n.oid = t.relnamespace
      WHERE n.nspname = 'public'
        AND t.relname = $1
        AND i.indisunique = true
        AND i.indnatts = 1
        AND i.indnkeyatts = 1
        AND i.indpred IS NULL
        AND i.indexprs IS NULL
        AND (SELECT attname FROM pg_attribute WHERE attrelid = t.oid AND attnum = i.indkey[0]) = $2
    `, [tableName, columnName]);
    return res.rowCount > 0;
  };

  // --- 001 & 002: webhook_events ---
  if (!(await tableExists("webhook_events"))) {
    diagnostics.push("Missing table 'webhook_events' (migration 001)");
  } else {
    // Exact PK
    const pkDef = await getConstraintDef("webhook_events", "webhook_events_pkey");
    if (!pkDef || pkDef.contype !== 'p' || pkDef.def.trim() !== "PRIMARY KEY (id)") {
      diagnostics.push("Missing exact primary key 'PRIMARY KEY (id)' on 'webhook_events' (migration 001)");
    }

    const whCols = [
      { name: "id", type: "uuid", nullable: "NO", defaultVal: "gen_random_uuid()" },
      { name: "event_id", type: "character varying", len: 255, nullable: "NO" },
      { name: "event_type", type: "character varying", len: 100, nullable: "NO" },
      { name: "status", type: "character varying", len: 50, nullable: "NO", defaultVal: "received" },
      { name: "failure_reason", type: "text", nullable: "YES" },
      { name: "created_at", type: "timestamp with time zone", nullable: "NO", defaultVal: "now()" },
      { name: "processed_at", type: "timestamp with time zone", nullable: "YES" }
    ];

    for (const c of whCols) {
      const col = await columnDetails("webhook_events", c.name);
      if (!col) {
        diagnostics.push(`Missing column 'webhook_events.${c.name}' (migration 001)`);
      } else {
        if (col.data_type !== c.type) diagnostics.push(`Column 'webhook_events.${c.name}' type mismatch (expected ${c.type}, got ${col.data_type})`);
        if (c.len && col.character_maximum_length !== c.len) diagnostics.push(`Column 'webhook_events.${c.name}' varchar length mismatch (expected ${c.len}, got ${col.character_maximum_length})`);
        if (col.is_nullable !== c.nullable) diagnostics.push(`Column 'webhook_events.${c.name}' nullability mismatch (expected ${c.nullable}, got ${col.is_nullable})`);
        if (c.defaultVal && normalizeDefault(col.column_default) !== c.defaultVal) {
          diagnostics.push(`Column 'webhook_events.${c.name}' default mismatch (expected '${c.defaultVal}', got '${normalizeDefault(col.column_default)}')`);
        }
      }
    }

    if (!(await singleColumnUnique("webhook_events", "event_id"))) {
      diagnostics.push("Missing unconditional single-column unique constraint/index on 'webhook_events.event_id' (migration 001/002)");
    }

    // CHECK constraint: webhook_events_status_check (exact positive whitelist)
    const chk = await getConstraintDef("webhook_events", "webhook_events_status_check");
    const expectedStatuses = ['received', 'processing', 'processed', 'failed', 'reconciliation_required', 'failed_logged', 'ignored'];
    if (!chk || chk.contype !== 'c') {
      diagnostics.push("Missing CHECK constraint 'webhook_events_status_check' (migration 002)");
    } else {
      const defUpper = chk.def.toUpperCase();
      // Disallow negative logic, tautologies, or invalid operators
      if (
        defUpper.includes("NOT IN") ||
        defUpper.includes("!=") ||
        defUpper.includes("<>") ||
        defUpper.includes("OR TRUE") ||
        defUpper.includes("OR 1=1") ||
        defUpper.includes("OR (TRUE)") ||
        !chk.def.includes("status")
      ) {
        diagnostics.push("CHECK constraint 'webhook_events_status_check' contains invalid negative logic or tautology (migration 002)");
      }

      // Extract all single-quoted strings from constraint definition to ensure exact status set without extra values
      const matches = Array.from(chk.def.matchAll(/'([^']+)'/g)).map((m) => m[1]);
      const statusSet = new Set(matches);
      const isExact = expectedStatuses.length === statusSet.size && expectedStatuses.every((s) => statusSet.has(s));
      if (!isExact) {
        diagnostics.push(`CHECK constraint 'webhook_events_status_check' must match exact 7 statuses: [${expectedStatuses.join(", ")}]`);
      }
    }

    // Confirm obsolete idx_webhook_events_event_id is ABSENT after migration 002
    const obsoleteIdx = await getIndexInfo("idx_webhook_events_event_id");
    if (obsoleteIdx) {
      diagnostics.push("Obsolete index 'idx_webhook_events_event_id' is present; it should have been dropped by migration 002");
    }

    // Exact index idx_webhook_events_status
    const idxSt = await getIndexInfo("idx_webhook_events_status");
    if (
      !idxSt ||
      idxSt.tablename !== "webhook_events" ||
      idxSt.is_unique ||
      idxSt.access_method !== "btree" ||
      idxSt.predicate ||
      idxSt.has_expressions ||
      (idxSt.key_columns && (idxSt.key_columns.length !== 1 || idxSt.key_columns[0] !== "status"))
    ) {
      diagnostics.push("Missing exact non-unique, non-partial B-tree index 'idx_webhook_events_status' on webhook_events(status) (migration 001)");
    }
  }

  // --- 003: booking_status, payment_status, refund_status, refunds, hotels.free_cancellation_hours ---
  const bookingStatuses = await enumValues("booking_status");
  const expectedBookingStatus = ['payment_pending', 'confirmed', 'cancelled', 'payment_failed', 'completed', 'cancellation_pending'];
  if (
    bookingStatuses.length !== expectedBookingStatus.length ||
    !bookingStatuses.every((val, idx) => val === expectedBookingStatus[idx])
  ) {
    diagnostics.push(`Enum 'booking_status' must match exact ordered set ['${expectedBookingStatus.join("', '")}'] (migration 003)`);
  }

  const paymentStatuses = await enumValues("payment_status");
  const expectedPaymentStatus = ['pending', 'captured', 'failed', 'refunded', 'refund_pending'];
  if (
    paymentStatuses.length !== expectedPaymentStatus.length ||
    !paymentStatuses.every((val, idx) => val === expectedPaymentStatus[idx])
  ) {
    diagnostics.push(`Enum 'payment_status' must match exact ordered set ['${expectedPaymentStatus.join("', '")}'] (migration 003)`);
  }

  const refundStatusLabels = await enumValues("refund_status");
  const expectedRefundStatus = ["pending", "processed", "failed"];
  if (
    refundStatusLabels.length !== expectedRefundStatus.length ||
    !refundStatusLabels.every((val, idx) => val === expectedRefundStatus[idx])
  ) {
    diagnostics.push(`Enum 'refund_status' must match exact ordered set ['pending', 'processed', 'failed'] (migration 003)`);
  }

  const freeCancelCol = await columnDetails("hotels", "free_cancellation_hours");
  if (!freeCancelCol) {
    diagnostics.push("Missing column 'hotels.free_cancellation_hours' (migration 003)");
  } else {
    if (freeCancelCol.data_type !== "integer") diagnostics.push("Column 'hotels.free_cancellation_hours' must be type integer");
    if (freeCancelCol.is_nullable !== "NO") diagnostics.push("Column 'hotels.free_cancellation_hours' must be NOT NULL");
    if (normalizeDefault(freeCancelCol.column_default) !== "24") {
      diagnostics.push(`Column 'hotels.free_cancellation_hours' default must be exactly '24', got '${normalizeDefault(freeCancelCol.column_default)}'`);
    }
  }

  if (!(await tableExists("refunds"))) {
    diagnostics.push("Missing table 'refunds' (migration 003)");
  } else {
    // PK
    const refPk = await getConstraintDef("refunds", "refunds_pkey");
    if (!refPk || refPk.contype !== 'p' || refPk.def.trim() !== "PRIMARY KEY (id)") {
      diagnostics.push("Missing exact primary key 'PRIMARY KEY (id)' on 'refunds' (migration 003)");
    }

    const refCols = [
      { name: "id", type: "uuid", nullable: "NO", defaultVal: "gen_random_uuid()" },
      { name: "booking_id", type: "uuid", nullable: "NO" },
      { name: "payment_id", type: "uuid", nullable: "NO" },
      { name: "user_id", type: "uuid", nullable: "NO" },
      { name: "razorpay_refund_id", type: "text", nullable: "YES" },
      { name: "razorpay_payment_id", type: "text", nullable: "NO" },
      { name: "idempotency_key", type: "text", nullable: "NO" },
      { name: "amount", type: "integer", nullable: "NO" },
      { name: "currency", type: "character", len: 3, nullable: "NO", defaultVal: "INR" },
      { name: "status", type: "USER-DEFINED", udtName: "refund_status", nullable: "NO", defaultVal: "pending" },
      { name: "reason", type: "text", nullable: "NO" },
      { name: "error_message", type: "text", nullable: "YES" },
      { name: "created_at", type: "timestamp with time zone", nullable: "NO", defaultVal: "now()" },
      { name: "updated_at", type: "timestamp with time zone", nullable: "NO", defaultVal: "now()" },
      { name: "attempt_count", type: "integer", nullable: "NO", defaultVal: "0" },
      { name: "last_attempt_at", type: "timestamp with time zone", nullable: "YES" },
      { name: "next_attempt_at", type: "timestamp with time zone", nullable: "YES", defaultVal: "now()" },
      { name: "lease_until", type: "timestamp with time zone", nullable: "YES" },
      { name: "leased_by", type: "character varying", len: 255, nullable: "YES" }
    ];

    for (const c of refCols) {
      const col = await columnDetails("refunds", c.name);
      if (!col) {
        diagnostics.push(`Missing column 'refunds.${c.name}'`);
      } else {
        if (col.data_type !== c.type) diagnostics.push(`Column 'refunds.${c.name}' type mismatch (expected ${c.type}, got ${col.data_type})`);
        if (c.len && col.character_maximum_length !== c.len) diagnostics.push(`Column 'refunds.${c.name}' character length mismatch (expected ${c.len}, got ${col.character_maximum_length})`);
        if (col.is_nullable !== c.nullable) diagnostics.push(`Column 'refunds.${c.name}' nullability mismatch (expected ${c.nullable}, got ${col.is_nullable})`);
        if (c.defaultVal && normalizeDefault(col.column_default) !== c.defaultVal) {
          diagnostics.push(`Column 'refunds.${c.name}' default mismatch (expected '${c.defaultVal}', got '${normalizeDefault(col.column_default)}')`);
        }
      }
    }

    // CHECK constraint: semantically exactly amount > 0
    const amtChk = await getConstraintDef("refunds", "refunds_amount_check");
    if (!amtChk || amtChk.contype !== 'c') {
      diagnostics.push("Missing CHECK constraint 'refunds_amount_check' (migration 003)");
    } else {
      let normalizedAmtChk = amtChk.def.trim();
      if (normalizedAmtChk.toUpperCase().startsWith("CHECK")) {
        normalizedAmtChk = normalizedAmtChk.slice(5).trim();
      }
      while (normalizedAmtChk.startsWith("(") && normalizedAmtChk.endsWith(")") && normalizedAmtChk.length > 2) {
        normalizedAmtChk = normalizedAmtChk.slice(1, -1).trim();
      }
      if (normalizedAmtChk.replace(/\s+/g, "") !== "amount>0") {
        diagnostics.push(`CHECK constraint 'refunds_amount_check' must enforce exactly 'amount > 0', got '${amtChk.def}' (migration 003)`);
      }
    }

    // Single-column unconditional unique constraints
    if (!(await singleColumnUnique("refunds", "booking_id"))) {
      diagnostics.push("Missing unconditional single-column unique constraint on refunds(booking_id) (migration 003)");
    }
    if (!(await singleColumnUnique("refunds", "idempotency_key"))) {
      diagnostics.push("Missing unconditional single-column unique constraint on refunds(idempotency_key) (migration 003)");
    }
    if (!(await singleColumnUnique("refunds", "razorpay_refund_id"))) {
      diagnostics.push("Missing unconditional single-column unique constraint on refunds(razorpay_refund_id) (migration 003)");
    }

    // Foreign keys with exact ON DELETE actions
    const bkFk = await getConstraintDef("refunds", "refunds_booking_id_fkey");
    if (!bkFk || bkFk.contype !== 'f' || !bkFk.def.includes("REFERENCES bookings(id)") || !bkFk.def.includes("ON DELETE CASCADE")) {
      diagnostics.push("Foreign key refunds(booking_id) -> bookings(id) ON DELETE CASCADE missing or invalid (migration 003)");
    }

    const payFk = await getConstraintDef("refunds", "refunds_payment_id_fkey");
    if (!payFk || payFk.contype !== 'f' || !payFk.def.includes("REFERENCES payments(id)") || !payFk.def.includes("ON DELETE CASCADE")) {
      diagnostics.push("Foreign key refunds(payment_id) -> payments(id) ON DELETE CASCADE missing or invalid (migration 003)");
    }

    const userFk = await getConstraintDef("refunds", "refunds_user_id_fkey");
    if (!userFk || userFk.contype !== 'f' || !userFk.def.includes("REFERENCES users(id)")) {
      diagnostics.push("Foreign key refunds(user_id) -> users(id) missing or invalid (migration 003)");
    }

    // Exact non-unique, non-partial B-tree indexes
    const idxBk = await getIndexInfo("idx_refunds_booking");
    if (
      !idxBk ||
      idxBk.tablename !== "refunds" ||
      idxBk.is_unique ||
      idxBk.access_method !== "btree" ||
      idxBk.predicate ||
      idxBk.has_expressions ||
      (idxBk.key_columns && (idxBk.key_columns.length !== 1 || idxBk.key_columns[0] !== "booking_id"))
    ) {
      diagnostics.push("Missing exact non-unique B-tree index 'idx_refunds_booking' on refunds(booking_id) (migration 003)");
    }

    const idxIdemp = await getIndexInfo("idx_refunds_idempotency");
    if (
      !idxIdemp ||
      idxIdemp.tablename !== "refunds" ||
      idxIdemp.is_unique ||
      idxIdemp.access_method !== "btree" ||
      idxIdemp.predicate ||
      idxIdemp.has_expressions ||
      (idxIdemp.key_columns && (idxIdemp.key_columns.length !== 1 || idxIdemp.key_columns[0] !== "idempotency_key"))
    ) {
      diagnostics.push("Missing exact non-unique B-tree index 'idx_refunds_idempotency' on refunds(idempotency_key) (migration 003)");
    }

    const idxSt = await getIndexInfo("idx_refunds_status");
    if (
      !idxSt ||
      idxSt.tablename !== "refunds" ||
      idxSt.is_unique ||
      idxSt.access_method !== "btree" ||
      idxSt.predicate ||
      idxSt.has_expressions ||
      (idxSt.key_columns && (idxSt.key_columns.length !== 1 || idxSt.key_columns[0] !== "status"))
    ) {
      diagnostics.push("Missing exact non-unique B-tree index 'idx_refunds_status' on refunds(status) (migration 003)");
    }

    // --- 004: idx_refunds_lease_claim on (status, lease_until, next_attempt_at) without partial predicate ---
    const leaseClaimIdx = await getIndexInfo("idx_refunds_lease_claim");
    const expectedLeaseCols = ["status", "lease_until", "next_attempt_at"];
    if (
      !leaseClaimIdx ||
      leaseClaimIdx.tablename !== "refunds" ||
      leaseClaimIdx.is_unique ||
      leaseClaimIdx.access_method !== "btree" ||
      leaseClaimIdx.predicate ||
      leaseClaimIdx.has_expressions ||
      (leaseClaimIdx.key_columns && (
        leaseClaimIdx.key_columns.length !== expectedLeaseCols.length ||
        !leaseClaimIdx.key_columns.every((c, i) => c === expectedLeaseCols[i])
      ))
    ) {
      diagnostics.push("Missing exact non-partial B-tree index 'idx_refunds_lease_claim' on refunds(status, lease_until, next_attempt_at) (migration 004)");
    }
  }

  // --- 005: reviews status default and indexes ---
  const revStCol = await columnDetails("reviews", "status");
  if (!revStCol || normalizeDefault(revStCol.column_default) !== "pending") {
    diagnostics.push("Column 'reviews.status' default must be exactly 'pending' (migration 005)");
  }

  const revUniqueIdx = await getIndexInfo("idx_reviews_booking_id_unique");
  if (
    !revUniqueIdx ||
    revUniqueIdx.tablename !== "reviews" ||
    !revUniqueIdx.is_unique ||
    revUniqueIdx.access_method !== "btree" ||
    !revUniqueIdx.def.includes("(booking_id)") ||
    !revUniqueIdx.def.toLowerCase().includes("booking_id is not null")
  ) {
    diagnostics.push("Missing partial unique index 'idx_reviews_booking_id_unique' ON reviews(booking_id) WHERE booking_id IS NOT NULL (migration 005)");
  }

  const revPaginationIdx = await getIndexInfo("idx_reviews_hotel_approved_pagination");
  if (
    !revPaginationIdx ||
    revPaginationIdx.tablename !== "reviews" ||
    revPaginationIdx.is_unique ||
    revPaginationIdx.access_method !== "btree" ||
    !revPaginationIdx.def.includes("(hotel_id, created_at DESC, id DESC)") ||
    !revPaginationIdx.def.toLowerCase().includes("status = 'approved'")
  ) {
    diagnostics.push("Missing composite index 'idx_reviews_hotel_approved_pagination' ON reviews(hotel_id, created_at DESC, id DESC) WHERE status = 'approved' (migration 005)");
  }

  const revUserIdx = await getIndexInfo("idx_reviews_user_hotel");
  if (
    !revUserIdx ||
    revUserIdx.tablename !== "reviews" ||
    revUserIdx.is_unique ||
    revUserIdx.access_method !== "btree" ||
    !revUserIdx.def.includes("(user_id, hotel_id, created_at DESC)") ||
    revUserIdx.predicate
  ) {
    diagnostics.push("Missing index 'idx_reviews_user_hotel' on reviews(user_id, hotel_id, created_at DESC) (migration 005)");
  }

  return {
    isValid: diagnostics.length === 0,
    diagnostics
  };
}

/**
 * Migration runner engine.
 */
export async function runMigrations(options = {}) {
  const {
    dryRun = false,
    baseline = false,
    confirm = false,
    migrationsDir = DEFAULT_MIGRATIONS_DIR,
    client: injectedClient = null,
    logger = console
  } = options;

  const client = injectedClient || new pg.Client({
    connectionString: env.DATABASE_URL,
    ssl: getPostgresSslConfig()
  });

  if (!injectedClient) {
    await client.connect();
  }

  let lockAcquired = false;

  try {
    await client.query("SELECT pg_advisory_lock(hashtext($1))", [LOCK_KEY_NAME]);
    lockAcquired = true;

    const allFiles = fs.readdirSync(migrationsDir)
      .filter((f) => f.endsWith(".sql") && !f.endsWith("_down.sql"))
      .sort();

    const trackingTableRes = await client.query(
      "SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'schema_migrations'"
    );
    const trackingTableExists = trackingTableRes.rowCount > 0;

    let appliedRows = [];
    if (trackingTableExists) {
      const res = await client.query("SELECT migration_name, checksum, applied_at FROM schema_migrations ORDER BY id ASC");
      appliedRows = res.rows;
    }

    const appliedMap = new Map(appliedRows.map((r) => [r.migration_name, r]));

    // Validate checksum invariance for already applied migrations
    for (const file of allFiles) {
      if (appliedMap.has(file)) {
        const filePath = path.join(migrationsDir, file);
        const content = fs.readFileSync(filePath, "utf8");
        const currentChecksum = computeChecksum(content);
        const recordedChecksum = appliedMap.get(file).checksum;
        if (currentChecksum !== recordedChecksum) {
          throw new Error(
            `Migration checksum mismatch for '${file}'. Recorded: ${recordedChecksum}, Current: ${currentChecksum}. Migration files are immutable.`
          );
        }
      }
    }

    const pendingFiles = allFiles.filter((f) => !appliedMap.has(f));

    // Refuse normal migration if ANY pending migration's owned artifacts already exist in DB
    if (!baseline && pendingFiles.length > 0) {
      const untrackedArtifacts = await detectUntrackedMigrationArtifacts(client, pendingFiles);
      if (untrackedArtifacts.length > 0) {
        const descriptions = untrackedArtifacts.map((u) => `${u.migration} (${u.artifact.type}: ${u.artifact.name || u.artifact.table})`);
        throw new Error(
          `Untracked schema detected: Database contains artifacts belonging to pending migrations [${descriptions.join(", ")}]. ` +
          `Running 'npm run migrate' could corrupt or duplicate data. Run 'npm run migrate:baseline' to verify and baseline existing migrations.`
        );
      }
    }

    // Baseline mode
    if (baseline) {
      if (dryRun) {
        throw new Error("Cannot combine --baseline and --dry-run.");
      }

      const baselineFiles = allFiles.filter((f) =>
        f.startsWith("001_") || f.startsWith("002_") || f.startsWith("003_") || f.startsWith("004_") || f.startsWith("005_")
      );
      const alreadyTracked = baselineFiles.filter((f) => appliedMap.has(f));

      if (alreadyTracked.length === baselineFiles.length) {
        logger.log("All baseline migrations (001-005) are already recorded in schema_migrations.");
        return { status: "already_baselined", count: 0 };
      }

      const verification = await verifyBaselineStructure(client);
      if (!verification.isValid) {
        logger.error("Baseline verification failed with the following structural mismatches:");
        verification.diagnostics.forEach((d) => logger.error(` - ${d}`));
        throw new Error(`Baseline aborted: Database schema does not match required baseline structure (${verification.diagnostics.length} issues).`);
      }

      if (!confirm) {
        logger.log("Proposed baseline migrations to record:");
        baselineFiles.forEach((f) => logger.log(` [BASELINE] ${f}`));
        logger.log("\nTo confirm and record these baseline migrations, rerun with --confirm flag.");
        return { status: "confirmation_required", proposed: baselineFiles };
      }

      await client.query("BEGIN");
      try {
        await client.query(`
          CREATE TABLE IF NOT EXISTS schema_migrations (
            id SERIAL PRIMARY KEY,
            migration_name VARCHAR(255) UNIQUE NOT NULL,
            checksum VARCHAR(64) NOT NULL,
            applied_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
            execution_time_ms INTEGER NOT NULL
          );
        `);

        let recordedCount = 0;
        for (const file of baselineFiles) {
          if (!appliedMap.has(file)) {
            const filePath = path.join(migrationsDir, file);
            const content = fs.readFileSync(filePath, "utf8");
            const checksum = computeChecksum(content);
            await client.query(
              `INSERT INTO schema_migrations (migration_name, checksum, applied_at, execution_time_ms)
               VALUES ($1, $2, NOW(), 0)`,
              [file, checksum]
            );
            recordedCount++;
            logger.log(`Baselined: ${file}`);
          }
        }
        await client.query("COMMIT");
        logger.log(`Successfully recorded ${recordedCount} baseline migration(s).`);
        return { status: "baselined", count: recordedCount };
      } catch (err) {
        await client.query("ROLLBACK");
        throw err;
      }
    }

    // Normal migrate / Dry-run
    if (dryRun) {
      logger.log(`--- Migration Status (Dry-Run: 0 database writes) ---`);
      logger.log(`Tracking table exists: ${trackingTableExists}`);
      logger.log(`Applied migrations (${appliedRows.length}):`);
      appliedRows.forEach((r) => logger.log(`  ✓ ${r.migration_name} (applied ${r.applied_at})`));
      logger.log(`Pending migrations (${pendingFiles.length}):`);
      pendingFiles.forEach((f) => logger.log(`  ⏳ ${f}`));
      return { status: "dry_run", applied: appliedRows.length, pending: pendingFiles.length, pendingFiles };
    }

    if (pendingFiles.length === 0) {
      logger.log("Database is up to date. No pending migrations.");
      return { status: "up_to_date", count: 0 };
    }

    let appliedCount = 0;
    for (const file of pendingFiles) {
      const filePath = path.join(migrationsDir, file);
      const rawContent = fs.readFileSync(filePath, "utf8");
      const checksum = computeChecksum(rawContent);
      const sanitizedSql = sanitizeMigrationSql(rawContent);

      const startTime = Date.now();
      await client.query("BEGIN");
      try {
        await client.query(`
          CREATE TABLE IF NOT EXISTS schema_migrations (
            id SERIAL PRIMARY KEY,
            migration_name VARCHAR(255) UNIQUE NOT NULL,
            checksum VARCHAR(64) NOT NULL,
            applied_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
            execution_time_ms INTEGER NOT NULL
          );
        `);

        if (sanitizedSql.length > 0) {
          await client.query(sanitizedSql);
        }
        const duration = Date.now() - startTime;
        await client.query(
          `INSERT INTO schema_migrations (migration_name, checksum, applied_at, execution_time_ms)
           VALUES ($1, $2, NOW(), $3)`,
          [file, checksum, duration]
        );
        await client.query("COMMIT");
        logger.log(`Applied: ${file} (${duration}ms)`);
        appliedCount++;
      } catch (migrationErr) {
        await client.query("ROLLBACK");
        logger.error(`Failed to apply migration '${file}': ${migrationErr.message}`);
        throw new Error(`Migration '${file}' failed: ${migrationErr.message}`);
      }
    }

    logger.log(`Successfully applied ${appliedCount} migration(s).`);
    return { status: "completed", count: appliedCount };
  } finally {
    if (lockAcquired) {
      try {
        await client.query("SELECT pg_advisory_unlock(hashtext($1))", [LOCK_KEY_NAME]);
      } catch (unlockErr) {
        logger.warn(`Failed to release advisory lock: ${unlockErr.message}`);
      }
    }
    if (!injectedClient) {
      await client.end();
    }
  }
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const args = process.argv.slice(2);
  const dryRun = args.includes("--dry-run");
  const baseline = args.includes("--baseline");
  const confirm = args.includes("--confirm");

  runMigrations({ dryRun, baseline, confirm })
    .then((result) => {
      if (result.status === "confirmation_required") {
        process.exit(1);
      }
      process.exit(0);
    })
    .catch((err) => {
      console.error("Migration Error:", err.message);
      process.exit(1);
    });
}
