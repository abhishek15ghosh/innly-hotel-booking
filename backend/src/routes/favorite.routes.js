import { Router } from "express";
import {
  addFavorite,
  listFavorites,
  removeFavorite
} from "../controllers/favorite.controller.js";
import { authenticate } from "../middlewares/auth.middleware.js";
import { validate } from "../middlewares/validate.middleware.js";
import { asyncHandler } from "../utils/asyncHandler.js";
import { hotelFavoriteParamSchema } from "../validators/favorite.validator.js";

const router = Router();

router.use(authenticate);
router.get("/", asyncHandler(listFavorites));
router.post("/:hotelId", validate(hotelFavoriteParamSchema), asyncHandler(addFavorite));
router.delete("/:hotelId", validate(hotelFavoriteParamSchema), asyncHandler(removeFavorite));

export default router;
