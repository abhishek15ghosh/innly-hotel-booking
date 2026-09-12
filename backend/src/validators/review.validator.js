import { z } from "zod";

export const createReviewSchema = z.object({
  body: z.object({
    hotelId: z.string().uuid(),
    bookingId: z.string().uuid(),
    reviewId: z.string().uuid().optional(),
    rating: z.number().int().min(1).max(5),
    title: z.string().trim().min(2).max(120),
    comment: z.string().trim().min(5).max(2000)
  }),
  query: z.object({}).optional(),
  params: z.object({}).optional()
});

export const listReviewsSchema = z.object({
  params: z.object({
    hotelId: z.string().uuid()
  }),
  query: z.object({
    page: z.coerce.number().int().min(1).default(1),
    limit: z.coerce.number().int().min(1).max(50).default(10)
  }).optional()
});

export const myReviewSchema = z.object({
  params: z.object({
    hotelId: z.string().uuid()
  }),
  query: z.object({}).optional()
});
