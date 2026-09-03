#!/usr/bin/env node
import fs from 'node:fs/promises';
import ZAI from 'z-ai-web-dev-sdk';

const shots = [
  '/home/z/my-project/upload/Screenshot_2026-09-03-10-50-26-49_46c373db02c2bd5fc908c769c4e6ba79.jpg',
  '/home/z/my-project/upload/Screenshot_2026-09-03-10-53-32-11_c1c1ab5b130ecbad99d6c7b0dce13cef.jpg',
];

const zai = await ZAI.create();

for (const path of shots) {
  const img = await fs.readFile(path);
  console.log('\n========== ' + path.split('/').pop() + ' ==========\n');

  const prompt = `This is a screenshot of a music player app's now-playing screen. Describe in EXTREME detail:

1. How is the album art positioned? (top half? full screen? what fraction?)
2. What's in the bottom half? Is it a blurred version of the album art? How blurred?
3. Where exactly does the crisp art END and the blurred art BEGIN? Is there a visible seam, or do they blend smoothly?
4. Where is the gradient/transition between crisp → blurred? (e.g., "starts at 50% screen height, fully blurred by 55%")
5. What's the layout of controls (title, artist, seekbar, buttons) in the bottom half — are they on top of the blurred art?
6. Any dark overlay on top of the blurred part? (for text legibility)
7. Estimate the BLUR RADIUS in dp (e.g., 25dp, 50dp)
8. Color treatment — is the blurred part the same colors as the art, or tinted/darkened?

Be CONCRETE with dp/percentage values. This will be used to implement the same effect in Jetpack Compose.`;

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
