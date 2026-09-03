#!/usr/bin/env node
import fs from 'node:fs/promises';
import ZAI from 'z-ai-web-dev-sdk';

const shots = [
  '/home/z/my-project/upload/Screenshot_2026-09-03-18-55-59-70_46c373db02c2bd5fc908c769c4e6ba79.jpg',
];

const zai = await ZAI.create();

for (const path of shots) {
  const img = await fs.readFile(path);
  console.log('\n========== ' + path.split('/').pop() + ' ==========\n');

  const prompt = `This is a screenshot of a music player app's now-playing screen. Describe in EXTREME detail:

1. Album art — where is it positioned? What % of screen height does it occupy? Is there a dark gradient overlay ON the album art itself? Describe the gradient on the art.
2. The blurred background — where does the blur start? Is the album art floating on top of the blur, or is the album art merged with the blur?
3. The chevron-up arrow button — is it visible? Where exactly is it positioned? Is it ABOVE or BELOW the 4-icon row (Shuffle/Repeat/Loop/Menu)?
4. The 4-icon row (Shuffle/Repeat/Loop/Menu) — where is it positioned vertically?
5. The dark gradient at the bottom of the SCREEN (not on the art) — does it exist? Where does it start?
6. Is there empty space anywhere? Describe the layout from top to bottom.`;

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
