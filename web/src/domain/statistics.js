export function bestSplit(run) {
  const full = (run.splits ?? []).filter((s) => s.distanceMeters === 1000);
  return full.length
    ? Math.min(...full.map((s) => s.durationSeconds))
    : undefined;
}
export function statistics(runs) {
  const bests = runs.map(bestSplit).filter(Number.isFinite);
  return {
    count: runs.length,
    totalDistance: runs.reduce((s, r) => s + r.distanceMeters, 0),
    longest: Math.max(0, ...runs.map((r) => r.distanceMeters)),
    best1k: bests.length ? Math.min(...bests) : undefined,
  };
}
export function improvement(runs) {
  const sorted = [...runs].sort(
    (a, b) => Date.parse(b.startTime) - Date.parse(a.startTime),
  );
  if (sorted.length < 2) return undefined;
  const current = bestSplit(sorted[0]);
  const prior = statistics(sorted.slice(1)).best1k;
  return Number.isFinite(current) && Number.isFinite(prior) && current < prior
    ? { seconds: prior - current, best: current }
    : undefined;
}
