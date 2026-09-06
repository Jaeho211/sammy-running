import { readdir, readFile, mkdir, writeFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { parseRuns } from "../src/domain/run.js";
const directory = new URL("../../data/runs/", import.meta.url);
const files = (await readdir(directory))
  .filter((f) => f.endsWith(".json"))
  .sort();
const records = await Promise.all(
  files.map(async (file) => {
    const run = JSON.parse(await readFile(new URL(file, directory), "utf8"));
    if (file !== `${run.id}.json`)
      throw new Error(`Filename/ID mismatch: ${file}`);
    return run;
  }),
);
const runs = parseRuns(records);
const output = new URL("../public/generated/", import.meta.url);
await mkdir(output, { recursive: true });
await writeFile(new URL("runs.json", output), JSON.stringify(runs));
console.log(
  `Validated ${runs.length} runs → ${fileURLToPath(output)}runs.json`,
);
