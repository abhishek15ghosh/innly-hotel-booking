import { Router } from "express";
import {
  cancelBooking,
  createBooking,
  getBookingHistory
} from "../controllers/booking.controller.js";
import { authenticate } from "../middlewares/auth.middleware.js";
import { validate } from "../middlewares/validate.middleware.js";
import { asyncHandler } from "../utils/asyncHandler.js";
import {
  cancelBookingSchema,
  createBookingSchema
} from "../validators/booking.validator.js";

const router = Router();

router.use(authenticate);
router.get("/", asyncHandler(getBookingHistory));
router.post("/", validate(createBookingSchema), asyncHandler(createBooking));
router.post("/:bookingId/cancel", validate(cancelBookingSchema), asyncHandler(cancelBooking));

export default router;
