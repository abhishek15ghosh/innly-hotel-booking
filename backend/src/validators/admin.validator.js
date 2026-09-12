import { z } from "zod";

const isoDate = z.string().regex(/^\d{4}-\d{2}-\d{2}$/, "Expected YYYY-MM-DD");

export const adminHotelSchema = z.object({
  body: z.object({
    name: z.string().min(2).max(180),
    slug: z.string().min(2).max(180),
    description: z.string().min(10),
    city: z.string().min(2).max(120),
    address: z.string().min(5).max(255),
    starRating: z.number().min(1).max(5).optional(),
    cancellationPolicy: z.string().min(10)
  }),
  params: z.object({}).optional(),
  query: z.object({}).optional()
});

export const adminRoomSchema = z.object({
  body: z.object({
    hotelId: z.string().uuid(),
    name: z.string().min(2).max(120),
    description: z.string().min(10),
    capacity: z.number().int().positive(),
    bedType: z.string().min(2).max(80),
    basePrice: z.number().int().positive(),
    totalInventory: z.number().int().positive()
  }),
  params: z.object({}).optional(),
  query: z.object({}).optional()
});

export const adminPricingSchema = z.object({
  body: z.object({
    date: isoDate,
    totalInventory: z.number().int().positive().optional(),
    priceOverride: z.number().int().positive().optional()
  }),
  params: z.object({
    roomId: z.string().uuid()
  }),
  query: z.object({}).optional()
});

export const moderateReviewSchema = z.object({
  body: z.object({
    status: z.enum(["approved", "rejected"])
  }),
  params: z.object({
    reviewId: z.string().uuid()
  }),
  query: z.object({}).optional()
});
