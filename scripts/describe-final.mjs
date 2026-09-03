#!/usr/bin/env node
import fs from 'node:fs/promises';
import ZAI from 'z-ai-web-dev-sdk';

const shots = [
  '/home/z/my-project/upload/IMG_20260904_014451_320.jpg',
  '/home/z/my-project/upload/IMG_20260904_014451_774.jpg',
];

const zai = await ZAI.create();

for (const path of shots) {
  const img = await fs.readFile(path);
  console.log('\n========== ' + path.split('/').pop() + ' ==========\n');

  const prompt = `This is a screenshot of the user's ViTune-BC music player app. Describe what you see — the now playing screen, layout, colors, alignment, any issues or things that look great. Be specific about dp/percentage values for any visual problems.`;

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
