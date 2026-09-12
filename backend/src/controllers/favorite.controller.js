import * as favoriteService from "../services/favorite.service.js";

export async function listFavorites(req, res) {
  const data = await favoriteService.listFavorites(req.user);
  res.json({ success: true, data });
}

export async function addFavorite(req, res) {
  await favoriteService.addFavorite(req.user, req.validated.params.hotelId);
  res.json({ success: true, data: null });
}

export async function removeFavorite(req, res) {
  await favoriteService.removeFavorite(req.user, req.validated.params.hotelId);
  res.json({ success: true, data: null });
}
