#!/usr/bin/env node
// Extract key frames from the uploaded screen recording and analyze them
// with the z-ai vision API, since the CLI doesn't accept local paths directly.

import { execSync } from 'node:child_process';
import fs from 'node:fs/promises';
import path from 'node:path';
import ZAI from 'z-ai-web-dev-sdk';

const VIDEO_PATH = '/home/z/my-project/upload/Record_2026-09-03-09-25-01.mp4';
const FRAMES_DIR = '/home/z/my-project/upload/frames';
const NUM_FRAMES = 12; // one frame every ~2.5s of the 30s video

// 1) Clean + recreate frames directory
await fs.rm(FRAMES_DIR, { recursive: true, force: true });
await fs.mkdir(FRAMES_DIR, { recursive: true });

// 2) Extract frames at evenly spaced timestamps
const duration = 30.297178; // from ffprobe
const interval = duration / (NUM_FRAMES + 1);
for (let i = 1; i <= NUM_FRAMES; i++) {
  const t = (interval * i).toFixed(2);
  const out = path.join(FRAMES_DIR, `frame_${String(i).padStart(2, '0')}.jpg`);
  execSync(
    `ffmpeg -y -ss ${t} -i "${VIDEO_PATH}" -frames:v 1 -q:v 2 "${out}" -loglevel error`,
  );
  console.log(`✓ frame ${i} @ ${t}s`);
}

// 3) Read frames as base64
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

// 4) Build prompt
const prompt = `These ${images.length} screenshots are taken from a 30-second screen recording of a MUSIC PLAYER mobile app UI/UX. They are shown in chronological order.

For EACH screenshot, describe in extreme detail what's on screen. Then give an overall synthesis covering:

1. **Overall visual style** — colors, gradients, glossy/matte finishes, typography, font weights
2. **Mini player / now playing bar** — position, layout, components (album art, title, controls, progress)
3. **Playlist section** — the 'glossy look' the user mentioned; album art rendering, glass/blur effects, card shape
4. **Lyrics display** — location (right side?), synced/timed, typography, animation cues
5. **Timeline / progress bar** — shape, position, color, visual treatment
6. **Bottom navigation / tab bar** — items, icons, labels
7. **Album art treatment** — rounded corners, shadows, reflections, blurred backgrounds
8. **Color palette** — primary, accent, background colors (estimate hex codes)
9. **Animations / transitions** — anything moving (player, lyrics, scroll, sheets)
10. **Notable UI patterns** — anything unique vs typical Android music apps
11. **Action buttons** — like, share, queue, lyrics toggle, mini/maxi player toggle

Be thorough and concrete — this analysis will be used to implement features in another music app (ViTune fork).`;

// 5) Run vision analysis
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
console.log('\n========== VISION ANALYSIS ==========\n');
console.log(content);

await fs.writeFile(
  '/home/z/my-project/upload/vision-analysis.txt',
  content,
);
console.log('\n✓ Saved to /home/z/my-project/upload/vision-analysis.txt');
