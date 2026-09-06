import { z } from "zod";
const positive = z.number().finite().positive();
export const runSchema = z
  .object({
    schemaVersion: z.literal(1),
    id: z.string().regex(/^\d{4}-\d{2}-\d{2}T\d{6}$/),
    date: z.iso.date(),
    startTime: z.iso.datetime({ offset: true }),
    title: z.string().max(200),
    comment: z.string().max(4000).optional(),
    distanceMeters: z.number().finite().nonnegative(),
    durationSeconds: positive,
    averagePaceSecondsPerKm: positive.optional(),
    splits: z
      .array(z.object({ distanceMeters: positive, durationSeconds: positive }))
      .optional(),
    heartRate: z.object({ average: positive, max: positive }).optional(),
    cadence: z.object({ average: positive }).optional(),
    route: z
      .array(
        z.object({
          lat: z.number().min(-90).max(90),
          lng: z.number().min(-180).max(180),
        }),
      )
      .optional(),
  })
  .strict();
export function parseRuns(data) {
  const runs = z.array(runSchema).parse(data);
  if (new Set(runs.map((r) => r.id)).size !== runs.length)
    throw new Error("Duplicate run ID");
  return runs.sort((a, b) => Date.parse(b.startTime) - Date.parse(a.startTime));
}
