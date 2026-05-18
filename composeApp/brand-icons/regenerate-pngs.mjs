#!/usr/bin/env node
/**
 * Regenerates transparent provider PNGs for composeResources from the SVG sources here.
 * Requires: npm install sharp (run once from this directory).
 */
import { readdir, mkdir } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import sharp from 'sharp';

const here = path.dirname(fileURLToPath(import.meta.url));
const outDir = path.resolve(here, '../src/commonMain/composeResources/drawable');

await mkdir(outDir, { recursive: true });
for (const file of await readdir(here)) {
  if (!file.endsWith('.svg')) continue;
  const base = file.replace(/\.svg$/, '');
  await sharp(path.join(here, file), { density: 288 })
    .resize(512, 512, { fit: 'contain', background: { r: 0, g: 0, b: 0, alpha: 0 } })
    .png()
    .toFile(path.join(outDir, `${base}.png`));
  console.log(`wrote ${base}.png`);
}
