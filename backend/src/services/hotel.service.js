import * as hotelRepository from "../repositories/hotel.repository.js";
import { ApiError } from "../utils/apiError.js";
import { getStayDates, summarizeAvailability } from "../utils/bookingAvailability.js";

export async function listHotels(filters, user) {
  const page = Number(filters.page || 1);
  const limit = Number(filters.limit || 50);
  const items = await hotelRepository.listHotels(
    {
      city: filters.city,
      minPrice: filters.minPrice,
      maxPrice: filters.maxPrice,
      minRating: filters.minRating,
      amenities: filters.amenities ? filters.amenities.split(",") : [],
      limit,
      offset: (page - 1) * limit
    },
    user?.id || null
  );

  return {
    items,
    page,
    limit,
    total: items.length
  };
}

export async function getHotelDetail(hotelId, user) {
  const hotel = await hotelRepository.getHotelById(hotelId, user?.id || null);
  if (!hotel) {
    throw new ApiError(404, "Hotel not found");
  }
  return hotel;
}

export async function getAvailability({ hotelId, roomId, checkIn, checkOut, rooms }) {
  const stayDates = getStayDates(checkIn, checkOut);
  const inventoryRows = await hotelRepository.getRoomInventory(hotelId, roomId, checkIn, checkOut);
  return summarizeAvailability(inventoryRows, stayDates, Number(rooms));
}
