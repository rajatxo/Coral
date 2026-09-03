#!/usr/bin/env node
import fs from 'node:fs/promises';
import path from 'node:path';
import ZAI from 'z-ai-web-dev-sdk';

const FRAMES = '/home/z/my-project/upload/v10_frames';
const files = (await fs.readdir(FRAMES)).filter(f => f.endsWith('.jpg')).sort();
console.log(`Loaded ${files.length} frames`);

const imgs = await Promise.all(files.map(async f => {
  const b = await fs.readFile(path.join(FRAMES, f));
  return { type: 'image_url', image_url: { url: `data:image/jpeg;base64,${b.toString('base64')}` } };
}));

const prompt = `These ${imgs.length} screenshots show the user's CURRENT ViTune-BC build. They are frustrated about several issues. For EACH screenshot, describe:

1. Are the Shuffle/Repeat/Loop/Queue buttons clickable? Or is the Queue button taking up the full width and blocking the others?
2. Is the Queue button only working on swipe-up (not on tap)?
3. Is the gradient background visible through the album art (transparent)?
4. What's the album art size? Is it aligned with the song name/artist/lyrics/volume bar?
5. Is there a shadow around the album art boundary?
6. Where is the "Now Playing" header? Is it bright white or faded?
7. Is the gradient background vibrant/blue enough, or is it dull/transparent?
8. Are the seekbar and volume bar smooth or laggy when dragging?

Be CONCRETE with dp/percentage estimates for fixes.`;

const zai = await ZAI.create();
const r = await zai.chat.completions.createVision({
  model: 'glm-4.5v',
  messages: [{ role: 'user', content: [{ type: 'text', text: prompt }, ...imgs] }],
  thinking: { type: 'enabled' },
});
const out = r.choices?.[0]?.message?.content || '(empty)';
console.log('\n========== ANALYSIS ==========\n');
console.log(out);
await fs.writeFile('/home/z/my-project/upload/v10-analysis.txt', out);
console.log('\n✓ saved');
