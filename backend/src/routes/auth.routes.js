import { Router } from "express";
import { syncProfile } from "../controllers/auth.controller.js";
import { authenticate } from "../middlewares/auth.middleware.js";
import { validate } from "../middlewares/validate.middleware.js";
import { asyncHandler } from "../utils/asyncHandler.js";
import { syncProfileSchema } from "../validators/auth.validator.js";

const router = Router();

router.post("/sync", authenticate, validate(syncProfileSchema), asyncHandler(syncProfile));

export default router;
