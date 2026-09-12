import cors from "cors";
import express from "express";
import rateLimit from "express-rate-limit";
import helmet from "helmet";
import morgan from "morgan";
import { pool } from "./config/db.js";
import { env } from "./config/env.js";
import { handleWebhook } from "./controllers/webhook.controller.js";
import { errorHandler, notFoundHandler } from "./middlewares/error.middleware.js";
import apiRoutes from "./routes/index.js";
import { ApiError } from "./utils/apiError.js";

export function createApp(options = {}) {
  const app = express();

  const trustProxyHops = options.trustProxyHops !== undefined ? options.trustProxyHops : env.TRUST_PROXY_HOPS;
  const rawCorsOrigins = options.corsAllowedOrigins !== undefined ? options.corsAllowedOrigins : env.CORS_ALLOWED_ORIGINS;
  const dbPool = options.dbPool || pool;
  const isProduction = options.nodeEnv ? options.nodeEnv === "production" : env.NODE_ENV === "production";

  if (options.webhookDeps) {
    app.locals.webhookDeps = options.webhookDeps;
  }

  // Strict numeric trust proxy configuration for reverse proxy / cloud load balancer
  app.set("trust proxy", trustProxyHops);

  app.use(helmet());

  // Parse allowed origins
  const allowedOrigins = (typeof rawCorsOrigins === "string" ? rawCorsOrigins : "")
    .split(",")
    .map((s) => s.trim())
    .filter(Boolean);

  // Origin-safe CORS configuration:
  // 1. Requests with NO Origin header (native Android apps, server-to-server, curl, Razorpay webhooks) are ALWAYS allowed.
  // 2. Browser requests with an Origin header must match an origin in allowedOrigins.
  // 3. In non-production, if no allowedOrigins are specified, all development origins are permitted.
  app.use(
    cors({
      origin: (origin, callback) => {
        if (!origin) {
          return callback(null, true);
        }
        if (allowedOrigins.includes(origin)) {
          return callback(null, true);
        }
        if (!isProduction && allowedOrigins.length === 0) {
          return callback(null, true);
        }
        return callback(new ApiError(403, "CORS origin blocked"));
      }
    })
  );

  // Health and Probe endpoints (Exempt from rate limiting)
  app.get("/api/v1/health/liveness", (req, res) => {
    return res.status(200).json({ status: "live" });
  });

  app.get("/api/v1/health/readiness", async (req, res) => {
    try {
      await dbPool.query("SELECT 1");
      return res.status(200).json({ status: "ready" });
    } catch (_) {
      // Return generic status 503 without exposing database URLs, credentials, usernames, or stack traces
      return res.status(503).json({ status: "not_ready" });
    }
  });

  // Generous Webhook Rate Limiter: placed before raw body parsing so raw bytes stream is untouched
  const webhookLimiter = rateLimit({
    windowMs: options.webhookWindowMs || 60 * 1000,
    max: options.webhookRateLimit || 300,
    standardHeaders: true,
    legacyHeaders: false,
    message: { success: false, error: "Too many webhook requests" }
  });

  // Raw body parsing specifically for Razorpay webhook signature verification
  app.post(
    "/api/v1/payments/webhook",
    webhookLimiter,
    express.raw({ type: "application/json" }),
    handleWebhook
  );

  // Standard JSON body parsing for all other API endpoints
  app.use(express.json());
  app.use(morgan("dev"));

  // Public exploration rate limiter
  const publicExploreLimiter = rateLimit({
    windowMs: options.publicExploreWindowMs || 15 * 60 * 1000,
    max: options.publicExploreRateLimit || 200,
    standardHeaders: true,
    legacyHeaders: false,
    message: { success: false, error: "Too many requests, please try again later." }
  });

  app.use("/api/v1/hotels", publicExploreLimiter);
  app.use("/api/v1/reviews", publicExploreLimiter);

  app.use("/api/v1", apiRoutes);
  app.use(notFoundHandler);
  app.use(errorHandler);

  return app;
}
