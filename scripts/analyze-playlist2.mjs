#!/usr/bin/env node
import fs from 'node:fs/promises';
import ZAI from 'z-ai-web-dev-sdk';

const shots = [
  '/home/z/my-project/upload/Screenshot_2026-09-04-11-24-58-14_c41d99f29ddb98f2b2d1f5dfe2e7da20.jpg',
  '/home/z/my-project/upload/Screenshot_2026-09-04-11-29-07-82_40deb401b9ffe8e1df2f1cc5ba480b12.jpg',
];

const zai = await ZAI.create();

for (const path of shots) {
  const img = await fs.readFile(path);
  console.log('\n========== ' + path.split('/').pop() + ' ==========\n');

  const prompt = `This is a screenshot of the user's CURRENT ViTune-BC playlist screen with the new liquid glass cards. Please describe:

1. Are the top 4 buttons (Favorites, Offline, History, My Top 50) aligned with the playlist cards below them via an imaginary vertical line? Or are they offset? Be specific about the misalignment in dp.

2. Look closely at the playlist name text — is there a visible RECTANGLE shape behind the playlist name? Describe it (size, position, color, opacity).

3. Is there a visible GAP between the 4 playlist cards (in the center, where they meet)? Describe the gap — how big, what shape is the empty space?

4. The user wants to put a "gemini sparkle" icon in the center gap between the 4 playlists. Where exactly would that go? Describe the position.

5. Anything else about the layout, alignment, spacing that needs fixing?

Be CONCRETE with dp values.`;

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
