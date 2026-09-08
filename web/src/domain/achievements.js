import { statistics } from "./statistics.js";
const definitions = [
  ["FIRST_RUN", "첫 러닝", "첫 러닝 기록", "✦", (s) => s.count >= 1],
  ...[1, 3, 5].map((k) => [
    `FIRST_${k}K`,
    `${k} km 완주`,
    `한 번에 ${k} km 달리기`,
    "↗",
    (s) => s.longest >= k * 1000,
  ]),
  ...[10, 25, 50, 100].map((k) => [
    `TOTAL_${k}K`,
    `누적 ${k} km`,
    `누적 ${k} km 달리기`,
    "◎",
    (s) => s.totalDistance >= k * 1000,
  ]),
  ...[5, 10, 20].map((k) => [
    `RUNS_${k}`,
    `${k}회 러닝`,
    `러닝 ${k}회 기록`,
    "⚑",
    (s) => s.count >= k,
  ]),
  [
    "FASTEST_1K",
    "최고 1 km",
    "첫 1 km 구간 기록 세우기",
    "ϟ",
    (s) => Number.isFinite(s.best1k),
  ],
  [
    "LONGEST_RUN",
    "최장 거리",
    "최장 거리 기록 세우기",
    "⌁",
    (s) => s.longest > 0,
  ],
];
export const achievements = (runs) => {
  const s = statistics(runs);
  return definitions.map(([id, title, description, icon, test]) => ({
    id,
    title,
    description,
    icon,
    unlocked: test(s),
  }));
};
