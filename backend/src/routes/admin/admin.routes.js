import { Router } from "express";
import {
  createHotel,
  createRoom,
  listBookings,
  listPayments,
  moderateReview,
  updateRoomPricing
} from "../../controllers/admin.controller.js";
import { authenticate, requireAdmin } from "../../middlewares/auth.middleware.js";
import { validate } from "../../middlewares/validate.middleware.js";
import { asyncHandler } from "../../utils/asyncHandler.js";
import {
  adminHotelSchema,
  adminPricingSchema,
  adminRoomSchema,
  moderateReviewSchema
} from "../../validators/admin.validator.js";

const router = Router();

router.use(authenticate, requireAdmin);

router.post("/hotels", validate(adminHotelSchema), asyncHandler(createHotel));
router.post("/rooms", validate(adminRoomSchema), asyncHandler(createRoom));
router.patch("/pricing/rooms/:roomId", validate(adminPricingSchema), asyncHandler(updateRoomPricing));
router.get("/bookings", asyncHandler(listBookings));
router.get("/payments", asyncHandler(listPayments));
router.patch("/reviews/:reviewId/moderate", validate(moderateReviewSchema), asyncHandler(moderateReview));

export default router;
