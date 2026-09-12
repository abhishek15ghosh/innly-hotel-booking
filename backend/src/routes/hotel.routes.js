import { Router } from "express";
import { getAvailability, getHotelDetail, listHotels } from "../controllers/hotel.controller.js";
import { getMyReview, listHotelReviews } from "../controllers/review.controller.js";
import { authenticate, optionalAuthenticate } from "../middlewares/auth.middleware.js";
import { validate } from "../middlewares/validate.middleware.js";
import { asyncHandler } from "../utils/asyncHandler.js";
import {
  availabilitySchema,
  hotelDetailSchema,
  listHotelsSchema
} from "../validators/hotel.validator.js";
import { listReviewsSchema, myReviewSchema } from "../validators/review.validator.js";

const router = Router();

router.use(optionalAuthenticate);
router.get("/", validate(listHotelsSchema), asyncHandler(listHotels));
router.get("/:hotelId", validate(hotelDetailSchema), asyncHandler(getHotelDetail));
router.get(
  "/:hotelId/availability",
  validate(availabilitySchema),
  asyncHandler(getAvailability)
);
router.get(
  "/:hotelId/reviews",
  validate(listReviewsSchema),
  asyncHandler(listHotelReviews)
);
router.get(
  "/:hotelId/my-review",
  authenticate,
  validate(myReviewSchema),
  asyncHandler(getMyReview)
);

export default router;
