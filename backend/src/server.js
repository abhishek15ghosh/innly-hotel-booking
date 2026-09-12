import { createApp } from "./app.js";
import { pool } from "./config/db.js";
import { env } from "./config/env.js";
import { startExpiryWorker, stopExpiryWorker } from "./services/bookingExpiry.service.js";
import { RefundReconciliationWorker } from "./workers/refundReconciliation.worker.js";

/**
 * Creates an idempotent, bounded graceful shutdown handler.
 */
export function createShutdownHandler(deps = {}) {
  const {
    server,
    dbPool = pool,
    stopWorkers = () => {},
    timeoutMs = 10000,
    exitFn = process.exit,
    logger = console
  } = deps;

  let isShuttingDown = false;
  let exitCalled = false;

  const safeExit = (code) => {
    if (!exitCalled) {
      exitCalled = true;
      exitFn(code);
    }
  };

  return async (signal = "SIGTERM") => {
    if (isShuttingDown) {
      logger.log(`Shutdown already in progress. Ignoring signal ${signal}.`);
      return;
    }
    isShuttingDown = true;
    logger.log(`Received ${signal}. Initiating graceful shutdown...`);

    // Bounded shutdown timer: force exit if closing hangs
    const timer = setTimeout(() => {
      logger.error("Graceful shutdown timed out. Forcing termination.");
      safeExit(1);
    }, timeoutMs);
    if (typeof timer.unref === "function") {
      timer.unref();
    }

    try {
      // 1. Stop background worker timers first
      stopWorkers();

      // 2. Stop accepting new connections
      if (server && typeof server.close === "function") {
        await new Promise((resolve) => server.close(resolve));
        logger.log("HTTP server closed.");
      }

      // 3. Close database connection pool
      if (dbPool && typeof dbPool.end === "function") {
        await dbPool.end();
        logger.log("Database connection pool closed.");
      }

      clearTimeout(timer);
      safeExit(0);
    } catch (err) {
      logger.error("Error during graceful shutdown:", err);
      clearTimeout(timer);
      safeExit(1);
    }
  };
}

const app = createApp();
const refundWorker = new RefundReconciliationWorker();

async function bootstrap() {
  await pool.query("SELECT 1");

  startExpiryWorker();
  refundWorker.start();

  const server = app.listen(env.PORT, () => {
    console.log(`Innly backend listening on port ${env.PORT}`);
  });

  const shutdown = createShutdownHandler({
    server,
    dbPool: pool,
    stopWorkers: () => {
      stopExpiryWorker();
      refundWorker.stop();
    }
  });

  process.on("SIGTERM", () => shutdown("SIGTERM"));
  process.on("SIGINT", () => shutdown("SIGINT"));
}

if (process.argv[1] && process.argv[1].endsWith("server.js")) {
  bootstrap().catch(async (error) => {
    console.error("Failed to start backend", error);
    try {
      await pool.end();
    } catch (_) {}
    process.exit(1);
  });
}
