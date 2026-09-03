#!/usr/bin/env node
import { execSync } from 'node:child_process';
import fs from 'node:fs/promises';
import path from 'node:path';
import ZAI from 'z-ai-web-dev-sdk';

const VIDEO = '/home/z/my-project/upload/Record_2026-09-03-23-54-47.mp4';
const FRAMES = '/home/z/my-project/upload/bitchord_ref_frames';
const N = 16;

await fs.rm(FRAMES, { recursive: true, force: true });
await fs.mkdir(FRAMES, { recursive: true });

const dur = 21.236889;
const step = dur / (N + 1);
for (let i = 1; i <= N; i++) {
  const t = (step * i).toFixed(2);
  execSync(`ffmpeg -y -ss ${t} -i "${VIDEO}" -frames:v 1 -q:v 2 "${FRAMES}/f${String(i).padStart(2,'0')}.jpg" -loglevel error`);
  console.log(`✓ ${t}s`);
}

const files = (await fs.readdir(FRAMES)).filter(f => f.endsWith('.jpg')).sort();
const imgs = await Promise.all(files.map(async f => {
  const b = await fs.readFile(path.join(FRAMES, f));
  return { type: 'image_url', image_url: { url: `data:image/jpeg;base64,${b.toString('base64')}` } };
}));

console.log(`Loaded ${imgs.length} frames`);

const prompt = `These ${imgs.length} screenshots are from BITCHORD app showing the color-changing animation when switching songs. For EACH screenshot, describe:

1. What's the dominant background color? (give hex estimate)
2. Is the gradient smooth? Top color vs bottom color?
3. Is the album art visible? What size/position?
4. Describe the TRANSITION between frames — how does the color change from one screenshot to the next? Is it a smooth crossfade, a hard cut, or something else?
5. Are there any visible "flashes" of intermediate colors during the transition?
6. How long does the color transition seem to take (estimate in ms)?

Then provide a CONCRETE implementation plan:
- What specific technique does BITCHORD use for the color transition?
- What animation duration would match their look?
- Any other details about the gradient style?`;

const zai = await ZAI.create();
const r = await zai.chat.completions.createVision({
  model: 'glm-4.5v',
  messages: [{ role: 'user', content: [{ type: 'text', text: prompt }, ...imgs] }],
  thinking: { type: 'enabled' },
});
const out = r.choices?.[0]?.message?.content || '(empty)';
console.log('\n========== ANALYSIS ==========\n');
console.log(out);
await fs.writeFile('/home/z/my-project/upload/bitchord-ref-analysis.txt', out);
console.log('\n✓ saved');
