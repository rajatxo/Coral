#!/usr/bin/env node
import fs from 'node:fs/promises';
import ZAI from 'z-ai-web-dev-sdk';

const shot = '/home/z/my-project/upload/Screenshot_2026-09-04-13-16-38-40_c41d99f29ddb98f2b2d1f5dfe2e7da20.jpg';
const img = await fs.readFile(shot);

const prompt = `The user zoomed in on their playlist card. They say:
1. "You expanded the down part too much" — the area for the playlist name is too tall/wide
2. "You can see a box shape thing behind the text" — there's a visible rectangle/box behind the playlist name
3. "The box is also behind the playlist cover pic but it's not visible because I added the playlist cover"
4. "I want to remove the box shape, or make opacity zero"

Please describe in EXTREME detail:
1. What does the "box shape" look like? Is it the glass container's background? The border? A separate rectangle?
2. Where exactly is the box visible? (behind the text? around the cover? both?)
3. How much vertical space does the playlist name area take? Is it too tall?
4. What would fix this — removing the background? removing the border? both?
5. Estimate the dp values of the current spacing.

Be very specific.`;

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
