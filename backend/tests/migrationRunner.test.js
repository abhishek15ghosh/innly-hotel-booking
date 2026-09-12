import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import test, { describe } from "node:test";
import { fileURLToPath } from "node:url";
import { getPostgresSslConfig } from "../src/config/db.js";
import {
  computeChecksum,
  detectUntrackedMigrationArtifacts,
  runMigrations,
  sanitizeMigrationSql,
  verifyBaselineStructure
} from "../src/utils/migrate.js";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

function createFakePgClient(initialState = {}) {
  const queries = [];
  const state = {
    tables: new Set(initialState.tables || []),
    columns: new Map(initialState.columns || []),
    indexes: new Map(initialState.indexes || []), // name -> { def, tablename, is_unique, access_method, predicate, total_atts, key_atts, has_expressions, key_columns }
    types: new Map(initialState.types || []), // name -> [labels]
    constraints: new Map(initialState.constraints || []), // "table.conname" -> { def, contype }
    singleColumnUniques: new Set(initialState.singleColumnUniques || []),
    schemaMigrations: new Map(initialState.schemaMigrations || []),
    inTransaction: false,
    lockHeld: false
  };

  const client = {
    state,
    queries,
    query: async (text, params = []) => {
      queries.push({ text, params });
      const q = text.trim();

      if (q.startsWith("SELECT pg_advisory_lock")) {
        state.lockHeld = true;
        return { rowCount: 1, rows: [{ pg_advisory_lock: null }] };
      }

      if (q.startsWith("SELECT pg_advisory_unlock")) {
        state.lockHeld = false;
        return { rowCount: 1, rows: [{ pg_advisory_unlock: true }] };
      }

      if (q.includes("FROM information_schema.tables WHERE table_schema = 'public' AND table_name = $1")) {
        const tableName = params[0];
        const exists = state.tables.has(tableName);
        return { rowCount: exists ? 1 : 0, rows: exists ? [{ "?column?": 1 }] : [] };
      }

      if (q.includes("FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'schema_migrations'")) {
        const exists = state.tables.has("schema_migrations");
        return { rowCount: exists ? 1 : 0, rows: exists ? [{ "?column?": 1 }] : [] };
      }

      if (q.includes("FROM information_schema.columns") && q.includes("table_name = $1 AND column_name = $2")) {
        const tableName = params[0];
        const colName = params[1];
        const key = `${tableName}.${colName}`;
        if (state.columns.has(key)) {
          const colInfo = state.columns.get(key);
          return {
            rowCount: 1,
            rows: [{
              data_type: colInfo.type,
              udt_name: colInfo.udtName || colInfo.type,
              is_nullable: colInfo.nullable || "NO",
              column_default: colInfo.default || null,
              character_maximum_length: colInfo.len || null
            }]
          };
        }
        return { rowCount: 0, rows: [] };
      }

      if (q.includes("FROM pg_indexes WHERE schemaname = 'public' AND indexname = $1")) {
        const indexName = params[0];
        const exists = state.indexes.has(indexName);
        return { rowCount: exists ? 1 : 0, rows: exists ? [{ indexname: indexName }] : [] };
      }

      if (q.includes("i.indnatts = 1") || (q.includes("pg_attribute") && q.includes("indisunique = true"))) {
        const [tableName, colName] = params;
        const key = `${tableName}.${colName}`;
        const isSingleUnique = state.singleColumnUniques.has(key);
        return { rowCount: isSingleUnique ? 1 : 0, rows: isSingleUnique ? [{ "?column?": 1 }] : [] };
      }

      if (q.includes("pg_get_indexdef") || q.includes("FROM pg_index i")) {
        const indexName = params[0];
        if (state.indexes.has(indexName)) {
          const info = state.indexes.get(indexName);
          return {
            rowCount: 1,
            rows: [{
              def: info.def,
              tablename: info.tablename,
              is_unique: info.is_unique || false,
              access_method: info.access_method || "btree",
              predicate: info.predicate || null,
              total_atts: info.total_atts || (info.key_columns ? info.key_columns.length : 1),
              key_atts: info.key_atts || (info.key_columns ? info.key_columns.length : 1),
              has_expressions: info.has_expressions || false,
              key_columns: info.key_columns || []
            }]
          };
        }
        return { rowCount: 0, rows: [] };
      }

      if (q.includes("FROM pg_enum e") && q.includes("enumlabel = $2")) {
        const [typeName, label] = params;
        if (state.types.has(typeName) && state.types.get(typeName).includes(label)) {
          return { rowCount: 1, rows: [{ "?column?": 1 }] };
        }
        return { rowCount: 0, rows: [] };
      }

      if (q.includes("FROM pg_enum") && q.includes("typname = $1")) {
        const typeName = params[0];
        if (state.types.has(typeName)) {
          const labels = state.types.get(typeName);
          return { rowCount: labels.length, rows: labels.map((l) => ({ enumlabel: l })) };
        }
        return { rowCount: 0, rows: [] };
      }

      if (q.includes("FROM pg_type WHERE typname = $1")) {
        const typeName = params[0];
        const exists = state.types.has(typeName);
        return { rowCount: exists ? 1 : 0, rows: exists ? [{ typname: typeName }] : [] };
      }

      if (q.includes("pg_get_constraintdef") || (q.includes("FROM pg_constraint c") && q.includes("c.conname = $2"))) {
        const [tableName, constraintName] = params;
        const key = `${tableName}.${constraintName}`;
        if (state.constraints.has(key)) {
          const cInfo = state.constraints.get(key);
          return { rowCount: 1, rows: [{ def: cInfo.def, contype: cInfo.contype }] };
        }
        return { rowCount: 0, rows: [] };
      }

      if (q.includes("FROM schema_migrations ORDER BY id ASC")) {
        const rows = Array.from(state.schemaMigrations.values());
        return { rowCount: rows.length, rows };
      }

      if (q.startsWith("CREATE TABLE IF NOT EXISTS schema_migrations")) {
        state.tables.add("schema_migrations");
        return { rowCount: 0, rows: [] };
      }

      if (q === "BEGIN") {
        state.inTransaction = true;
        return { rowCount: 0, rows: [] };
      }

      if (q === "COMMIT") {
        state.inTransaction = false;
        return { rowCount: 0, rows: [] };
      }

      if (q === "ROLLBACK") {
        state.inTransaction = false;
        if (state.tables.has("schema_migrations") && state.schemaMigrations.size === 0) {
          state.tables.delete("schema_migrations");
        }
        return { rowCount: 0, rows: [] };
      }

      if (q.startsWith("INSERT INTO schema_migrations")) {
        const [name, checksum, duration] = params;
        state.schemaMigrations.set(name, {
          migration_name: name,
          checksum,
          applied_at: new Date().toISOString(),
          execution_time_ms: duration || 0
        });
        return { rowCount: 1, rows: [] };
      }

      return { rowCount: 0, rows: [] };
    }
  };

  return client;
}

