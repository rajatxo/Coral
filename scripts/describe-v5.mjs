#!/usr/bin/env node
import fs from 'node:fs/promises';
import ZAI from 'z-ai-web-dev-sdk';

const shots = [
  '/home/z/my-project/upload/Screenshot_2026-09-03-18-35-09-89_46c373db02c2bd5fc908c769c4e6ba79.jpg',
];

const zai = await ZAI.create();

for (const path of shots) {
  const img = await fs.readFile(path);
  console.log('\n========== ' + path.split('/').pop() + ' ==========\n');

  const prompt = `This is a screenshot of a music player app's now-playing screen. Please describe in extreme detail:

1. Album art size and position — is it too big? Pixelated? Where is it on screen?
2. The blur background — is the blur visible? Strong enough?
3. Is there a hard seam between crisp art and blurred bg?
4. Is the chevron-up arrow button visible? If yes, where exactly? If no, is there ANY button at the bottom?
5. What's at the bottom of the screen — describe everything you see.
6. Are there black/empty areas anywhere?
7. Anything else broken?`;

  const r = await zai.chat.completions.createVision({
    model: 'glm-4.5v',
    messages: [{ role: 'user', content: [
      { type: 'text', text: prompt },
      { type: 'image_url', image_url: { url: `data:image/jpeg;base64,${img.toString('base64')}` } }
    ]}],
    thinking: { type: 'disabled' },
  });
  console.log(r.choices?.[0]?.message?.content);
}
