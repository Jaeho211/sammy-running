// Explicit development utility. Never run automatically during a build.
import { mkdir, writeFile } from "node:fs/promises";
const folder = new URL("../../data/runs/", import.meta.url);
await mkdir(folder, { recursive: true });
const samples = [
  ["2026-08-22", 1000, [510]],
  ["2026-08-26", 3000, [490, 478, 465]],
  ["2026-08-30", 5000, [482, 470, 459, 452, 449]],
  ["2026-09-03", 2200, [451, 435, 94]],
  ["2026-09-07", 5241, [455, 448, 439, 429, 418, 108]],
];
for (const [i, [date, meters, times]] of samples.entries()) {
  const id = `${date}T080231`,
    durationSeconds = times.reduce((a, b) => a + b, 0);
  const run = {
    schemaVersion: 1,
    id,
    date,
    startTime: `${date}T08:02:31+09:00`,
    distanceMeters: meters,
    durationSeconds,
    averagePaceSecondsPerKm: (durationSeconds / meters) * 1000,
    splits: times.map((seconds, j) => ({
      distanceMeters: Math.min(1000, meters - j * 1000),
      durationSeconds: seconds,
    })),
  };
  if (i !== 3) {
    run.route = Array.from({ length: 42 }, (_, j) => {
      const t = (j / 41) * Math.PI * 1.85;
      return {
        lat: Number(
          (37.522 + Math.sin(t) * (0.003 + i * 0.0006) + j * 0.000035).toFixed(
            6,
          ),
        ),
        lng: Number(
          (126.982 + Math.cos(t) * (0.006 + i * 0.001) + j * 0.000025).toFixed(
            6,
          ),
        ),
      };
    });
  }
  await writeFile(
    new URL(`${id}.json`, folder),
    JSON.stringify(run, null, 2) + "\n",
    { flag: "wx" },
  );
}
