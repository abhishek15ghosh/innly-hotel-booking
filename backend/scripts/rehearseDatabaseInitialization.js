import { spawnSync } from "child_process";
import crypto from "crypto";
import dotenv from "dotenv";
import path from "path";
import { fileURLToPath } from "url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const backendRoot = path.resolve(__dirname, "..");

export function buildDisposableDatabaseUrl(originalDbUrl, disposableDbName) {
  if (!originalDbUrl) {
    throw new Error("DATABASE_URL is missing or empty.");
  }
  const u = new URL(originalDbUrl);
  const originalDbName = u.pathname.slice(1);

  if (disposableDbName === originalDbName) {
    throw new Error("Disposable database name cannot match the configured active database name.");
  }

  u.pathname = `/${disposableDbName}`;
  return {
    disposableDbUrl: u.toString(),
    originalDbName,
    host: u.hostname,
    port: u.port || "5432",
    username: u.username ? decodeURIComponent(u.username) : undefined,
    password: u.password ? decodeURIComponent(u.password) : undefined
  };
}

export function executeRehearsal(deps = {}) {
  const {
    dbUrl = process.env.DATABASE_URL,
    execFn = spawnSync,
    logger = console
  } = deps;

  if (!dbUrl) {
    throw new Error("DATABASE_URL is not set.");
  }

  const uniqueSuffix = `${Date.now()}_${crypto.randomBytes(4).toString("hex")}`;
  const disposableDbName = `innly_rehearsal_${uniqueSuffix}`;

  const {
    disposableDbUrl,
    originalDbName,
    host,
    port,
    username,
    password
  } = buildDisposableDatabaseUrl(dbUrl, disposableDbName);

  logger.log(`========================================================`);
  logger.log(`Starting Disposable Database Initialization Rehearsal`);
  logger.log(`Active Target DB: ${originalDbName} (PRESERVED - UNTOUCHED)`);
  logger.log(`Disposable Target DB: ${disposableDbName}`);
  logger.log(`Host: ${host}:${port}`);
  logger.log(`========================================================`);

  // Child environment with securely injected PGPASSWORD (never in command line arguments)
  const childEnv = { ...process.env };
  if (password) {
    childEnv.PGPASSWORD = password;
  }

  const runCommand = (command, args, envOverrides = {}) => {
    // Log command line without any passwords or credentials
    logger.log(`>> Executing: ${command} ${args.join(" ")}`);
    const result = execFn(command, args, {
      cwd: backendRoot,
      stdio: "inherit",
      env: { ...childEnv, ...envOverrides }
    });

    if (result.status !== 0) {
      throw new Error(`Command failed with exit code ${result.status}: ${command} ${args.join(" ")}`);
    }
  };

  let dbCreated = false;
  let errorOccurred = null;

  try {
    // Step 0: Create disposable DB using host/port/username derived from DATABASE_URL
    const createArgs = [];
    if (host) createArgs.push("-h", host);
    if (port) createArgs.push("-p", port);
    if (username) createArgs.push("-U", username);
    createArgs.push(disposableDbName);

    runCommand("createdb", createArgs);
    dbCreated = true;

    const psqlArgs = [];
    if (host) psqlArgs.push("-h", host);
    if (port) psqlArgs.push("-p", port);
    if (username) psqlArgs.push("-U", username);

    // Step 1: Base Schema
    logger.log("\n[Step 1/5] Applying Base Schema (sql/schema.sql)...");
    runCommand("psql", [...psqlArgs, "-d", disposableDbName, "-f", "sql/schema.sql"]);

    // Step 2: Forward Migrations (001-005)
    logger.log("\n[Step 2/5] Running Forward Migrations (001-005)...");
    runCommand("node", ["src/utils/migrate.js"], { DATABASE_URL: disposableDbUrl });

    // Step 3: Seed Staging Data
    logger.log("\n[Step 3/5] Applying Seed Data (sql/seed.sql)...");
    runCommand("psql", [...psqlArgs, "-d", disposableDbName, "-f", "sql/seed.sql"]);

    // Step 4: Dry-Run Verification (Expect 5 applied, 0 pending)
    logger.log("\n[Step 4/5] Running Dry-Run Verification...");
    runCommand("node", ["src/utils/migrate.js", "--dry-run"], { DATABASE_URL: disposableDbUrl });

    // Step 5: Idempotency Re-run (Rerun migrate and seed against same DB)
    logger.log("\n[Step 5/5] Testing Idempotency (Re-running migrate and seed)...");
    runCommand("node", ["src/utils/migrate.js"], { DATABASE_URL: disposableDbUrl });
    runCommand("psql", [...psqlArgs, "-d", disposableDbName, "-f", "sql/seed.sql"]);

    logger.log("\n========================================================");
    logger.log(`✓ DISPOSABLE DATABASE REHEARSAL COMPLETED SUCCESSFULLY!`);
    logger.log(`Database initialization sequence and idempotency verified.`);
    logger.log(`========================================================\n`);

    return { success: true, disposableDbName };
  } catch (err) {
    errorOccurred = err;
    logger.error(`\n✖ REHEARSAL FAILED: ${err.message}`);
    throw err;
  } finally {
    if (dbCreated) {
      logger.log(`\n>> Cleaning up: Dropping disposable database ${disposableDbName}...`);
      const dropArgs = [];
      if (host) dropArgs.push("-h", host);
      if (port) dropArgs.push("-p", port);
      if (username) dropArgs.push("-U", username);
      dropArgs.push(disposableDbName);

      const dropRes = execFn("dropdb", dropArgs, {
        cwd: backendRoot,
        stdio: "inherit",
        env: childEnv
      });

      if (dropRes.status === 0) {
        logger.log(`✓ Cleanly dropped disposable database ${disposableDbName}`);
      } else {
        const cleanupMsg = `FATAL: Failed to drop disposable database '${disposableDbName}'. Please manually run: dropdb ${dropArgs.join(" ")}`;
        logger.error(cleanupMsg);
        if (!errorOccurred) {
          throw new Error(cleanupMsg);
        }
      }
    }
  }
}

if (process.argv[1] && process.argv[1].endsWith("rehearseDatabaseInitialization.js")) {
  dotenv.config({ path: path.join(backendRoot, ".env") });
  try {
    executeRehearsal();
    process.exit(0);
  } catch (_) {
    process.exit(1);
  }
}
