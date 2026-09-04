#!/usr/bin/env node
import fs from 'node:fs/promises';
import ZAI from 'z-ai-web-dev-sdk';

const shot = '/home/z/my-project/upload/Picsart_26-09-04_23-11-53-115.jpg';
const img = await fs.readFile(shot);

const prompt = `This is a SIDE-BY-SIDE COLLAGE comparing the user's CURRENT app (left side) with the SimpMusic reference (right side).

Please describe in EXTREME detail the DIFFERENCES:

LEFT (current app):
1. Where are the shuffle/play/search buttons positioned? (high? low? what level on screen?)
2. Is there a song count text below the buttons?
3. How big is the sort capsule?
4. Is the album art immersive (fills screen) or small?
5. What's the background like? (solid black? blurred art? gradient?)

RIGHT (SimpMusic reference):
1. Where are the shuffle/play/search buttons positioned? (what level on screen?)
2. Is there a song count? Where is it?
3. How big is the sort capsule? Is the song count INSIDE the sort capsule?
4. Is the album art immersive? How big?
5. What's the background like? (pitch black? colored? gradient?)
6. Does the album art fade into the background seamlessly?

CRITICAL: Describe the exact position difference of the buttons between left and right. The user wants buttons at the SAME LEVEL as the right pic.

Also describe the sort capsule difference — the user wants the song count INSIDE the sort capsule on the right side.`;

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
