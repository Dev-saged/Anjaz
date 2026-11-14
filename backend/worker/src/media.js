import { HttpError } from './auth.js';

// جسر مؤقت: المرسل يرفع، والأعضاء ينزّلون ويؤكدون، وبعد تأكيد الجميع بساعة تُحذف النسخة من القاعدة
export const MEDIA_MAX = 6_000_000;
export const VIDEO_MAX = 10_000_000;
const CHUNK = 800_000;
export const HOLD_AFTER_ACKS_MS = 3_600_000;
export const MAX_AGE_MS = 14 * 86_400_000;
const TYPES = new Set([
  'image/jpeg', 'image/png', 'image/webp',
  'video/mp4', 'audio/mp4', 'audio/aac', 'audio/ogg', 'audio/webm', 'audio/mpeg',
  'application/pdf', 'application/zip', 'application/octet-stream', 'text/plain',
  'application/msword', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  'application/vnd.ms-excel', 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
]);

async function roomOf(ctx, scope) {
  const m = /^room:([A-Za-z0-9_-]{1,128})$/.exec(String(scope));
  if (!m) throw new HttpError(403, 'غير مسموح');
  const r = await ctx.fs.get(`rooms/${m[1]}`);
  if (!r || !r.members.includes(ctx.uid)) throw new HttpError(403, 'غير مسموح');
  return r;
}

export async function putMedia(ctx, limited) {
  const scope = ctx.request.headers.get('x-scope') || '';
  const mime = (ctx.request.headers.get('content-type') || '').split(';')[0].trim();
  if (!TYPES.has(mime)) throw new HttpError(415, 'نوع الملف غير مدعوم');
  await roomOf(ctx, scope);
  if (limited(ctx.uid, 'media', 30, ctx.now)) throw new HttpError(429, 'محاولات كثيرة');
  const buf = new Uint8Array(await ctx.request.arrayBuffer());
  if (buf.length === 0 || buf.length > (mime === 'video/mp4' ? VIDEO_MAX : MEDIA_MAX)) throw new HttpError(413, 'حجم الملف غير مقبول');
  const id = ctx.request.headers.get('x-idempotency-key') || crypto.randomUUID();
  if (!/^[A-Za-z0-9-]{8,64}$/.test(id)) throw new HttpError(400, 'مفتاح غير صالح');
  const exists = await ctx.env.DB.prepare('SELECT owner FROM media WHERE id = ?').bind(id).first();
  if (exists) {
    if (exists.owner !== ctx.uid) throw new HttpError(409, 'معرّف مستخدم');
    return { id };
  }
  const n = Math.ceil(buf.length / CHUNK);
  const stmts = [ctx.env.DB.prepare('INSERT INTO media (id, scope, owner, mime, size, created_at, chunks, delete_at) VALUES (?, ?, ?, ?, ?, ?, ?, NULL)')
    .bind(id, scope, ctx.uid, mime, buf.length, ctx.now, n)];
  for (let i = 0; i < n; i++) {
    stmts.push(ctx.env.DB.prepare('INSERT INTO media_chunks (media_id, idx, data) VALUES (?, ?, ?)').bind(id, i, buf.slice(i * CHUNK, (i + 1) * CHUNK)));
  }
  await ctx.env.DB.batch(stmts);
  return { id };
}

export async function getMedia(ctx, id) {
  const m = await ctx.env.DB.prepare('SELECT scope, mime, size FROM media WHERE id = ?').bind(id).first();
  if (!m) throw new HttpError(404, 'غير موجود');
  try { await roomOf(ctx, m.scope); } catch { throw new HttpError(404, 'غير موجود'); }
  const rows = (await ctx.env.DB.prepare('SELECT data FROM media_chunks WHERE media_id = ? ORDER BY idx').bind(id).all()).results;
  const out = new Uint8Array(m.size);
  let off = 0;
  for (const r of rows) { const b = new Uint8Array(r.data); out.set(b, off); off += b.length; }
  return new Response(out, {
    headers: { 'content-type': m.mime, 'cache-control': 'no-store', 'x-content-type-options': 'nosniff', 'content-disposition': 'attachment' },
  });
}

// تأكيد الاستلام: عند تأكيد كل الأعضاء غير المرسل يبدأ العدّ التنازلي للحذف
export async function ackMedia(ctx, id) {
  const m = await ctx.env.DB.prepare('SELECT scope, owner, delete_at FROM media WHERE id = ?').bind(id).first();
  if (!m) throw new HttpError(404, 'غير موجود');
  const room = await roomOf(ctx, m.scope).catch(() => { throw new HttpError(404, 'غير موجود'); });
  if (m.owner === ctx.uid) throw new HttpError(400, 'المرسل لا يؤكد');
  await ctx.env.DB.prepare('INSERT OR IGNORE INTO media_acks (media_id, uid, at) VALUES (?, ?, ?)').bind(id, ctx.uid, ctx.now).run();
  const acked = new Set((await ctx.env.DB.prepare('SELECT uid FROM media_acks WHERE media_id = ?').bind(id).all()).results.map((r) => r.uid));
  const need = room.members.filter((u) => u !== m.owner);
  const done = need.every((u) => acked.has(u));
  if (done && m.delete_at == null) {
    await ctx.env.DB.prepare('UPDATE media SET delete_at = ? WHERE id = ?').bind(ctx.now + HOLD_AFTER_ACKS_MS, id).run();
  }
  return { acked: acked.size, remaining: need.filter((u) => !acked.has(u)).length };
}

export async function purgeMedia(env, now = Date.now()) {
  const due = (await env.DB.prepare('SELECT id FROM media WHERE (delete_at IS NOT NULL AND delete_at <= ?) OR created_at < ?')
    .bind(now, now - MAX_AGE_MS).all()).results;
  for (const { id } of due) {
    await env.DB.batch([
      env.DB.prepare('DELETE FROM media_chunks WHERE media_id = ?').bind(id),
      env.DB.prepare('DELETE FROM media_acks WHERE media_id = ?').bind(id),
      env.DB.prepare('DELETE FROM media WHERE id = ?').bind(id),
    ]);
  }
  return due.length;
}
