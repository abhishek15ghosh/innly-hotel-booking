import { Router } from "express";
import { createReview } from "../controllers/review.controller.js";
import { authenticate } from "../middlewares/auth.middleware.js";
import { validate } from "../middlewares/validate.middleware.js";
import { asyncHandler } from "../utils/asyncHandler.js";
import { createReviewSchema } from "../validators/review.validator.js";

const router = Router();

router.use(authenticate);
router.post("/", validate(createReviewSchema), asyncHandler(createReview));

export default router;
