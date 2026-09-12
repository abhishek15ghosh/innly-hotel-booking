import assert from "node:assert/strict";
import test, { describe } from "node:test";
import { buildDisposableDatabaseUrl, executeRehearsal } from "../scripts/rehearseDatabaseInitialization.js";

describe("Database Rehearsal Runner Unit Tests (Mock Execution)", () => {
  test("1. buildDisposableDatabaseUrl preserves host, port, credentials and decodes URL-encoded credentials", () => {
    const origUrl = "postgres://user%40name:p%40ss%3Aword@pg.example.com:5433/innly_prod";
    const { disposableDbUrl, originalDbName, host, port, username, password } = buildDisposableDatabaseUrl(
      origUrl,
      "innly_rehearsal_temp"
    );

    assert.equal(originalDbName, "innly_prod");
    assert.equal(host, "pg.example.com");
    assert.equal(port, "5433");
    assert.equal(username, "user@name");
    assert.equal(password, "p@ss:word");
    assert.equal(disposableDbUrl, "postgres://user%40name:p%40ss%3Aword@pg.example.com:5433/innly_rehearsal_temp");
  });

  test("2. buildDisposableDatabaseUrl throws error if disposable DB matches active DB", () => {
    assert.throws(
      () => buildDisposableDatabaseUrl("postgres://localhost:5432/innly", "innly"),
      /Disposable database name cannot match the configured active database name/
    );
  });

  test("3. executeRehearsal succeeds and passes PGPASSWORD in child env without leaking in args or logs", () => {
    const executedCommands = [];
    const loggedMessages = [];

    const mockExec = (cmd, args, opts) => {
      executedCommands.push({ cmd, args, env: opts?.env });
      return { status: 0 };
    };

    const mockLogger = {
      log: (m) => loggedMessages.push(m),
      error: (m) => loggedMessages.push(m)
    };

    const res = executeRehearsal({
      dbUrl: "postgres://custom_user:secret_password_123@127.0.0.1:5432/innly_dev",
      execFn: mockExec,
      logger: mockLogger
    });

    assert.equal(res.success, true);
    assert.ok(executedCommands.some((c) => c.cmd === "createdb"));
    assert.ok(executedCommands.some((c) => c.cmd === "dropdb"));

    // Check that PGPASSWORD is set in child env
    for (const execution of executedCommands) {
      assert.equal(execution.env?.PGPASSWORD, "secret_password_123");
      // Check that password NEVER appears in command arguments
      for (const arg of execution.args) {
        assert.ok(!arg.includes("secret_password_123"), `Password found in argument: ${arg}`);
      }
    }

    // Check that password NEVER appears in logged messages
    for (const log of loggedMessages) {
      assert.ok(!log.includes("secret_password_123"), `Password found in log: ${log}`);
    }
  });

  test("4. executeRehearsal fails fast on createdb failure and does not call dropdb", () => {
    const executedCommands = [];
    const mockExec = (cmd, args) => {
      executedCommands.push({ cmd, args });
      if (cmd === "createdb") {
        return { status: 1 };
      }
      return { status: 0 };
    };

    assert.throws(
      () => {
        executeRehearsal({
          dbUrl: "postgres://mock_user@127.0.0.1:5432/innly_dev",
          execFn: mockExec,
          logger: { log: () => {}, error: () => {} }
        });
      },
      /Command failed with exit code 1: createdb/
    );

    // dropdb should not be called since createdb failed
    assert.ok(!executedCommands.some((c) => c.cmd === "dropdb"));
  });

  test("5. executeRehearsal cleans up and throws error if migration fails during rehearsal", () => {
    const executedCommands = [];
    const mockExec = (cmd, args) => {
      executedCommands.push({ cmd, args });
      if (cmd === "node" && args[0] === "src/utils/migrate.js") {
        return { status: 1 };
      }
      return { status: 0 };
    };

    assert.throws(
      () => {
        executeRehearsal({
          dbUrl: "postgres://mock_user@127.0.0.1:5432/innly_dev",
          execFn: mockExec,
          logger: { log: () => {}, error: () => {} }
        });
      },
      /Command failed with exit code 1/
    );

    // Verify dropdb was still called in finally block
    assert.ok(executedCommands.some((c) => c.cmd === "dropdb"));
  });

  test("6. executeRehearsal reports fatal error if cleanup dropdb fails", () => {
    const logs = [];
    const mockExec = (cmd, args) => {
      if (cmd === "dropdb") {
        return { status: 1 };
      }
      return { status: 0 };
    };

    assert.throws(
      () => {
        executeRehearsal({
          dbUrl: "postgres://mock_user@127.0.0.1:5432/innly_dev",
          execFn: mockExec,
          logger: { log: (m) => logs.push(m), error: (m) => logs.push(m) }
        });
      },
      /Failed to drop disposable database/
    );

    assert.ok(logs.some((l) => typeof l === "string" && l.includes("FATAL: Failed to drop disposable database")));
  });

  test("7. Active target database is NEVER passed as target database to createdb or dropdb", () => {
    const activeDbName = "innly_production_active";
    const executedCommands = [];

    const mockExec = (cmd, args) => {
      executedCommands.push({ cmd, args });
      return { status: 0 };
    };

    executeRehearsal({
      dbUrl: `postgres://user:pass@127.0.0.1:5432/${activeDbName}`,
      execFn: mockExec,
      logger: { log: () => {}, error: () => {} }
    });

    for (const execution of executedCommands) {
      if (execution.cmd === "createdb" || execution.cmd === "dropdb") {
        const targetDbArg = execution.args[execution.args.length - 1];
        assert.notEqual(targetDbArg, activeDbName, `Active DB passed to destructive command ${execution.cmd}`);
        assert.ok(targetDbArg.startsWith("innly_rehearsal_"));
      }
    }
  });
});
