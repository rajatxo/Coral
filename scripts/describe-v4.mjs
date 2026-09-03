#!/usr/bin/env node
import fs from 'node:fs/promises';
import ZAI from 'z-ai-web-dev-sdk';

const shots = [
  '/home/z/my-project/upload/Screenshot_2026-09-03-18-05-24-45_c1c1ab5b130ecbad99d6c7b0dce13cef.jpg',
  '/home/z/my-project/upload/Screenshot_2026-09-03-18-04-32-66_46c373db02c2bd5fc908c769c4e6ba79.jpg',
];

const zai = await ZAI.create();

for (const path of shots) {
  const img = await fs.readFile(path);
  console.log('\n========== ' + path.split('/').pop() + ' ==========\n');

  const prompt = `This is a screenshot of a music player app's now-playing screen. Please describe in extreme detail:

1. Album art size — is it too big? Is the image being stretched/pixelated?
2. Are the people/faces in the album art visible, or do they look cut off / vanished?
3. The blur background — is the blur visible? Strong enough? 
4. The blend between crisp art and blurred bg — is there a hard seam?
5. The dark gradient at the bottom — is text readable?
6. Position of the chevron-up button — where is it on screen, what's near it?
7. Anything else broken or missing?`;

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
