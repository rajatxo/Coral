#!/usr/bin/env node
import { execSync } from 'node:child_process';
import fs from 'node:fs/promises';
import path from 'node:path';
import ZAI from 'z-ai-web-dev-sdk';

const VIDEO = '/home/z/my-project/upload/Record_2026-09-04-08-46-13.mp4';
const FRAMES = '/home/z/my-project/upload/release_frames';
const N = 20;

await fs.rm(FRAMES, { recursive: true, force: true });
await fs.mkdir(FRAMES, { recursive: true });

const dur = 14.903878;
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

const prompt = `These ${imgs.length} screenshots are from the user's RELEASE build of ViTune-BC, showing song transitions. Focus on:

1. Is there an EMPTY BOX/PLACEHOLDER shape that appears before the album art thumbnail loads when switching songs? Describe it precisely — what does it look like, where is it, how long does it appear?
2. Is the gradient animation smooth? Describe the color transition.
3. Does the gradient transition happen BEFORE or AFTER the thumbnail loads? Is there a moment where the gradient is new but the thumbnail is still old/empty?
4. What's the order of events when changing songs?

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
await fs.writeFile('/home/z/my-project/upload/release-analysis.txt', out);
console.log('\n✓ saved');
