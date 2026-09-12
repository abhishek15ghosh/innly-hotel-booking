import { handleRazorpayWebhook } from "../services/webhook.service.js";

export async function handleWebhook(req, res, next) {
  try {
    const rawBodyBuffer = req.body;
    const signatureHeader = req.headers["x-razorpay-signature"];
    const eventIdHeader = req.headers["x-razorpay-event-id"];
    const deps = req.app?.locals?.webhookDeps || {};

    const result = await handleRazorpayWebhook(rawBodyBuffer, signatureHeader, eventIdHeader, deps);
    return res.status(200).json(result);
  } catch (error) {
    return next(error);
  }
}
