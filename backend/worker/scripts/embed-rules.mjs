// يضمّن نص قواعد Firestore داخل الـ Worker لينشرها بنفسه بعد كل نشر
import { readFileSync, writeFileSync } from 'node:fs';
const src = readFileSync(new URL('../../firebase/firestore.rules', import.meta.url), 'utf8');
writeFileSync(new URL('../src/rules.generated.js', import.meta.url), `// مولَّد من backend/firebase/firestore.rules بـ npm run rules — لا تعدّله يدوياً\nexport default ${JSON.stringify(src)};\n`);
