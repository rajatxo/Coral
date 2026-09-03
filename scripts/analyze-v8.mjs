#!/usr/bin/env node
import { execSync } from 'node:child_process';
import fs from 'node:fs/promises';
import path from 'node:path';
import ZAI from 'z-ai-web-dev-sdk';

const VIDEO = '/home/z/my-project/upload/Record_2026-09-03-20-05-59.mp4';
const FRAMES = '/home/z/my-project/upload/v8_frames';
const N = 16;

await fs.rm(FRAMES, { recursive: true, force: true });
await fs.mkdir(FRAMES, { recursive: true });

const dur = 15.807422;
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

const prompt = `These ${imgs.length} screenshots are from the user's CURRENT ViTune-BC build. The user is frustrated and wants it to look like "before" — meaning the early version where the album art was a SQUARE in the upper portion, with the blurred background visible all around it. 

For EACH screenshot, describe:

1. Is the album art a square, a horizontal rectangle, or full-screen? What's the aspect ratio?
2. Where is the album art positioned (top, center, full screen)?
3. Is there a visible hard edge/seam where the album art ends?
4. Is there a black gradient or dark band ON the album art itself?
5. Where are the controls (play/pause/skip/4-icon row/chevron-up) positioned?
6. Is the chevron-up arrow visible? Where?
7. What's broken or missing?
8. Compare to a "standard" music player layout (album art square at top, controls below).

Be CONCRETE.`;

const zai = await ZAI.create();
const r = await zai.chat.completions.createVision({
  model: 'glm-4.5v',
  messages: [{ role: 'user', content: [{ type: 'text', text: prompt }, ...imgs] }],
  thinking: { type: 'enabled' },
});
const out = r.choices?.[0]?.message?.content || '(empty)';
console.log('\n========== ANALYSIS ==========\n');
console.log(out);
await fs.writeFile('/home/z/my-project/upload/v8-analysis.txt', out);
console.log('\n✓ saved');
