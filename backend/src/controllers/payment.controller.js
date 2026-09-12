import * as paymentService from "../services/payment.service.js";

export async function verifyPayment(req, res) {
  const data = await paymentService.verifyPaymentSignature(req.user, req.validated.body);
  res.json({ success: true, data });
}
