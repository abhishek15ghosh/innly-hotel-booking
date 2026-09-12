import { z } from "zod";

const optionalProfileString = (min, max) =>
  z.preprocess(
    (val) => (val === "" || val === null ? undefined : val),
    z.string().min(min).max(max).optional()
  );

export const syncProfileSchema = z.object({
  body: z.object({
    displayName: optionalProfileString(2, 120),
    phoneNumber: optionalProfileString(8, 20)
  }),
  query: z.object({}).optional(),
  params: z.object({}).optional()
});
