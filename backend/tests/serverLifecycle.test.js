import assert from "node:assert/strict";
import test, { describe } from "node:test";
import { createShutdownHandler } from "../src/server.js";
import { startExpiryWorker, stopExpiryWorker } from "../src/services/bookingExpiry.service.js";
import { RefundReconciliationWorker } from "../src/workers/refundReconciliation.worker.js";

describe("Server Lifecycle & Graceful Shutdown Unit Tests", () => {
  test("1. createShutdownHandler stops workers, closes server, and ends pool in exact order", async () => {
    const events = [];

    const mockServer = {
      close: (cb) => {
        events.push("server_close");
        cb();
      }
    };

    const mockPool = {
      end: async () => {
        events.push("pool_end");
      }
    };

    const stopWorkers = () => {
      events.push("stop_workers");
    };

    let exitCode = null;
    const mockExit = (code) => {
      exitCode = code;
    };

    const shutdown = createShutdownHandler({
      server: mockServer,
      dbPool: mockPool,
      stopWorkers,
      exitFn: mockExit,
      logger: { log: () => {}, error: () => {} }
    });

    await shutdown("SIGTERM");

    assert.equal(exitCode, 0);
    assert.deepEqual(events, ["stop_workers", "server_close", "pool_end"]);
  });

  test("2. createShutdownHandler ignores second invocation if shutdown already in progress", async () => {
    let poolEndCalls = 0;
    const mockPool = {
      end: async () => {
        poolEndCalls++;
      }
    };

    const shutdown = createShutdownHandler({
      server: { close: (cb) => cb() },
      dbPool: mockPool,
      stopWorkers: () => {},
      exitFn: () => {},
      logger: { log: () => {}, error: () => {} }
    });

    // Run first call
    const p1 = shutdown("SIGTERM");
    // Run second call immediately
    const p2 = shutdown("SIGINT");

    await Promise.all([p1, p2]);

    assert.equal(poolEndCalls, 1);
  });

  test("3. Shutdown timeout produces single failure exit (1) and prevents subsequent success exit (0)", async () => {
    const exitCalls = [];
    let finishServerClose;

    const mockHangingServer = {
      close: (cb) => {
        finishServerClose = cb;
      }
    };

    const mockPool = {
      end: async () => {}
    };

    const shutdown = createShutdownHandler({
      server: mockHangingServer,
      dbPool: mockPool,
      stopWorkers: () => {},
      timeoutMs: 50, // Short timeout for test
      exitFn: (code) => {
        exitCalls.push(code);
      },
      logger: { log: () => {}, error: () => {} }
    });

    // Start shutdown (server will hang until finishServerClose is called)
    shutdown("SIGTERM");

    // Wait for timeout to fire (100ms > 50ms timeout)
    await new Promise((resolve) => setTimeout(resolve, 100));

    // Verify exit(1) was called due to timeout
    assert.deepEqual(exitCalls, [1]);

    // Now resolve the hanging server.close callback
    if (typeof finishServerClose === "function") {
      finishServerClose();
    }

    // Give time for any microtasks
    await new Promise((resolve) => setTimeout(resolve, 50));

    // Verify exitFn was NOT called again with 0 (remains [1])
    assert.deepEqual(exitCalls, [1]);
  });

  test("4. Worker Observability: startExpiryWorker and stopExpiryWorker log start and stop events once", () => {
    const logs = [];
    const mockLogger = {
      log: (msg) => logs.push(msg),
      error: () => {}
    };

    startExpiryWorker(45000, {
      logger: mockLogger,
      withTx: async () => ({ processedCount: 0 }),
      bookingRepo: { getExpiredPendingBookings: async () => [] }
    });

    assert.ok(logs.some((l) => l.includes("Booking expiry worker started (interval: 45000ms)")));

    stopExpiryWorker({ logger: mockLogger });
    assert.ok(logs.some((l) => l.includes("Booking expiry worker stopped")));
  });

  test("5. Worker Observability: RefundReconciliationWorker logs start and stop events with workerId", () => {
    const logs = [];
    const mockLogger = {
      log: (msg) => logs.push(msg),
      error: () => {}
    };

    const worker = new RefundReconciliationWorker({
      workerId: "test-refund-worker-1",
      intervalMs: 15000,
      logger: mockLogger,
      withTx: async () => []
    });

    worker.start();
    assert.ok(logs.some((l) => l.includes("Refund reconciliation worker started [ID: test-refund-worker-1] (interval: 15000ms)")));

    worker.stop();
    assert.ok(logs.some((l) => l.includes("Refund reconciliation worker stopped [ID: test-refund-worker-1]")));
  });
});
