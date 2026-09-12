import * as favoriteRepository from "../repositories/favorite.repository.js";
import { ApiError } from "../utils/apiError.js";

export async function listFavorites(user) {
  if (!user?.id) {
    throw new ApiError(403, "Sync your profile before using favorites");
  }
  return favoriteRepository.listFavorites(user.id);
}

export async function addFavorite(user, hotelId) {
  if (!user?.id) {
    throw new ApiError(403, "Sync your profile before using favorites");
  }
  await favoriteRepository.addFavorite(user.id, hotelId);
}

export async function removeFavorite(user, hotelId) {
  if (!user?.id) {
    throw new ApiError(403, "Sync your profile before using favorites");
  }
  await favoriteRepository.removeFavorite(user.id, hotelId);
}
