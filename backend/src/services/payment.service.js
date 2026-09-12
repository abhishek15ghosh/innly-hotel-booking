import crypto from "crypto";
import { env } from "../config/env.js";
import { razorpayClient } from "../config/razorpay.js";
import { withTransaction } from "../config/db.js";
import * as bookingRepository from "../repositories/booking.repository.js";
import { ApiError } from "../utils/apiError.js";

export async function verifyPaymentSignature(user, payload, deps = {}) {
  if (!user?.id) {
    throw new ApiError(403, "Sync your profile before verifying payments");
  }

  const {
    withTx = withTransaction,
    bookingRepo = bookingRepository,
    razorpay = razorpayClient,
    razorpaySecret = env.RAZORPAY_KEY_SECRET
  } = deps;

  if (!razorpaySecret) {
    throw new ApiError(500, "Payment gateway secret configuration missing");
  }

  return withTx(async (client) => {
    const context = await bookingRepo.getPaymentVerificationContext(client, payload.bookingId, user.id);
    if (!context) {
      throw new ApiError(404, "Booking payment context not found");
    }

    // Idempotent success response if already confirmed/captured
    if (context.bookingStatus === "confirmed" && context.paymentStatus === "captured") {
      if (
        context.razorpayOrderId === payload.razorpayOrderId &&
        context.storedRazorpayPaymentId === payload.razorpayPaymentId
      ) {
        return {
          bookingId: payload.bookingId,
          bookingStatus: "confirmed",
          paymentStatus: "captured"
        };
      }
      throw new ApiError(400, "Razorpay payment ID mismatch for confirmed booking");
    }

    // Late payment reconciliation error if booking expired / failed / cancelled
    if (["cancelled", "payment_failed"].includes(context.bookingStatus)) {
      throw new ApiError(409, "Payment received after reservation expired. Support reconciliation required.");
    }

    if (context.bookingStatus !== "payment_pending") {
      throw new ApiError(400, "Booking is not in pending payment state");
    }

    if (!context.razorpayOrderId || payload.razorpayOrderId !== context.razorpayOrderId) {
      throw new ApiError(400, "Razorpay order mismatch");
    }

    // Compute HMAC signature using server-stored order ID & timingSafeEqual
    const expectedSignatureStr = crypto
      .createHmac("sha256", razorpaySecret)
      .update(`${context.razorpayOrderId}|${payload.razorpayPaymentId}`)
      .digest("hex");

    const expectedBuf = Buffer.from(expectedSignatureStr, "utf8");
    const actualBuf = Buffer.from(payload.razorpaySignature || "", "utf8");

    if (expectedBuf.length !== actualBuf.length || !crypto.timingSafeEqual(expectedBuf, actualBuf)) {
      throw new ApiError(400, "Razorpay signature verification failed");
    }

    // Fail closed if Razorpay client or payments.fetch is missing / fails
    if (!razorpay || !razorpay.payments || typeof razorpay.payments.fetch !== "function") {
      throw new ApiError(502, "Payment verification unavailable. Please try again.");
    }

    let paymentInfo;
    try {
      paymentInfo = await razorpay.payments.fetch(payload.razorpayPaymentId);
    } catch (err) {
      if (err instanceof ApiError) throw err;
      throw new ApiError(502, "Payment verification unavailable. Please try again.");
    }

    if (!paymentInfo) {
      throw new ApiError(502, "Payment verification unavailable. Please try again.");
    }

    if (paymentInfo.order_id !== context.razorpayOrderId) {
      throw new ApiError(400, "Razorpay payment order ID mismatch");
    }

    if (paymentInfo.id !== payload.razorpayPaymentId) {
      throw new ApiError(400, "Razorpay payment ID mismatch");
    }

    if (Number(paymentInfo.amount) !== Math.round(Number(context.paymentAmount) * 100)) {
      throw new ApiError(400, "Razorpay payment amount mismatch");
    }

    if (paymentInfo.currency !== (context.paymentCurrency || "INR")) {
      throw new ApiError(400, "Razorpay payment currency mismatch");
    }

    if (paymentInfo.status === "authorized") {
      return {
        bookingId: payload.bookingId,
        bookingStatus: "payment_pending",
        paymentStatus: "authorized",
        message: "Payment authorized but not captured yet."
      };
    }

    if (paymentInfo.status !== "captured") {
      throw new ApiError(400, "Razorpay payment is not captured");
    }

    await bookingRepo.markPaymentCaptured(client, payload.bookingId, payload);

    return {
      bookingId: payload.bookingId,
      bookingStatus: "confirmed",
      paymentStatus: "captured"
    };
  });
}
