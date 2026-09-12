import { z } from "zod";

const isoDate = z.string().regex(/^\d{4}-\d{2}-\d{2}$/, "Expected YYYY-MM-DD");

export const listHotelsSchema = z.object({
  body: z.object({}).optional(),
  params: z.object({}).optional(),
  query: z.object({
    city: z.string().optional(),
    minPrice: z.coerce.number().int().optional(),
    maxPrice: z.coerce.number().int().optional(),
    minRating: z.coerce.number().optional(),
    amenities: z.string().optional(),
    page: z.coerce.number().int().positive().optional(),
    limit: z.coerce.number().int().positive().max(50).optional()
  })
});

export const hotelDetailSchema = z.object({
  body: z.object({}).optional(),
  query: z.object({}).optional(),
  params: z.object({
    hotelId: z.string().uuid()
  })
});

export const availabilitySchema = z.object({
  body: z.object({}).optional(),
  params: z.object({
    hotelId: z.string().uuid()
  }),
  query: z.object({
    roomId: z.string().uuid(),
    checkIn: isoDate,
    checkOut: isoDate,
    rooms: z.coerce.number().int().positive()
  })
});
