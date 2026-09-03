#!/usr/bin/env node
import fs from 'node:fs/promises';
import ZAI from 'z-ai-web-dev-sdk';

const shot = '/home/z/my-project/upload/IMG20260903215052.jpg';
const img = await fs.readFile(shot);

const prompt = `This is a screenshot of a music player app's now-playing screen that the user wants to use as inspiration. Describe in EXTREME detail:

1. Layout from top to bottom — every element with its position and size in dp
2. Album art (cover) — what shape? what corner radius? what size?
3. Background — is it a gradient? what colors? is it sampled from the album art?
4. Title/artist/menu button — where are they positioned relative to the album art?
5. Play/pause button — where is it? what size?
6. Volume bar — does it exist? what style?
7. Shuffle/Repeat/Loop/Queue buttons — where are they? how arranged?
8. Is there a chevron-up arrow anywhere?
9. Is there a heart icon visible? where? is it a "double-tap to favorite" feature?
10. What's the overall vibe/style? (Apple Music-like, Material, custom, etc.)

Be CONCRETE with dp values.`;

const zai = await ZAI.create();
const r = await zai.chat.completions.createVision({
  model: 'glm-4.5v',
  messages: [{ role: 'user', content: [
    { type: 'text', text: prompt },
    { type: 'image_url', image_url: { url: `data:image/jpeg;base64,${img.toString('base64')}` } }
  ]}],
  thinking: { type: 'enabled' },
});
console.log(r.choices?.[0]?.message?.content);
