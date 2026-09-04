#!/usr/bin/env node
import fs from 'node:fs/promises';
import ZAI from 'z-ai-web-dev-sdk';

const shot = '/home/z/my-project/upload/Screenshot_2026-09-04-17-41-39-40_c41d99f29ddb98f2b2d1f5dfe2e7da20.jpg';
const img = await fs.readFile(shot);

const prompt = `This is the user's CURRENT playlist UI. They say "you just added the border...make it glossy effect...like the playlist cover is on top of a glass".

Please describe:
1. What does the current glass effect look like? Is it just a border? Or is there a glossy/translucent fill?
2. How can we make it look more "glossy" — like the cover is sitting ON TOP of a real glass surface?
3. Should there be a gradient highlight at the top (simulating light reflection on glass)?
4. Should the fill be more visible (translucent white)?
5. What specific changes would make it look like real glossy glass?

Be specific with values (alpha percentages, gradient stops, etc.)`;

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
