#!/usr/bin/env node
import fs from 'node:fs/promises';
import ZAI from 'z-ai-web-dev-sdk';

const img = await fs.readFile('/home/z/my-project/upload/Screenshot_2026-09-03-10-19-27-49_40deb401b9ffe8e1df2f1cc5ba480b12.jpg');

const prompt = `This is a GitHub repository file listing screenshot. List EVERY file and folder name you can see, exactly as written. Be exhaustive — include the file extensions (.kt, .txt, etc.). Output as a simple bullet list.`;

const zai = await ZAI.create();
const r = await zai.chat.completions.createVision({
  model: 'glm-4.5v',
  messages: [{ role: 'user', content: [
    { type: 'text', text: prompt },
    { type: 'image_url', image_url: { url: `data:image/jpeg;base64,${img.toString('base64')}` } }
  ]}],
  thinking: { type: 'disabled' },
});
console.log(r.choices?.[0]?.message?.content);
