import * as hotelService from "../services/hotel.service.js";

export async function listHotels(req, res) {
  const data = await hotelService.listHotels(req.validated.query, req.user);
  res.json({ success: true, data });
}

export async function getHotelDetail(req, res) {
  const data = await hotelService.getHotelDetail(req.validated.params.hotelId, req.user);
  res.json({ success: true, data });
}

export async function getAvailability(req, res) {
  const data = await hotelService.getAvailability({
    hotelId: req.validated.params.hotelId,
    roomId: req.validated.query.roomId,
    checkIn: req.validated.query.checkIn,
    checkOut: req.validated.query.checkOut,
    rooms: req.validated.query.rooms
  });
  res.json({ success: true, data });
}
