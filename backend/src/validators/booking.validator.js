import { z } from "zod";

const isoDate = z.string().regex(/^\d{4}-\d{2}-\d{2}$/, "Expected YYYY-MM-DD");

export const createBookingSchema = z.object({
  body: z.object({
    hotelId: z.string().uuid(),
    roomId: z.string().uuid(),
    checkIn: isoDate,
    checkOut: isoDate,
    rooms: z.number().int().positive().max(5),
    guests: z.number().int().positive().max(10),
    guestName: z.string().min(2).max(120),
    guestEmail: z.string().email(),
    guestPhone: z.string().min(8).max(20)
  }),
  query: z.object({}).optional(),
  params: z.object({}).optional()
});

export const cancelBookingSchema = z.object({
  body: z.object({
    reason: z.string().min(3).max(255)
  }),
  params: z.object({
    bookingId: z.string().uuid()
  }),
  query: z.object({}).optional()
});

export const verifyPaymentSchema = z.object({
  body: z.object({
    bookingId: z.string().uuid(),
    razorpayOrderId: z.string().min(5),
    razorpayPaymentId: z.string().min(5),
    razorpaySignature: z.string().min(5)
  }),
  query: z.object({}).optional(),
  params: z.object({}).optional()
});
