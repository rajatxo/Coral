#!/usr/bin/env node
import fs from 'node:fs/promises';
import ZAI from 'z-ai-web-dev-sdk';

const shot = '/home/z/my-project/upload/Screenshot_2026-09-04-00-56-01-29_46c373db02c2bd5fc908c769c4e6ba79.jpg';
const img = await fs.readFile(shot);

const prompt = `This is a screenshot of the user's CURRENT ViTune-BC build. Please analyze:

1. Album art alignment — Is the LEFT edge of the album art aligned with the LEFT edge of the text below it (song name, artist, lyrics, volume bar)? Is the RIGHT edge of the album art aligned with the RIGHT edge of the seekbar/buttons below it? Describe any misalignment in dp.

2. Text readability — Is the song name, artist name, and lyrics text readable against the background? Are there any areas where the text is hard to read (e.g., over bright/whitish album art)?

3. "Now Playing" header — What's the current size and text? Is it readable?

4. Seekbar — Where is it? Is there a song duration number shown below it? If yes, where? If no, where would be the best place to add it?

5. Shuffle/Repeat/Loop/Queue buttons — Are they all the same size? Do they all have the same circle background effect when clicked? Describe each button's state.

6. Menu button (3 dots) — Where is it? Does it have a circle effect when clicked?

7. Anything else worth noting about the layout, spacing, alignment, or readability?

Be CONCRETE with dp/percentage values for fixes.`;

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
