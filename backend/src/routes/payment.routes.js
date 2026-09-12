import { Router } from "express";
import { verifyPayment } from "../controllers/payment.controller.js";
import { authenticate } from "../middlewares/auth.middleware.js";
import { validate } from "../middlewares/validate.middleware.js";
import { asyncHandler } from "../utils/asyncHandler.js";
import { verifyPaymentSchema } from "../validators/booking.validator.js";

const router = Router();

router.use(authenticate);
router.post("/verify", validate(verifyPaymentSchema), asyncHandler(verifyPayment));

export default router;
