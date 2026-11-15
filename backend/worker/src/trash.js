import { Firestore } from './firestore.js';

export const RETENTION_MS = 30 * 24 * 60 * 60 * 1000;
const SUBS = { tasks: ['events', 'photos'], squares: [], camps: [], regions: [] };

// إزالة فعلية لما بقي في سلة المهملات أكثر من 30 يوماً (أو حُذف نهائياً) مع مجموعاته الفرعية
export async function purgeTrash(env, fetchFn = (...a) => fetch(...a), now = Date.now()) {
  const fs = new Firestore(env, fetchFn);
  const cutoff = now - RETENTION_MS;
  let n = 0;
  for (const col of ['tasks', 'camps', 'squares', 'regions']) {
    let rows = [];
    try { rows = await fs.query(col, [['deletedAt', 'LESS_THAN', cutoff]]); } catch { continue; }
    for (const doc of rows) {
      try {
        for (const sub of SUBS[col]) {
          const kids = await fs.listAll(`${col}/${doc.id}/${sub}`);
          for (const k of kids) await fs.remove(`${col}/${doc.id}/${sub}/${k.id}`);
        }
        await fs.remove(`${col}/${doc.id}`);
        n++;
      } catch { /* يُعاد في الدورة التالية */ }
    }
  }
  return n;
}
