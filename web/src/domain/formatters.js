export const distance = (meters) => (meters / 1000).toFixed(2);
export function duration(seconds) {
  if (!Number.isFinite(seconds) || seconds < 0) return "—";
  const s = Math.round(seconds);
  return s >= 3600
    ? `${Math.floor(s / 3600)}:${String(Math.floor(s / 60) % 60).padStart(2, "0")}:${String(s % 60).padStart(2, "0")}`
    : `${Math.floor(s / 60)}:${String(s % 60).padStart(2, "0")}`;
}
export const pace = (run) =>
  run.averagePaceSecondsPerKm ??
  (run.distanceMeters > 0
    ? (run.durationSeconds / run.distanceMeters) * 1000
    : undefined);
export const dateLabel = (date) =>
  new Intl.DateTimeFormat("ko-KR", {
    month: "long",
    day: "numeric",
    weekday: "short",
    timeZone: "UTC",
  }).format(new Date(`${date}T12:00:00Z`));
