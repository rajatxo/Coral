#!/usr/bin/env node
import { execSync } from 'node:child_process';
import fs from 'node:fs/promises';
import path from 'node:path';
import ZAI from 'z-ai-web-dev-sdk';

const VIDEO = '/home/z/my-project/upload/Record_2026-09-03-21-22-07.mp4';
const SHOT = '/home/z/my-project/upload/Screenshot_2026-09-03-18-04-32-66_46c373db02c2bd5fc908c769c4e6ba79.jpg';
const FRAMES = '/home/z/my-project/upload/v9_frames';
const N = 14;

await fs.rm(FRAMES, { recursive: true, force: true });
await fs.mkdir(FRAMES, { recursive: true });

const dur = 12.801522;
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

// Add the reference screenshot at the end
const shotBytes = await fs.readFile(SHOT);
imgs.push({ type: 'image_url', image_url: { url: `data:image/jpeg;base64,${shotBytes.toString('base64')}` } });

console.log(`Loaded ${imgs.length} images (last is reference)`);

const prompt = `These ${imgs.length - 1} screenshots show the user's CURRENT ViTune-BC build (frustrated because buttons are not clickable). The LAST image is a REFERENCE screenshot of the look they want.

For EACH current-build screenshot, describe:
1. Are the buttons (Shuffle/Repeat/Loop/Menu) visible? Where exactly are they positioned vertically?
2. Is the chevron-up arrow visible? Where?
3. Are the buttons overlapping each other or overlapping the chevron-up?
4. Is there empty space anywhere?
5. What's blocking the buttons from being clickable?

Then for the REFERENCE screenshot (last image), describe:
- The exact layout (top to bottom): album art size/position, where the controls are, where the chevron-up is, etc.
- What makes the buttons clickable in the reference (spacing, no overlap, etc.)
- The relationship between album art size and button positions

Finally, provide a CONCRETE FIX PLAN with specific dp values:
- What size should the album art be?
- What padding/spacing between elements?
- Where exactly should each control sit?`;

const zai = await ZAI.create();
const r = await zai.chat.completions.createVision({
  model: 'glm-4.5v',
  messages: [{ role: 'user', content: [{ type: 'text', text: prompt }, ...imgs] }],
  thinking: { type: 'enabled' },
});
const out = r.choices?.[0]?.message?.content || '(empty)';
console.log('\n========== ANALYSIS ==========\n');
console.log(out);
await fs.writeFile('/home/z/my-project/upload/v9-analysis.txt', out);
console.log('\n✓ saved');
