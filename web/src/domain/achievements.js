import { statistics } from "./statistics.js";
const definitions = [
  ["FIRST_RUN", "첫 발자국", "함께한 첫 달리기", "✦", (s) => s.count >= 1],
  ...[1, 3, 5].map((k) => [
    `FIRST_${k}K`,
    `${k} km 탐험가`,
    `한 번에 ${k} km 달리기`,
    "↗",
    (s) => s.longest >= k * 1000,
  ]),
  ...[10, 25, 50, 100].map((k) => [
    `TOTAL_${k}K`,
    `${k} km의 추억`,
    `누적 ${k} km 달리기`,
    "◎",
    (s) => s.totalDistance >= k * 1000,
  ]),
  ...[5, 10, 20].map((k) => [
    `RUNS_${k}`,
    `${k}번의 약속`,
    `함께 ${k}번 달리기`,
    "⚑",
    (s) => s.count >= k,
  ]),
  [
    "FASTEST_1K",
    "나만의 최고 1 km",
    "첫 1 km 구간 기록 세우기",
    "ϟ",
    (s) => Number.isFinite(s.best1k),
  ],
  [
    "LONGEST_RUN",
    "가장 먼 발걸음",
    "나만의 최장 거리 기록 세우기",
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
