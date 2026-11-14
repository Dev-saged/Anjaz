import { accessToken } from './google.js';
import { isLead, taskReader } from './policy.js';

export async function sendFcm(env, fs, uids, { title, body, kind, id, high = false }, fetchFn = (...a) => fetch(...a)) {
  const unique = [...new Set(uids)];
  const docs = await Promise.all(unique.map((u) => fs.get(`devices/${u}`)));
  const tokens = docs.filter((d) => d?.token).map((d) => d.token);
  if (!tokens.length) return 0;
  const bearer = await accessToken(env, Date.now(), fetchFn);
  const url = `https://fcm.googleapis.com/v1/projects/${env.FIREBASE_PROJECT_ID}/messages:send`;
  const results = await Promise.all(tokens.map((token) => fetchFn(url, {
    method: 'POST',
    headers: { authorization: `Bearer ${bearer}`, 'content-type': 'application/json' },
    body: JSON.stringify({
      message: {
        token,
        notification: { title, body },
        data: { kind, id },
        android: { priority: 'HIGH', notification: { channel_id: high ? 'sos' : 'default' } },
      },
    }),
  }).then((r) => r.ok).catch(() => false)));
  return results.filter(Boolean).length;
}

// مدراء النطاق: مدير/إداري المنطقة + مدير المربع + المركز
export async function leadsFor(fs, regionId, squareId) {
  const [region, central] = await Promise.all([
    fs.query('users', [['regionId', 'EQUAL', regionId]]),
    fs.query('users', [['role', 'EQUAL', 'CENTRAL']]),
  ]);
  return [...region, ...central]
    .filter((u) => isLead(u) && (u.role !== 'SQUARE_MANAGER' || u.squareId === squareId))
    .map((u) => u.id);
}

export async function targetsFor(fs, actorId, actor, kind, id) {
  if (kind === 'chat') {
    const room = await fs.get(`rooms/${id}`);
    if (!room || !room.members?.includes(actorId)) return null;
    return { uids: room.members.filter((m) => m !== actorId), title: 'رسالة جديدة', body: 'لديك رسالة جديدة', high: false };
  }
  if (kind === 'task') {
    const t = await fs.get(`tasks/${id}`);
    if (!t || !taskReader(actor, t, actorId)) return null;
    const uids = [t.assigneeId, t.createdBy].filter((u) => u && u !== actorId);
    return { uids, title: 'تحديث على مهمة', body: String(t.title || '').slice(0, 80), high: false };
  }
  if (kind === 'sos') {
    const s = await fs.get(`sos/${id}`);
    if (!s || s.senderId !== actorId) return null;
    const uids = (await leadsFor(fs, s.regionId, s.squareId)).filter((u) => u !== actorId);
    return { uids, title: 'نداء استغاثة', body: 'اضغط لفتح الموقع والتفاصيل', high: true };
  }
  return null;
}
