import test from "node:test";
import assert from "node:assert/strict";
import { pace, duration } from "./formatters.js";
import { statistics, bestSplit, improvement } from "./statistics.js";
import { achievements } from "./achievements.js";
import { parseRuns } from "./run.js";
const run = {
  schemaVersion: 1,
  id: "2026-09-07T080231",
  date: "2026-09-07",
  startTime: "2026-09-07T08:02:31+09:00",
  title: "test",
  distanceMeters: 1241,
  durationSeconds: 526,
  splits: [
    { distanceMeters: 1000, durationSeconds: 418 },
    { distanceMeters: 241, durationSeconds: 108 },
  ],
};
test("pace handles supplied, calculated and zero distance values", () => {
  assert.equal(pace({ distanceMeters: 3000, durationSeconds: 1350 }), 450);
  assert.equal(pace({ ...run, averagePaceSecondsPerKm: 420 }), 420);
  assert.equal(pace({ ...run, distanceMeters: 0 }), undefined);
  assert.equal(duration(undefined), "—");
  assert.equal(duration(3599.9), "1:00:00");
});
test("best 1K excludes the partial finishing segment", () => {
  assert.equal(bestSplit(run), 418);
  assert.equal(
    bestSplit({
      ...run,
      splits: [{ distanceMeters: 241, durationSeconds: 90 }],
    }),
    undefined,
  );
});
test("aggregates and missing GPS or optional data are safe", () => {
  assert.deepEqual(statistics([]), {
    count: 0,
    totalDistance: 0,
    longest: 0,
    best1k: undefined,
  });
  assert.deepEqual(statistics([run, run]), {
    count: 2,
    totalDistance: 2482,
    longest: 1241,
    best1k: 418,
  });
  assert.equal(parseRuns([{ ...run, splits: undefined }]).length, 1);
});
test("achievement boundaries and empty state", () => {
  assert.ok(achievements([]).every((b) => !b.unlocked));
  const badges = achievements(
    Array.from({ length: 5 }, () => ({ ...run, distanceMeters: 5000 })),
  );
  for (const id of [
    "FIRST_RUN",
    "FIRST_5K",
    "TOTAL_25K",
    "RUNS_5",
    "FASTEST_1K",
    "LONGEST_RUN",
  ])
    assert.equal(badges.find((b) => b.id === id).unlocked, true);
  assert.equal(badges.find((b) => b.id === "TOTAL_50K").unlocked, false);
});
test("improvements compare latest against all earlier records", () => {
  const old = {
    ...run,
    startTime: "2026-09-01T08:02:31+09:00",
    splits: [{ distanceMeters: 1000, durationSeconds: 450 }],
  };
  assert.deepEqual(improvement([old, run]), { seconds: 32, best: 418 });
  assert.equal(improvement([run]), undefined);
  assert.equal(improvement([old, { ...run, splits: [] }]), undefined);
});
test("schema rejects invalid numeric values, duplicate IDs and unknown fields", () => {
  for (const bad of [
    { ...run, distanceMeters: -1 },
    { ...run, route: [{ lat: 91, lng: 0 }] },
    { ...run, token: "secret" },
    { ...run, durationSeconds: 0 },
    { ...run, date: "2026-02-31" },
  ])
    assert.throws(() => parseRuns([bad]));
  assert.throws(() => parseRuns([run, run]));
});
