#!/usr/bin/env node
// Extract frames from the BITCHORD reference video and analyze them.

import { execSync } from 'node:child_process';
import fs from 'node:fs/promises';
import path from 'node:path';
import ZAI from 'z-ai-web-dev-sdk';

const VIDEO_PATH = '/home/z/my-project/upload/Record_2026-09-03-09-41-27.mp4';
const FRAMES_DIR = '/home/z/my-project/upload/bitchord_frames';
const NUM_FRAMES = 16; // ~one frame every ~1s of the 16.7s video — dense coverage

await fs.rm(FRAMES_DIR, { recursive: true, force: true });
await fs.mkdir(FRAMES_DIR, { recursive: true });

const duration = 16.776333;
const interval = duration / (NUM_FRAMES + 1);
for (let i = 1; i <= NUM_FRAMES; i++) {
  const t = (interval * i).toFixed(2);
  const out = path.join(FRAMES_DIR, `frame_${String(i).padStart(2, '0')}.jpg`);
  execSync(
    `ffmpeg -y -ss ${t} -i "${VIDEO_PATH}" -frames:v 1 -q:v 2 "${out}" -loglevel error`,
  );
  console.log(`✓ frame ${i} @ ${t}s`);
}

const files = (await fs.readdir(FRAMES_DIR)).filter((f) => f.endsWith('.jpg')).sort();
const images = await Promise.all(
  files.map(async (f) => {
    const buf = await fs.readFile(path.join(FRAMES_DIR, f));
    return {
      type: 'image_url',
      image_url: { url: `data:image/jpeg;base64,${buf.toString('base64')}` },
    };
  }),
);
console.log(`Loaded ${images.length} frames`);

const prompt = `These ${images.length} screenshots are taken from a 16-second screen recording of the BITCHORD music player app — the user wants to replicate this exact design in their own app (ViTune-BC, a ViTune fork written in Jetpack Compose).

For EACH screenshot, describe in extreme detail what's on screen — pay particular attention to the NOW PLAYING screen layout.

Then provide a CONCRETE IMPLEMENTATION SPEC covering:

1. **Now Playing screen layout (top-to-bottom)** — describe every element in vertical order
2. **Album art treatment** — exact shape, corner radius, shadow, any reflection/gloss, scale animation when paused
3. **Title + artist row** — typography (size, weight, color), alignment, what's on the right (heart icon? share?)
4. **Lyrics inline strip** — the single-line lyric that sits below the title; how it animates, color, the ">" chevron
5. **Progress / seek bar** — height, color, thumb shape, whether timestamps are shown (left/right positions)
6. **Main controls row** — previous / play-pause / next; sizes (dp), colors, shapes (circular button?), spacing
7. **Secondary controls row** — shuffle / repeat / loop / queue / more; positions and icons used
8. **Background** — solid color, gradient, blurred album art? Any dim/overlay?
9. **Top app bar** — back button, title, menu icons (if any)
10. **Color palette** — estimate hex for background, surface, accent, text primary/secondary
11. **Spacing & padding** — rough dp values for margins/paddings between elements
12. **Animations** — anything moving (album scale, lyrics fade, progress thumb pulse, play/pause morph)
13. **Unique UI patterns** — anything notable vs typical Material You music players

Be EXTREMELY detailed — the user has zero coding knowledge so I need to write the implementation files for them. Every visual detail matters. Estimate exact dp/sp values where possible.`;

console.log('Calling vision API…');
const zai = await ZAI.create();
const response = await zai.chat.completions.createVision({
  model: 'glm-4.5v',
  messages: [
    { role: 'user', content: [{ type: 'text', text: prompt }, ...images] },
  ],
  thinking: { type: 'enabled' },
});

const content = response.choices?.[0]?.message?.content || '(empty)';
console.log('\n========== BITCHORD VISION ANALYSIS ==========\n');
console.log(content);

await fs.writeFile('/home/z/my-project/upload/bitchord-analysis.txt', content);
console.log('\n✓ Saved to /home/z/my-project/upload/bitchord-analysis.txt');
