import { HttpError, verifyIdToken } from './auth.js';
import { readBackup, runBackup } from './backup.js';
import { escalate } from './cron.js';
import { ackMedia, getMedia, purgeMedia, putMedia } from './media.js';
import { Firestore } from './firestore.js';
import { Identity } from './identity.js';
import { sendFcm, targetsFor } from './notify.js';
import {
  canCreateUser, canManageUser, isActive, isLead, randomPassword, shapeOk, validPassword, validUsername,
} from './policy.js';

const buckets = new Map();

function json(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store', 'x-content-type-options': 'nosniff' },
  });
}

function limited(uid, key, max, now) {
  const k = `${uid}:${key}`;
  const b = (buckets.get(k) || []).filter((t) => now - t < 60_000);
  if (b.length >= max) return true;
  b.push(now); buckets.set(k, b);
  return false;
}

async function body(request) {
  try { return await request.json(); } catch { throw new HttpError(400, 'طلب غير صالح'); }
}

const str = (v, max) => typeof v === 'string' && v.trim().length > 0 && v.length <= max;

async function profile(fs, uid) {
  const p = await fs.get(`users/${uid}`);
  if (!isActive(p)) throw new HttpError(403, 'الحساب غير مفعّل');
  return p;
}

async function audit(fs, actorId, action, targetId, regionId, now) {
  await fs.add('audit', { actorId, action, targetId, regionId: regionId ?? null, at: now });
}

async function createUser(ctx) {
  const b = await body(ctx.request);
  const target = { role: b.role, regionId: b.regionId ?? null, squareId: b.squareId ?? null };
  if (!validUsername(b.username)) throw new HttpError(400, 'اسم المستخدم: 3-32 حرفاً لاتينياً أو أرقاماً، أو بريد كامل مثل name@domain.com');
  if (!validPassword(b.password)) throw new HttpError(400, 'كلمة المرور 8 أحرف على الأقل');
  if (!str(b.displayName, 80)) throw new HttpError(400, 'الاسم مطلوب');
  if (!shapeOk(target) || !canCreateUser(ctx.actor, target)) throw new HttpError(403, 'ليس لديك صلاحية إنشاء هذا الحساب');
  const created = await ctx.identity.createUser(b.username, b.password, b.displayName.trim());
  if (created.error) throw new HttpError(409, 'اسم المستخدم مستخدم مسبقاً');
  try {
    await ctx.fs.set(`users/${created.uid}`, {
      username: b.username, displayName: b.displayName.trim(), ...target, active: true, createdBy: ctx.uid, updatedAt: ctx.now,
    });
  } catch (e) {
    await ctx.identity.setDisabled(created.uid, true);
    throw e;
  }
  await audit(ctx.fs, ctx.uid, 'user.create', created.uid, target.regionId, ctx.now);
  return json({ uid: created.uid }, 201);
}

async function manageTarget(ctx, uid) {
  const t = await ctx.fs.get(`users/${uid}`);
  if (!t || !canManageUser(ctx.actor, t)) throw new HttpError(403, 'ليس لديك صلاحية على هذا الحساب');
  return t;
}

async function resetPassword(ctx, uid) {
  if (limited(ctx.uid, 'reset', 10, ctx.now)) throw new HttpError(429, 'محاولات كثيرة');
  const t = await manageTarget(ctx, uid);
  const b = await body(ctx.request).catch(() => ({}));
  const password = b.password ?? randomPassword();
  if (!validPassword(password)) throw new HttpError(400, 'كلمة المرور 8 أحرف على الأقل');
  await ctx.identity.setPassword(uid, password);
  await audit(ctx.fs, ctx.uid, 'user.reset_password', uid, t.regionId, ctx.now);
  return json({ password: b.password ? undefined : password });
}

async function setActive(ctx, uid) {
  const b = await body(ctx.request);
  if (typeof b.active !== 'boolean') throw new HttpError(400, 'طلب غير صالح');
  if (uid === ctx.uid) throw new HttpError(400, 'لا يمكنك تعطيل حسابك');
  const t = await manageTarget(ctx, uid);
  await ctx.identity.setDisabled(uid, !b.active);
  await ctx.fs.patch(`users/${uid}`, { active: b.active, updatedAt: ctx.now });
  await audit(ctx.fs, ctx.uid, b.active ? 'user.enable' : 'user.disable', uid, t.regionId, ctx.now);
  return json({ ok: true });
}

async function membersInScope(ctx, ids) {
  if (!Array.isArray(ids) || ids.length < 1 || ids.length > 40 || ids.some((i) => typeof i !== 'string')) {
    throw new HttpError(400, 'قائمة الأعضاء غير صالحة');
  }
  const unique = [...new Set([...ids, ctx.uid])];
  const docs = await Promise.all(unique.map((u) => ctx.fs.get(`users/${u}`)));
  docs.forEach((d) => {
    if (!isActive(d)) throw new HttpError(400, 'عضو غير صالح');
    if (ctx.actor.role !== 'CENTRAL' && d.role !== 'CENTRAL' && d.regionId !== ctx.actor.regionId) throw new HttpError(403, 'عضو من منطقة أخرى');
  });
  return unique;
}

