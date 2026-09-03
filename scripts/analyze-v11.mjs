#!/usr/bin/env node
import { execSync } from 'node:child_process';
import fs from 'node:fs/promises';
import path from 'node:path';
import ZAI from 'z-ai-web-dev-sdk';

const VIDEO = '/home/z/my-project/upload/Record_2026-09-04-00-19-22.mp4';
const FRAMES = '/home/z/my-project/upload/v11_frames';
const N = 20;

await fs.rm(FRAMES, { recursive: true, force: true });
await fs.mkdir(FRAMES, { recursive: true });

const dur = 15.431422;
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

const prompt = `These ${imgs.length} screenshots show the user's CURRENT ViTune-BC build during song transitions. Focus on the COLOR TRANSITION ANIMATION.

For EACH frame, note:
1. The dominant background color (hex estimate)
2. Is this color a "loading/neutral" color or a "final" color from the album art?
3. How does it compare to the previous frame's color?

Then analyze the TRANSITION QUALITY:
1. Is the color transition smooth and flowing, or does it look janky/stepped?
2. Does the color "snap" abruptly or crossfade smoothly?
3. Is there a visible "grey flash" or "neutral flash" between songs?
4. How long does the transition take (estimate in ms)?
5. Does it match BITCHORD's smooth crossfade style?

Be CONCRETE about what's wrong with the animation flow.`;

const zai = await ZAI.create();
const r = await zai.chat.completions.createVision({
  model: 'glm-4.5v',
  messages: [{ role: 'user', content: [{ type: 'text', text: prompt }, ...imgs] }],
  thinking: { type: 'enabled' },
});
const out = r.choices?.[0]?.message?.content || '(empty)';
console.log('\n========== ANALYSIS ==========\n');
console.log(out);
await fs.writeFile('/home/z/my-project/upload/v11-analysis.txt', out);
console.log('\n✓ saved');
