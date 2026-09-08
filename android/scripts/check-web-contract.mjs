import { readdir, readFile } from 'node:fs/promises';
import { runSchema } from '../../web/src/domain/run.js';

const directory = new URL('../core/build/contract-fixtures/', import.meta.url);
const files = (await readdir(directory)).filter(name => name.endsWith('.json'));
if (files.length < 3) throw new Error('Run Android :core:test before checking the shared JSON contract.');
for (const file of files) {
  const run = runSchema.parse(JSON.parse(await readFile(new URL(file, directory), 'utf8')));
  if (file !== `${run.id}.json`) throw new Error(`Filename mismatch: ${file}`);
}
console.log(`Validated ${files.length} Android-generated records with the web Run JSON v1 schema.`);
