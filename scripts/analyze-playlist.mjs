#!/usr/bin/env node
import fs from 'node:fs/promises';
import ZAI from 'z-ai-web-dev-sdk';

const shots = [
  '/home/z/my-project/upload/Screenshot_2026-09-04-10-31-02-88_c41d99f29ddb98f2b2d1f5dfe2e7da20.jpg',
  '/home/z/my-project/upload/IMG20260904103042.jpg',
  '/home/z/my-project/upload/IMG_2026-09-04_104409.jpg',
];

const zai = await ZAI.create();

for (const path of shots) {
  const img = await fs.readFile(path);
  console.log('\n========== ' + path.split('/').pop() + ' ==========\n');

  const prompt = `This is either: (a) the user's CURRENT playlist UI in ViTune-BC, OR (b) the user's hand-drawn mockup of what they want, OR (c) a reference image they want to copy.

Please describe in EXTREME detail:

1. What's in this image? (current UI, drawing, or reference?)
2. If current UI: describe the playlist cards — shape, size, layout, what's on them, what looks boring
3. If drawing: describe the desired layout — where's the playlist cover, where's the playlist name, where's the liquid glass effect, how do the blocks interlock?
4. If reference: describe the glossy/interlocking look in detail — colors, shapes, glass effect, layout
5. Specific dp values for spacing, sizes, corner radii
6. Colors used (hex estimates)
7. Any unique design elements worth implementing

Be CONCRETE with dp/percentage values.`;

  const r = await zai.chat.completions.createVision({
    model: 'glm-4.5v',
    messages: [{ role: 'user', content: [
      { type: 'text', text: prompt },
      { type: 'image_url', image_url: { url: `data:image/jpeg;base64,${img.toString('base64')}` } }
    ]}],
    thinking: { type: 'enabled' },
  });
  console.log(r.choices?.[0]?.message?.content);
}
