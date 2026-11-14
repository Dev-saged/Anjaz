import { Firestore } from './firestore.js';
import { leadsFor, sendFcm } from './notify.js';

const OPEN = ['NEW', 'SEEN', 'IN_PROGRESS', 'AWAITING_REVIEW'];
const WEEKDAYS = { Sun: 0, Mon: 1, Tue: 2, Wed: 3, Thu: 4, Fri: 5, Sat: 6 };

export function localTime(now, tz = 'Asia/Hebron') {
  const parts = Object.fromEntries(new Intl.DateTimeFormat('en-CA', {
    timeZone: tz, year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', hourCycle: 'h23', weekday: 'short',
  }).formatToParts(now).map((p) => [p.type, p.value]));
  return { day: `${parts.year}-${parts.month}-${parts.day}`, hour: Number(parts.hour), weekday: WEEKDAYS[parts.weekday] };
}

export function due(t, lt) {
  if (t.active !== true || t.lastDay === lt.day || lt.hour < (Number.isInteger(t.hour) ? t.hour : 7)) return false;
  return t.every === 'DAILY' || (t.every === 'WEEKLY' && t.weekday === lt.weekday);
}

export async function runRecurring(env, fetchFn = (...a) => fetch(...a), now = Date.now()) {
  const fs = new Firestore(env, fetchFn);
  const lt = localTime(now);
  const templates = await fs.query('templates', [['active', 'EQUAL', true]]);
  let created = 0;
  for (const t of templates) {
    if (!due(t, lt)) continue;
    await fs.patch(`templates/${t.id}`, { lastDay: lt.day });
    const id = await fs.add('tasks', {
      title: String(t.title).slice(0, 120), description: String(t.description || '').slice(0, 1000), priority: t.priority || 'NORMAL',
      status: 'NEW', createdBy: t.createdBy, regionId: t.regionId ?? null, squareId: t.squareId ?? null, assigneeId: t.assigneeId ?? null,
      deadline: now + (23 - lt.hour) * 3_600_000 + 59 * 60_000, createdAt: now, updatedAt: now, recurringId: t.id,
    });
    if (t.assigneeId) await sendFcm(env, fs, [t.assigneeId], { title: 'مهمة متكررة جديدة', body: String(t.title).slice(0, 80), kind: 'task', id }, fetchFn);
    created++;
  }
  return { created, scanned: templates.length };
}

export async function weeklyReport(env, fetchFn = (...a) => fetch(...a), now = Date.now()) {
  const fs = new Firestore(env, fetchFn);
  const lt = localTime(now);
  if (lt.weekday !== 4) return { skipped: true };
  const since = now - 7 * 86_400_000;
  const regions = await fs.listAll('regions');
  let sent = 0;
  for (const r of regions) {
    const tasks = await fs.query('tasks', [['regionId', 'EQUAL', r.id]]);
    const created = tasks.filter((t) => t.createdAt >= since).length;
    const done = tasks.filter((t) => t.status === 'APPROVED' && t.updatedAt >= since).length;
    const overdue = tasks.filter((t) => OPEN.includes(t.status) && typeof t.deadline === 'number' && t.deadline < now).length;
    await fs.set(`weekly/${r.id}_${lt.day}`, { regionId: r.id, day: lt.day, created, done, overdue, at: now });
    const leads = await leadsFor(fs, r.id, null);
    sent += await sendFcm(env, fs, leads, { title: 'تقرير الأسبوع جاهز', body: `أُنجز ${done} · أُنشئ ${created} · متأخر ${overdue}`, kind: 'report', id: `${r.id}_${lt.day}` }, fetchFn);
  }
  return { regions: regions.length, sent };
}