function createCompleteAuthoritativeBaselineState() {
  return {
    tables: ["webhook_events", "refunds", "hotels", "reviews"],
    columns: [
      ["webhook_events.id", { type: "uuid", nullable: "NO", default: "gen_random_uuid()" }],
      ["webhook_events.event_id", { type: "character varying", len: 255, nullable: "NO" }],
      ["webhook_events.event_type", { type: "character varying", len: 100, nullable: "NO" }],
      ["webhook_events.status", { type: "character varying", len: 50, nullable: "NO", default: "'received'::character varying" }],
      ["webhook_events.failure_reason", { type: "text", nullable: "YES" }],
      ["webhook_events.created_at", { type: "timestamp with time zone", nullable: "NO", default: "now()" }],
      ["webhook_events.processed_at", { type: "timestamp with time zone", nullable: "YES" }],
      ["refunds.id", { type: "uuid", nullable: "NO", default: "gen_random_uuid()" }],
      ["refunds.booking_id", { type: "uuid", nullable: "NO" }],
      ["refunds.payment_id", { type: "uuid", nullable: "NO" }],
      ["refunds.user_id", { type: "uuid", nullable: "NO" }],
      ["refunds.razorpay_refund_id", { type: "text", nullable: "YES" }],
      ["refunds.razorpay_payment_id", { type: "text", nullable: "NO" }],
      ["refunds.idempotency_key", { type: "text", nullable: "NO" }],
      ["refunds.amount", { type: "integer", nullable: "NO" }],
      ["refunds.currency", { type: "character", len: 3, nullable: "NO", default: "'INR'::bpchar" }],
      ["refunds.status", { type: "USER-DEFINED", udtName: "refund_status", nullable: "NO", default: "'pending'::refund_status" }],
      ["refunds.reason", { type: "text", nullable: "NO" }],
      ["refunds.error_message", { type: "text", nullable: "YES" }],
      ["refunds.created_at", { type: "timestamp with time zone", nullable: "NO", default: "now()" }],
      ["refunds.updated_at", { type: "timestamp with time zone", nullable: "NO", default: "now()" }],
      ["refunds.attempt_count", { type: "integer", nullable: "NO", default: "0" }],
      ["refunds.last_attempt_at", { type: "timestamp with time zone", nullable: "YES" }],
      ["refunds.next_attempt_at", { type: "timestamp with time zone", nullable: "YES", default: "now()" }],
      ["refunds.lease_until", { type: "timestamp with time zone", nullable: "YES" }],
      ["refunds.leased_by", { type: "character varying", len: 255, nullable: "YES" }],
      ["hotels.free_cancellation_hours", { type: "integer", nullable: "NO", default: "24" }],
      ["reviews.status", { type: "USER-DEFINED", udtName: "review_status", nullable: "NO", default: "'pending'::review_status" }]
    ],
    indexes: [
      ["idx_webhook_events_status", { tablename: "webhook_events", is_unique: false, access_method: "btree", key_columns: ["status"], def: "CREATE INDEX idx_webhook_events_status ON public.webhook_events USING btree (status)" }],
      ["idx_refunds_booking", { tablename: "refunds", is_unique: false, access_method: "btree", key_columns: ["booking_id"], def: "CREATE INDEX idx_refunds_booking ON public.refunds USING btree (booking_id)" }],
      ["idx_refunds_idempotency", { tablename: "refunds", is_unique: false, access_method: "btree", key_columns: ["idempotency_key"], def: "CREATE INDEX idx_refunds_idempotency ON public.refunds USING btree (idempotency_key)" }],
      ["idx_refunds_status", { tablename: "refunds", is_unique: false, access_method: "btree", key_columns: ["status"], def: "CREATE INDEX idx_refunds_status ON public.refunds USING btree (status)" }],
      ["idx_refunds_lease_claim", { tablename: "refunds", is_unique: false, access_method: "btree", key_columns: ["status", "lease_until", "next_attempt_at"], def: "CREATE INDEX idx_refunds_lease_claim ON public.refunds USING btree (status, lease_until, next_attempt_at)" }],
      ["idx_reviews_booking_id_unique", { tablename: "reviews", is_unique: true, access_method: "btree", key_columns: ["booking_id"], def: "CREATE UNIQUE INDEX idx_reviews_booking_id_unique ON public.reviews USING btree (booking_id) WHERE (booking_id IS NOT NULL)", predicate: "(booking_id IS NOT NULL)" }],
      ["idx_reviews_hotel_approved_pagination", { tablename: "reviews", is_unique: false, access_method: "btree", key_columns: ["hotel_id", "created_at", "id"], def: "CREATE INDEX idx_reviews_hotel_approved_pagination ON public.reviews USING btree (hotel_id, created_at DESC, id DESC) WHERE (status = 'approved'::review_status)", predicate: "(status = 'approved'::review_status)" }],
      ["idx_reviews_user_hotel", { tablename: "reviews", is_unique: false, access_method: "btree", key_columns: ["user_id", "hotel_id", "created_at"], def: "CREATE INDEX idx_reviews_user_hotel ON public.reviews USING btree (user_id, hotel_id, created_at DESC)" }]
    ],
    types: [
      ["booking_status", ["payment_pending", "confirmed", "cancelled", "payment_failed", "completed", "cancellation_pending"]],
      ["payment_status", ["pending", "captured", "failed", "refunded", "refund_pending"]],
      ["refund_status", ["pending", "processed", "failed"]]
    ],
    constraints: [
      ["webhook_events.webhook_events_pkey", { contype: 'p', def: "PRIMARY KEY (id)" }],
      ["webhook_events.webhook_events_status_check", {
        contype: 'c',
        def: "CHECK ((status)::text = ANY ((ARRAY['received'::character varying, 'processing'::character varying, 'processed'::character varying, 'failed'::character varying, 'reconciliation_required'::character varying, 'failed_logged'::character varying, 'ignored'::character varying])::text[]))"
      }],
      ["refunds.refunds_pkey", { contype: 'p', def: "PRIMARY KEY (id)" }],
      ["refunds.refunds_amount_check", { contype: 'c', def: "CHECK (amount > 0)" }],
      ["refunds.refunds_booking_id_fkey", { contype: 'f', def: "FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE CASCADE" }],
      ["refunds.refunds_payment_id_fkey", { contype: 'f', def: "FOREIGN KEY (payment_id) REFERENCES payments(id) ON DELETE CASCADE" }],
      ["refunds.refunds_user_id_fkey", { contype: 'f', def: "FOREIGN KEY (user_id) REFERENCES users(id)" }]
    ],
    singleColumnUniques: [
      "webhook_events.event_id",
      "refunds.booking_id",
      "refunds.idempotency_key",
      "refunds.razorpay_refund_id"
    ]
  };
}

