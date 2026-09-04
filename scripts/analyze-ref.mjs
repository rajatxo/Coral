#!/usr/bin/env node
import fs from 'node:fs/promises';
import ZAI from 'z-ai-web-dev-sdk';

const shot = '/home/z/my-project/upload/IMG_20260904_104409.jpg';
const img = await fs.readFile(shot);

const prompt = `This is a reference image (a drawing or sketch) showing the user's desired playlist card design — they want "interlocking liquid glass" / "glossy" effect.

Describe in EXTREME detail:
1. What's in this image?
2. The desired layout — interlocking blocks? Where's the playlist cover (white), where's the playlist name (yellow), where's the glass effect (black)?
3. How do the blocks "interlock"? Are they stacked, overlapping, puzzle-like?
4. Specific dp values, colors, shapes
5. How would you implement the "interlocking" effect in Jetpack Compose?
6. What makes this look "premium" / "glossy" / "next level"?`;

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
