import { Firestore } from './firestore.js';
import { leadsFor, sendFcm } from './notify.js';

const OPEN = ['NEW', 'SEEN', 'IN_PROGRESS', 'AWAITING_REVIEW'];
const HOUR = 3_600_000;

// قراءات محدودة: المهام المفتوحة فقط، والتصفية على الموعد داخل الذاكرة
export async function escalate(env, fetchFn = fetch, now = Date.now()) {
  const fs = new Firestore(env, fetchFn);
  const open = await fs.query('tasks', [['status', 'IN', OPEN]]);
  let escalated = 0; let reminded = 0;
  for (const t of open) {
    if (typeof t.deadline !== 'number') continue;
    if (t.deadline < now && !t.escalatedAt) {
      const leads = await leadsFor(fs, t.regionId, t.squareId);
      await sendFcm(env, fs, leads, { title: 'مهمة متأخرة', body: String(t.title).slice(0, 80), kind: 'task', id: t.id }, fetchFn);
      await fs.patch(`tasks/${t.id}`, { escalatedAt: now });
      await fs.add('audit', { action: 'task.escalated', targetId: t.id, regionId: t.regionId, actorId: 'system', at: now });
      escalated++;
    } else if (t.deadline >= now && t.deadline - now <= HOUR && !t.remindedAt && t.assigneeId) {
      await sendFcm(env, fs, [t.assigneeId], { title: 'تذكير بموعد مهمة', body: String(t.title).slice(0, 80), kind: 'task', id: t.id }, fetchFn);
      await fs.patch(`tasks/${t.id}`, { remindedAt: now });
      reminded++;
    }
  }
  return { escalated, reminded, scanned: open.length };
}
