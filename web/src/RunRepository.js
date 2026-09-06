import { parseRuns } from "./domain/run.js";
export async function loadRuns(signal) {
  const response = await fetch(
    `${import.meta.env.BASE_URL}generated/runs.json`,
    { signal },
  );
  if (!response.ok)
    throw new Error("기록을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.");
  return parseRuns(await response.json());
}
