#!/usr/bin/env node
import fs from 'node:fs/promises';
import ZAI from 'z-ai-web-dev-sdk';

const shot = '/home/z/my-project/upload/Screenshot_2026-09-04-12-55-08-28_c41d99f29ddb98f2b2d1f5dfe2e7da20.jpg';
const img = await fs.readFile(shot);

const prompt = `This is the user's CURRENT playlist UI. The user says "I want the playlist name INSIDE the glass". 

Please describe:
1. Where is the playlist name currently? (above the glass cover? below it? on top of it?)
2. Where is the glass effect currently applied? (only on the cover? on the whole card?)
3. The user wants the playlist name to be INSIDE the glass — meaning the glass container should be bigger and the name should sit on top of the glass (below the cover image, but still inside the glass container).
4. Confirm the alignment is now correct (top 4 buttons aligned with playlist cards).

Be specific about the current layout and what "inside the glass" means visually.`;

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
