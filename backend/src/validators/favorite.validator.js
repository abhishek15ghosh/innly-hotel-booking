import { z } from "zod";

export const hotelFavoriteParamSchema = z.object({
  body: z.object({}).optional(),
  query: z.object({}).optional(),
  params: z.object({
    hotelId: z.string().uuid()
  })
});
