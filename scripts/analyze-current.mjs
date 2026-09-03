#!/usr/bin/env node
import { execSync } from 'node:child_process';
import fs from 'node:fs/promises';
import path from 'node:path';
import ZAI from 'z-ai-web-dev-sdk';

const VIDEO_PATH = '/home/z/my-project/upload/Record_2026-09-03-11-59-31.mp4';
const FRAMES_DIR = '/home/z/my-project/upload/current_frames';
const NUM_FRAMES = 18;

await fs.rm(FRAMES_DIR, { recursive: true, force: true });
await fs.mkdir(FRAMES_DIR, { recursive: true });

const duration = 21.780733;
const interval = duration / (NUM_FRAMES + 1);
for (let i = 1; i <= NUM_FRAMES; i++) {
  const t = (interval * i).toFixed(2);
  const out = path.join(FRAMES_DIR, `frame_${String(i).padStart(2, '0')}.jpg`);
  execSync(`ffmpeg -y -ss ${t} -i "${VIDEO_PATH}" -frames:v 1 -q:v 2 "${out}" -loglevel error`);
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

const prompt = `These ${images.length} screenshots are taken from a 21-second screen recording of the user's CURRENT ViTune-BC app build — they want me to compare this to BITCHORD's design and identify what's wrong.

For EACH screenshot describe what's on screen — especially the Now Playing view.

Then provide a CRITICAL BUG REPORT:

1. **Album art coverage** — does the album art cover the FULL screen? (Top status bar, bottom nav bar, edges?) If there are black bars or empty regions, describe them.
2. **Blend between crisp top and blurred bottom** — is there a visible hard line/seam? Does the transition look natural or jarring? Where exactly does the seam appear (what % of screen height)?
3. **What's at the bottom-right of the controls row** — is there a queue icon? a menu icon (3 dots)? something else? Describe it.
4. **Lyrics interaction** — when user taps the lyric strip, what happens? Does it expand inline over the album art? Does it open a separate screen? Are there controls on the expanded view (search button, settings)?
5. **Lyrics expanded view** — describe the layout: where's the album art thumbnail, are there action buttons (search, edit, settings), is there a close button?
6. **Anything else broken or missing** compared to BITCHORD's signature look (Apple Music style).

Be EXTREMELY specific with dp/percent estimates. The user has zero coding knowledge so I need to write fixes for them.`;

console.log('Calling vision API…');
const zai = await ZAI.create();
const response = await zai.chat.completions.createVision({
  model: 'glm-4.5v',
  messages: [{ role: 'user', content: [{ type: 'text', text: prompt }, ...images] }],
  thinking: { type: 'enabled' },
});

const content = response.choices?.[0]?.message?.content || '(empty)';
console.log('\n========== ANALYSIS ==========\n');
console.log(content);

await fs.writeFile('/home/z/my-project/upload/current-analysis.txt', content);
console.log('\n✓ Saved');