async function createGroup(ctx, kind) {
  if (!isLead(ctx.actor)) throw new HttpError(403, 'غير مسموح');
  const b = await body(ctx.request);
  if (!str(b.name, 80)) throw new HttpError(400, 'الاسم مطلوب');
  const members = await membersInScope(ctx, b.members);
  const id = crypto.randomUUID();
  if (kind === 'room') {
    await ctx.fs.set(`rooms/${id}`, { type: 'group', name: b.name.trim(), members, regionId: ctx.actor.regionId, createdBy: ctx.uid, lastMessage: '', lastAt: ctx.now });
  } else {
    await ctx.fs.set(`meetings/${id}`, { title: b.name.trim(), members, regionId: ctx.actor.regionId, createdBy: ctx.uid, createdAt: ctx.now });
  }
  await audit(ctx.fs, ctx.uid, `${kind}.create`, id, ctx.actor.regionId, ctx.now);
  return json({ id }, 201);
}

async function notify(ctx) {
  if (limited(ctx.uid, 'notify', 60, ctx.now)) throw new HttpError(429, 'محاولات كثيرة');
  const b = await body(ctx.request);
  if (!str(b.id, 128) || !['chat', 'task', 'sos'].includes(b.kind)) throw new HttpError(400, 'طلب غير صالح');
  const t = await targetsFor(ctx.fs, ctx.uid, ctx.actor, b.kind, b.id);
  if (!t) throw new HttpError(403, 'غير مسموح');
  const sent = await sendFcm(ctx.env, ctx.fs, t.uids, { ...t, kind: b.kind, id: b.id }, ctx.fetch);
  return json({ sent });
}

function centralOnly(ctx) {
  if (ctx.actor.role !== 'CENTRAL') throw new HttpError(403, 'للإدارة المركزية فقط');
}

export async function route(ctx, method, path) {
  let m;
  if (method === 'POST' && path === '/v1/users') return createUser(ctx);
  if (method === 'POST' && (m = path.match(/^\/v1\/users\/([^/]+)\/password$/))) return resetPassword(ctx, m[1]);
  if (method === 'POST' && (m = path.match(/^\/v1\/users\/([^/]+)\/active$/))) return setActive(ctx, m[1]);
  if (method === 'POST' && path === '/v1/rooms/group') return createGroup(ctx, 'room');
  if (method === 'POST' && path === '/v1/meetings') return createGroup(ctx, 'meeting');
  if (method === 'POST' && path === '/v1/notify') return notify(ctx);
  if (method === 'PUT' && path === '/v1/media') return json(await putMedia(ctx, limited), 201);
  if (method === 'GET' && (m = path.match(/^\/v1\/media\/([A-Za-z0-9-]+)$/))) return getMedia(ctx, m[1]);
  if (method === 'POST' && (m = path.match(/^\/v1\/media\/([A-Za-z0-9-]+)\/ack$/))) return json(await ackMedia(ctx, m[1]));
  if (method === 'POST' && path === '/v1/backups/run') { centralOnly(ctx); return json(await runBackup(ctx.env, ctx.fetch, ctx.now)); }
  if (method === 'GET' && path === '/v1/backups') {
    centralOnly(ctx);
    const r = await ctx.env.DB.prepare('SELECT id, created_at, bytes FROM backups ORDER BY created_at DESC LIMIT 30').all();
    return json({ backups: r.results });
  }
  if (method === 'GET' && (m = path.match(/^\/v1\/backups\/([A-Za-z0-9-]+)$/))) {
    centralOnly(ctx);
    const bytes = await readBackup(ctx.env, m[1]);
    if (!bytes) throw new HttpError(404, 'غير موجود');
    return new Response(bytes, { headers: { 'content-type': 'application/gzip', 'content-disposition': `attachment; filename="anjaz-${m[1]}.json.gz"` } });
  }
  throw new HttpError(404, 'غير موجود');
}

export async function handle(request, env, deps = {}) {
  const fetchFn = deps.fetch || ((...a) => fetch(...a));
  const now = deps.now ?? Date.now();
  const url = new URL(request.url);
  try {
    if (url.pathname === '/v1/health') return json({ ok: true });
    const auth = request.headers.get('authorization') || '';
    if (!auth.startsWith('Bearer ')) throw new HttpError(401, 'غير مصرّح');
    const uid = await verifyIdToken(auth.slice(7), env.FIREBASE_PROJECT_ID, { fetchFn, now });
    const fs = new Firestore(env, fetchFn);
    const actor = await profile(fs, uid);
    return await route({ request, env, fs, identity: new Identity(env, fetchFn), fetch: fetchFn, uid, actor, now }, request.method, url.pathname);
  } catch (e) {
    if (e instanceof HttpError) return json({ error: e.message }, e.status);
    return json({ error: 'خطأ داخلي' }, 500);
  }
}

export default {
  fetch: (request, env) => handle(request, env),
  async scheduled(event, env, ctx) {
    if (event.cron === '0 21 * * *') {
      ctx.waitUntil((async () => {
        await runBackup(env);
        await purgeMedia(env);
      })());
    } else {
      ctx.waitUntil((async () => { await escalate(env); await purgeMedia(env); })());
    }
  },
};