describe("PostgreSQL Migration Runner & Adversarial Structured Verification Unit Tests", () => {
  test("1. computeChecksum produces deterministic SHA-256 hex string", () => {
    const hash1 = computeChecksum("CREATE TABLE test (id INT);");
    const hash2 = computeChecksum("CREATE TABLE test (id INT);");
    assert.equal(hash1, hash2);
    assert.equal(hash1.length, 64);
  });

  test("2. sanitizeMigrationSql strips outer BEGIN/COMMIT ignoring comments, strings, bare and tagged dollar quotes", () => {
    const sqlComplex = `
      -- Top line comment with BEGIN
      BEGIN;
      CREATE TABLE sample (id UUID);
      /* Block comment with COMMIT */
      INSERT INTO logs (msg) VALUES ('BEGIN and COMMIT inside string');
      DO $$
      BEGIN
        PERFORM 1;
      END $$;
      CREATE FUNCTION fn() RETURNS void AS $tag$
      BEGIN
        PERFORM 2;
      END $tag$ LANGUAGE plpgsql;
      COMMIT;
      -- Bottom comment
    `;

    const cleaned = sanitizeMigrationSql(sqlComplex);
    assert.match(cleaned, /CREATE TABLE sample/);
    assert.match(cleaned, /'BEGIN and COMMIT inside string'/);
    assert.match(cleaned, /DO \$\$/);
    assert.match(cleaned, /\$tag\$/);
    assert.ok(!cleaned.startsWith("BEGIN;"));
    assert.ok(!cleaned.endsWith("COMMIT;"));
  });

  test("3. verifyBaselineStructure passes on complete authoritative baseline metadata", async () => {
    const fullState = createCompleteAuthoritativeBaselineState();
    const client = createFakePgClient(fullState);
    const result = await verifyBaselineStructure(client);

    assert.equal(result.isValid, true);
    assert.equal(result.diagnostics.length, 0);
  });

  test("4. verifyBaselineStructure rejects wrong primary key column (e.g. PRIMARY KEY (event_id))", async () => {
    const invalidState = createCompleteAuthoritativeBaselineState();
    invalidState.constraints = invalidState.constraints.map(([k, v]) => {
      if (k === "webhook_events.webhook_events_pkey") {
        return [k, { contype: 'p', def: "PRIMARY KEY (event_id)" }];
      }
      return [k, v];
    });

    const client = createFakePgClient(invalidState);
    const result = await verifyBaselineStructure(client);

    assert.equal(result.isValid, false);
    assert.ok(result.diagnostics.some((d) => d.includes("PRIMARY KEY (id)")));
  });

  test("5. verifyBaselineStructure rejects missing webhook_events.failure_reason column", async () => {
    const invalidState = createCompleteAuthoritativeBaselineState();
    invalidState.columns = invalidState.columns.filter(([k]) => k !== "webhook_events.failure_reason");

    const client = createFakePgClient(invalidState);
    const result = await verifyBaselineStructure(client);

    assert.equal(result.isValid, false);
    assert.ok(result.diagnostics.some((d) => d.includes("Missing column 'webhook_events.failure_reason'")));
  });

  test("6. verifyBaselineStructure rejects altered default 240 for free_cancellation_hours", async () => {
    const invalidState = createCompleteAuthoritativeBaselineState();
    invalidState.columns = invalidState.columns.map(([k, v]) => {
      if (k === "hotels.free_cancellation_hours") {
        return [k, { ...v, default: "240" }];
      }
      return [k, v];
    });

    const client = createFakePgClient(invalidState);
    const result = await verifyBaselineStructure(client);

    assert.equal(result.isValid, false);
    assert.ok(result.diagnostics.some((d) => d.includes("default must be exactly '24'")));
  });

  test("7. verifyBaselineStructure rejects altered default 10 for refunds.attempt_count", async () => {
    const invalidState = createCompleteAuthoritativeBaselineState();
    invalidState.columns = invalidState.columns.map(([k, v]) => {
      if (k === "refunds.attempt_count") {
        return [k, { ...v, default: "10" }];
      }
      return [k, v];
    });

    const client = createFakePgClient(invalidState);
    const result = await verifyBaselineStructure(client);

    assert.equal(result.isValid, false);
    assert.ok(result.diagnostics.some((d) => d.includes("attempt_count' default mismatch (expected '0'")));
  });

  test("8. verifyBaselineStructure rejects webhook status check with NOT IN, OR TRUE, or extra unapproved status value", async () => {
    // 8a. Extra unapproved status
    const extraState = createCompleteAuthoritativeBaselineState();
    extraState.constraints = extraState.constraints.map(([k, v]) => {
      if (k === "webhook_events.webhook_events_status_check") {
        return [k, {
          contype: 'c',
          def: "CHECK ((status)::text = ANY ((ARRAY['received'::character varying, 'processing'::character varying, 'processed'::character varying, 'failed'::character varying, 'reconciliation_required'::character varying, 'failed_logged'::character varying, 'ignored'::character varying, 'custom_extra'::character varying])::text[]))"
        }];
      }
      return [k, v];
    });
    const clientExtra = createFakePgClient(extraState);
    const resultExtra = await verifyBaselineStructure(clientExtra);
    assert.equal(resultExtra.isValid, false);
    assert.ok(resultExtra.diagnostics.some((d) => d.includes("must match exact 7 statuses")));

    // 8b. NOT IN negative logic
    const notInState = createCompleteAuthoritativeBaselineState();
    notInState.constraints = notInState.constraints.map(([k, v]) => {
      if (k === "webhook_events.webhook_events_status_check") {
        return [k, { contype: 'c', def: "CHECK (status NOT IN ('invalid1', 'invalid2'))" }];
      }
      return [k, v];
    });
    const clientNotIn = createFakePgClient(notInState);
    const resultNotIn = await verifyBaselineStructure(clientNotIn);
    assert.equal(resultNotIn.isValid, false);
    assert.ok(resultNotIn.diagnostics.some((d) => d.includes("invalid negative logic or tautology") || d.includes("must match exact 7 statuses")));

    // 8c. OR TRUE tautology
    const orTrueState = createCompleteAuthoritativeBaselineState();
    orTrueState.constraints = orTrueState.constraints.map(([k, v]) => {
      if (k === "webhook_events.webhook_events_status_check") {
        return [k, { contype: 'c', def: "CHECK (status IN ('received', 'processing', 'processed', 'failed', 'reconciliation_required', 'failed_logged', 'ignored') OR TRUE)" }];
      }
      return [k, v];
    });
    const clientOrTrue = createFakePgClient(orTrueState);
    const resultOrTrue = await verifyBaselineStructure(clientOrTrue);
    assert.equal(resultOrTrue.isValid, false);
    assert.ok(resultOrTrue.diagnostics.some((d) => d.includes("invalid negative logic or tautology")));
  });

  test("9. verifyBaselineStructure rejects refunds_amount_check with amount >= 0 or amount > 0 OR TRUE", async () => {
    const invalidAmtState = createCompleteAuthoritativeBaselineState();
    invalidAmtState.constraints = invalidAmtState.constraints.map(([k, v]) => {
      if (k === "refunds.refunds_amount_check") {
        return [k, { contype: 'c', def: "CHECK (amount > 0 OR TRUE)" }];
      }
      return [k, v];
    });
    const client = createFakePgClient(invalidAmtState);
    const result = await verifyBaselineStructure(client);
    assert.equal(result.isValid, false);
    assert.ok(result.diagnostics.some((d) => d.includes("must enforce exactly 'amount > 0'")));
  });

  test("10. verifyBaselineStructure rejects reordered, extra, or missing enum values in booking_status and payment_status", async () => {
    // 10a. Reordered booking_status
    const reorderedBooking = createCompleteAuthoritativeBaselineState();
    reorderedBooking.types = reorderedBooking.types.map(([k, v]) => {
      if (k === "booking_status") {
        return [k, ["confirmed", "payment_pending", "cancelled", "payment_failed", "completed", "cancellation_pending"]];
      }
      return [k, v];
    });
    const clientBooking = createFakePgClient(reorderedBooking);
    const resBooking = await verifyBaselineStructure(clientBooking);
    assert.equal(resBooking.isValid, false);
    assert.ok(resBooking.diagnostics.some((d) => d.includes("Enum 'booking_status' must match exact ordered set")));

    // 10b. Missing payment_status value
    const missingPayment = createCompleteAuthoritativeBaselineState();
    missingPayment.types = missingPayment.types.map(([k, v]) => {
      if (k === "payment_status") {
        return [k, ["pending", "captured", "failed", "refunded"]]; // missing refund_pending
      }
      return [k, v];
    });
    const clientPayment = createFakePgClient(missingPayment);
    const resPayment = await verifyBaselineStructure(clientPayment);
    assert.equal(resPayment.isValid, false);
    assert.ok(resPayment.diagnostics.some((d) => d.includes("Enum 'payment_status' must match exact ordered set")));
  });

  test("11. verifyBaselineStructure rejects partial unique index or missing single-column unconditional uniqueness", async () => {
    const invalidUniqState = createCompleteAuthoritativeBaselineState();
    invalidUniqState.singleColumnUniques = invalidUniqState.singleColumnUniques.filter((k) => k !== "refunds.booking_id");

    const client = createFakePgClient(invalidUniqState);
    const result = await verifyBaselineStructure(client);

    assert.equal(result.isValid, false);
    assert.ok(result.diagnostics.some((d) => d.includes("Missing unconditional single-column unique constraint on refunds(booking_id)")));
  });

  test("12. verifyBaselineStructure rejects index with wrong access method, unexpected predicate, or included columns", async () => {
    // 12a. Hash access method instead of btree
    const hashIdxState = createCompleteAuthoritativeBaselineState();
    hashIdxState.indexes = hashIdxState.indexes.map(([k, v]) => {
      if (k === "idx_refunds_status") {
        return [k, { ...v, access_method: "hash" }];
      }
      return [k, v];
    });
    const clientHash = createFakePgClient(hashIdxState);
    const resHash = await verifyBaselineStructure(clientHash);
    assert.equal(resHash.isValid, false);
    assert.ok(resHash.diagnostics.some((d) => d.includes("idx_refunds_status")));

    // 12b. Unexpected predicate on non-partial index
    const predIdxState = createCompleteAuthoritativeBaselineState();
    predIdxState.indexes = predIdxState.indexes.map(([k, v]) => {
      if (k === "idx_refunds_booking") {
        return [k, { ...v, predicate: "(booking_id IS NOT NULL)" }];
      }
      return [k, v];
    });
    const clientPred = createFakePgClient(predIdxState);
    const resPred = await verifyBaselineStructure(clientPred);
    assert.equal(resPred.isValid, false);
    assert.ok(resPred.diagnostics.some((d) => d.includes("idx_refunds_booking")));
  });

  test("13. verifyBaselineStructure rejects obsolete idx_webhook_events_event_id when present", async () => {
    const invalidState = createCompleteAuthoritativeBaselineState();
    invalidState.indexes.push([
      "idx_webhook_events_event_id",
      { tablename: "webhook_events", is_unique: false, def: "CREATE INDEX idx_webhook_events_event_id ON public.webhook_events (event_id)" }
    ]);

    const client = createFakePgClient(invalidState);
    const result = await verifyBaselineStructure(client);

    assert.equal(result.isValid, false);
    assert.ok(result.diagnostics.some((d) => d.includes("Obsolete index 'idx_webhook_events_event_id' is present")));
  });

  test("14. Normal migrate refuses execution if untracked objects exist even when schema_migrations is partially populated", async () => {
    const client = createFakePgClient({
      tables: ["schema_migrations", "webhook_events", "refunds"],
      schemaMigrations: [
        ["001_create_webhook_events.sql", {
          migration_name: "001_create_webhook_events.sql",
          checksum: computeChecksum(fs.readFileSync(path.resolve(__dirname, "../src/migrations/001_create_webhook_events.sql"), "utf8")),
          applied_at: new Date().toISOString(),
          execution_time_ms: 5
        }],
        ["002_harden_webhook_events.sql", {
          migration_name: "002_harden_webhook_events.sql",
          checksum: computeChecksum(fs.readFileSync(path.resolve(__dirname, "../src/migrations/002_harden_webhook_events.sql"), "utf8")),
          applied_at: new Date().toISOString(),
          execution_time_ms: 5
        }]
      ]
    });

    await assert.rejects(
      async () => {
        await runMigrations({
          dryRun: false,
          baseline: false,
          client,
          logger: { log: () => {}, error: () => {}, warn: () => {} }
        });
      },
      /Untracked schema detected: Database contains artifacts belonging to pending migrations/
    );
  });

  test("15. Production PostgreSQL TLS throws error when rejectUnauthorized is disabled or unverified", () => {
    assert.throws(
      () => {
        getPostgresSslConfig("production", { DATABASE_SSL_REJECT_UNAUTHORIZED: "false" });
      },
      /Insecure PostgreSQL TLS configuration rejected in production/
    );

    assert.throws(
      () => {
        getPostgresSslConfig("production", { DATABASE_SSL_ALLOW_INSECURE: "true" });
      },
      /Insecure PostgreSQL TLS configuration rejected in production/
    );

    const validProdConfig = getPostgresSslConfig("production", { DATABASE_SSL_CA: "---CERT---" });
    assert.equal(validProdConfig.rejectUnauthorized, true);
    assert.equal(validProdConfig.ca, "---CERT---");
  });

  test("16. Baseline with --confirm records 001-005 in schema_migrations within atomic transaction", async () => {
    const fullState = createCompleteAuthoritativeBaselineState();
    const client = createFakePgClient(fullState);
    const result = await runMigrations({
      baseline: true,
      confirm: true,
      client,
      logger: { log: () => {}, error: () => {}, warn: () => {} }
    });

    assert.equal(result.status, "baselined");
    assert.equal(result.count, 5);
    assert.equal(client.state.schemaMigrations.size, 5);
  });
});
