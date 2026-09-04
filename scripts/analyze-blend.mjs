#!/usr/bin/env node
import fs from 'node:fs/promises';
import ZAI from 'z-ai-web-dev-sdk';

const shot = '/home/z/my-project/upload/Picsart_26-09-05_00-04-53-494.jpg';
const img = await fs.readFile(shot);

const prompt = `This is a side-by-side comparison collage. LEFT = user's current app, RIGHT = SimpMusic reference.

Focus ONLY on the BACKGROUND BLENDING:

LEFT (current):
1. How does the album art transition to the dark background?
2. Is there a hard line/cut? Or a gradient?
3. Is the bottom half solid black? Or does it show blurred art colors?
4. Does the art "melt" into the background organically?

RIGHT (SimpMusic reference):
1. How does the album art transition to the dark background?
2. Is the blend SEAMLESS (zero hard lines)?
3. Does the bottom half show the art's colors (blurred/tinted)?
4. Does the art "melt" into the background organically?

CRITICAL: Describe the EXACT visual difference in the blending technique between left and right. What specific gradient/blur/alpha values would make the left match the right?

Also: what's wrong with the play button position? Where should it be?`;

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
