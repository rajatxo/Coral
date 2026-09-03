#!/usr/bin/env node
import { execSync } from 'node:child_process';
import fs from 'node:fs/promises';
import path from 'node:path';
import ZAI from 'z-ai-web-dev-sdk';

const VIDEO = '/home/z/my-project/upload/Record_2026-09-03-19-24-49.mp4';
const FRAMES = '/home/z/my-project/upload/v7_frames';
const N = 16;

await fs.rm(FRAMES, { recursive: true, force: true });
await fs.mkdir(FRAMES, { recursive: true });

const dur = 18.048867;
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

const prompt = `These ${imgs.length} screenshots show the user's CURRENT ViTune-BC build. They are frustrated. For EACH screenshot, describe:

1. Is there a DARK GRADIENT visible ON the album art itself (the cover)? Or is the cover showing in its original colors? Be very precise.
2. What's the position of the chevron-up arrow button?
3. Is the chevron-up arrow covering/blocking the 4-icon row (Shuffle/Repeat/Loop/Menu)? Can the user tap those icons?
4. Where exactly is the 4-icon row positioned vertically?
5. Is the album art's bottom edge fading naturally to transparent, or is there a black band/gradient on it?
6. Is there empty space anywhere?

Be CONCRETE with dp/percentage estimates.`;

const zai = await ZAI.create();
const r = await zai.chat.completions.createVision({
  model: 'glm-4.5v',
  messages: [{ role: 'user', content: [{ type: 'text', text: prompt }, ...imgs] }],
  thinking: { type: 'enabled' },
});
const out = r.choices?.[0]?.message?.content || '(empty)';
console.log('\n========== ANALYSIS ==========\n');
console.log(out);
await fs.writeFile('/home/z/my-project/upload/v7-analysis.txt', out);
console.log('\n✓ saved');
