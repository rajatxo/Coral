#!/usr/bin/env node
import fs from 'node:fs/promises';
import ZAI from 'z-ai-web-dev-sdk';

const shots = [
  '/home/z/my-project/upload/Screenshot_2026-09-04-17-06-17-27_c41d99f29ddb98f2b2d1f5dfe2e7da20.jpg',
  '/home/z/my-project/upload/IMG_20260904_171138.jpg',
];

const zai = await ZAI.create();

for (const path of shots) {
  const img = await fs.readFile(path);
  console.log('\n========== ' + path.split('/').pop() + ' ==========\n');

  const prompt = `This is either the user's CURRENT playlist UI or a REFERENCE screenshot they want to copy.

Please describe in EXTREME detail:
1. Is this the current UI or the reference?
2. If current UI: What is the "rectangle thing" inside each playlist card? Describe it precisely — is it a separate box behind the text? Behind the cover? What color/opacity?
3. If reference: Describe the playlist cards — how does the glass effect look? Where is the playlist name? How far from the cover? What's the layout?
4. Describe the 4 top buttons (Favorites, Offline, History, My Top 50) — what do they look like?
5. What's the background color? (pure black or gradient?)
6. How are the playlist cards aligned with the top buttons?

Be very specific about the differences between the two screenshots.`;

  const r = await zai.chat.completions.createVision({
    model: 'glm-4.5v',
    messages: [{ role: 'user', content: [
      { type: 'text', text: prompt },
      { type: 'image_url', image_url: { url: `data:image/jpeg;base64,${img.toString('base64')}` } }
    ]}],
    thinking: { type: 'enabled' },
  });
  console.log(r.choices?.[0]?.message?.content);
}
