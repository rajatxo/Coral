#!/usr/bin/env node
import fs from 'node:fs/promises';
import ZAI from 'z-ai-web-dev-sdk';

const shot = '/home/z/my-project/upload/IMG_20260904_115917.jpg';
const img = await fs.readFile(shot);

const prompt = `The user annotated this screenshot with red circles and a white bar to show issues with their playlist UI. Please describe:

1. What do the RED CIRCLES mark? What's visible there — is there a rectangle/box shape behind the playlist name text? Describe exactly what you see in those circled areas.

2. What does the WHITE BAR represent? The user said they put it to show alignment — is it showing that the playlist cards need to shift left/right to align with the top buttons?

3. Is there a star/sparkle icon visible in the center of the 4 playlist cards? (The user wants it removed completely)

4. Describe the current alignment between the top 4 buttons (Favorites, Offline, etc.) and the playlist cards below — are they aligned, or offset?

5. Anything else the user wants fixed?

Be very specific about what you see.`;

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
