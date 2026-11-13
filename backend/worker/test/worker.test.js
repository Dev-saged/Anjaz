import { test } from 'node:test';
import assert from 'node:assert/strict';
import { gunzipSync } from 'node:zlib';
import { handle } from '../src/index.js';
import { verifyIdToken, resetKeyCache } from '../src/auth.js';
import { canCreateUser, canManageUser } from '../src/policy.js';
import { escalate } from '../src/cron.js';
import { runBackup, readBackup } from '../src/backup.js';
import { world, idToken, PROJECT, NOW } from './harness.js';

const users = {
  central: { role: 'CENTRAL', regionId: null, squareId: null },
  rmA: { role: 'REGION_MANAGER', regionId: 'A', squareId: null },
  smA1: { role: 'SQUARE_MANAGER', regionId: 'A', squareId: 'A1' },
  staffA1: { role: 'FIELD_STAFF', regionId: 'A', squareId: 'A1' },
  staffA2: { role: 'FIELD_STAFF', regionId: 'A', squareId: 'A2' },
  rmB: { role: 'REGION_MANAGER', regionId: 'B', squareId: null },
  staffB1: { role: 'FIELD_STAFF', regionId: 'B', squareId: 'B1' },
  ghost: { role: 'FIELD_STAFF', regionId: 'A', squareId: 'A1', active: false },
};
const newUser = (o = {}) => ({ username: 'sami', password: 'pass1234', displayName: 'سامي', role: 'FIELD_STAFF', regionId: 'A', squareId: 'A1', ...o });

test('التحقق من رمز الدخول', async () => {
  const w = await world(users);
  const f = w.fetch;
  assert.equal(await verifyIdToken(await idToken(w.k, 'rmA'), PROJECT, { fetchFn: f, now: NOW }), 'rmA');
  await assert.rejects(verifyIdToken(await idToken(w.k, 'rmA', { aud: 'other' }), PROJECT, { fetchFn: f, now: NOW }));
  await assert.rejects(verifyIdToken(await idToken(w.k, 'rmA', { exp: 1 }), PROJECT, { fetchFn: f, now: NOW }));
  const t = await idToken(w.k, 'rmA'); const bad = t.slice(0, -4) + 'AAAA';
  await assert.rejects(verifyIdToken(bad, PROJECT, { fetchFn: f, now: NOW }));
  const [h, , s] = t.split('.'); const forged = `${h}.${Buffer.from(JSON.stringify({ iss: `https://securetoken.google.com/${PROJECT}`, aud: PROJECT, sub: 'central', iat: 1, exp: 9e9 })).toString('base64url')}.${s}`;
  await assert.rejects(verifyIdToken(forged, PROJECT, { fetchFn: f, now: NOW }));
  resetKeyCache();
});

test('مصفوفة صلاحيات الإنشاء والإدارة', () => {
  const A = (r, reg = 'A', sq = null) => ({ role: r, regionId: reg, squareId: sq, active: true });
  assert.ok(canCreateUser(A('CENTRAL', null), A('REGION_MANAGER', 'B')));
  assert.ok(canCreateUser(A('CENTRAL', null), A('CENTRAL', null)));
  assert.ok(canCreateUser(A('REGION_MANAGER'), A('FIELD_STAFF', 'A', 'A2')));
  assert.ok(!canCreateUser(A('REGION_MANAGER'), A('FIELD_STAFF', 'B', 'B1')));
  assert.ok(!canCreateUser(A('REGION_MANAGER'), A('REGION_MANAGER')));
  assert.ok(!canCreateUser(A('REGION_MANAGER'), A('CENTRAL', null)));
  assert.ok(canCreateUser(A('SQUARE_MANAGER', 'A', 'A1'), A('FIELD_STAFF', 'A', 'A1')));
  assert.ok(!canCreateUser(A('SQUARE_MANAGER', 'A', 'A1'), A('FIELD_STAFF', 'A', 'A2')));
  assert.ok(!canCreateUser(A('FIELD_STAFF', 'A', 'A1'), A('FIELD_STAFF', 'A', 'A1')));
  assert.ok(!canCreateUser(A('CENTRAL', null), { role: 'FIELD_STAFF', regionId: 'A', squareId: null }));
  assert.ok(!canCreateUser({ ...A('CENTRAL', null), active: false }, A('FIELD_STAFF', 'A', 'A1')));
  assert.ok(canManageUser(A('REGION_MANAGER'), A('SQUARE_MANAGER', 'A', 'A1')));
  assert.ok(!canManageUser(A('SQUARE_MANAGER', 'A', 'A1'), A('SQUARE_MANAGER', 'A', 'A1')));
  assert.ok(!canManageUser(A('REGION_MANAGER'), A('FIELD_STAFF', 'B', 'B1')));
});

test('إنشاء الحسابات: النجاح والرفض', async () => {
  const w = await world(users);
  const post = (who, body) => w.call(handle, who, 'POST', '/v1/users', body);
  assert.equal((await w.call(handle, null, 'POST', '/v1/users', newUser())).status, 401);
  assert.equal((await post('ghost', newUser())).status, 403);
  assert.equal((await post('staffA1', newUser({ username: 'x1x1' }))).status, 403);
  assert.equal((await post('rmA', newUser({ regionId: 'B', squareId: 'B1' }))).status, 403);
  assert.equal((await post('rmA', newUser({ role: 'REGION_MANAGER', squareId: null }))).status, 403);
  assert.equal((await post('rmA', newUser({ password: 'short' }))).status, 400);
  assert.equal((await post('rmA', newUser({ username: 'Bad Name' }))).status, 400);
  const ok = await post('rmA', newUser());
  assert.equal(ok.status, 201);
  const { uid } = await ok.json();
  const doc = w.docs(`users/${uid}`);
  assert.deepEqual([doc.role, doc.regionId, doc.squareId, doc.active, doc.createdBy], ['FIELD_STAFF', 'A', 'A1', true, 'rmA']);
  assert.equal((await post('rmA', newUser())).status, 409);
  assert.ok([...w.store.keys()].some((p) => p.startsWith('audit/')));
  const c = await post('central', newUser({ username: 'rmz', role: 'REGION_MANAGER', regionId: 'C', squareId: null }));
  assert.equal(c.status, 201);
});

test('إعادة ضبط كلمة المرور والتعطيل', async () => {
  const w = await world(users);
  const r = await w.call(handle, 'rmA', 'POST', '/v1/users/staffA1/password', {});
  assert.equal(r.status, 200);
  const { password } = await r.json();
  assert.ok(password.length >= 8);
  assert.equal(w.calls.identity.at(-1).body.localId, 'staffA1');
  assert.equal((await w.call(handle, 'rmA', 'POST', '/v1/users/staffB1/password', {})).status, 403);
  assert.equal((await w.call(handle, 'staffA1', 'POST', '/v1/users/staffA2/password', {})).status, 403);
  assert.equal((await w.call(handle, 'smA1', 'POST', '/v1/users/rmA/password', {})).status, 403);
  assert.equal((await w.call(handle, 'rmA', 'POST', '/v1/users/staffA1/active', { active: false })).status, 200);
  assert.equal(w.docs('users/staffA1').active, false);
  assert.equal(w.calls.identity.at(-1).body.disableUser, true);
  assert.equal((await w.call(handle, 'staffA1', 'GET', '/v1/backups')).status, 403);
  assert.equal((await w.call(handle, 'rmA', 'POST', '/v1/users/rmA/active', { active: false })).status, 400);
});

test('المجموعات والاجتماعات تُنشأ ضمن المنطقة فقط', async () => {
  const w = await world(users);
  const g = (who, members) => w.call(handle, who, 'POST', '/v1/rooms/group', { name: 'فريق', members });
  assert.equal((await g('rmA', ['smA1', 'staffA1'])).status, 201);
  assert.equal((await g('rmA', ['staffB1'])).status, 403);
  assert.equal((await g('staffA1', ['smA1'])).status, 403);
  assert.equal((await g('rmA', ['ghost'])).status, 400);
  assert.equal((await g('central', ['staffA1', 'staffB1'])).status, 201);
  assert.equal((await w.call(handle, 'rmA', 'POST', '/v1/meetings', { name: 'اجتماع', members: ['smA1'] })).status, 201);
  const room = [...w.store.keys()].find((p) => p.startsWith('rooms/'));
  assert.deepEqual(w.docs(room).members.includes('rmA'), true);
});

test('الإشعارات: التحقق من الصلاحية واستهداف المدراء فقط', async () => {
  const w = await world(users);
  w.put('rooms/r1', { type: 'direct', members: ['staffA1', 'smA1'] });
  for (const u of ['smA1', 'rmA', 'staffA2', 'central', 'rmB']) w.put(`devices/${u}`, { token: `tok-${u}` });
  w.put('sos/s1', { senderId: 'staffA1', regionId: 'A', squareId: 'A1', status: 'OPEN' });
  const n = (who, kind, id) => w.call(handle, who, 'POST', '/v1/notify', { kind, id });
  assert.equal((await n('staffA2', 'chat', 'r1')).status, 403);
  assert.equal((await n('staffA1', 'chat', 'r1')).status, 200);
  assert.deepEqual(w.calls.fcm.map((m) => m.token), ['tok-smA1']);
  assert.equal(w.calls.fcm[0].notification.body, 'لديك رسالة جديدة');
  w.calls.fcm.length = 0;
  assert.equal((await n('smA1', 'sos', 's1')).status, 403);
  assert.equal((await n('staffA1', 'sos', 's1')).status, 200);
  const toks = w.calls.fcm.map((m) => m.token).sort();
  assert.deepEqual(toks, ['tok-central', 'tok-rmA', 'tok-smA1']);
  assert.ok(w.calls.fcm.every((m) => m.android.notification.channel_id === 'sos'));
  assert.equal((await n('staffA1', 'bogus', 'x')).status, 400);
});

test('الوسائط: النطاق والنوع والحجم وعدم التكرار', async () => {
  const w = await world(users);
  w.put('rooms/r1', { type: 'direct', members: ['staffA1', 'smA1'] });
  const bytes = new Uint8Array([1, 2, 3, 4]);
  const put = (who, scope, type = 'image/jpeg', body = bytes, extra = {}) => w.call(handle, who, 'PUT', '/v1/media', body, { 'x-scope': scope, 'content-type': type, ...extra });
  assert.equal((await put('staffA2', 'room:r1')).status, 403);
  assert.equal((await put('rmA', 'room:r1')).status, 403);
  assert.equal((await put('staffA1', 'room:r1', 'text/html')).status, 415);
  assert.equal((await put('staffA1', 'room:r1', 'image/jpeg', new Uint8Array(1_500_001))).status, 413);
  const ok = await put('staffA1', 'room:r1', 'image/jpeg', bytes, { 'x-idempotency-key': 'abcdef123456' });
  assert.equal(ok.status, 201);
  const { id } = await ok.json();
  assert.equal((await put('staffA1', 'room:r1', 'image/jpeg', bytes, { 'x-idempotency-key': 'abcdef123456' })).status, 201);
  assert.equal(w.env.DB.raw.prepare('SELECT COUNT(*) c FROM media').get().c, 1);
  const g = await w.call(handle, 'smA1', 'GET', `/v1/media/${id}`);
  assert.equal(g.status, 200);
  assert.deepEqual([...new Uint8Array(await g.arrayBuffer())], [1, 2, 3, 4]);
  assert.equal(g.headers.get('content-disposition'), 'attachment');
  assert.equal((await w.call(handle, 'staffA2', 'GET', `/v1/media/${id}`)).status, 404);
  assert.equal((await w.call(handle, 'central', 'GET', `/v1/media/${id}`)).status, 404);
});

test('النسخ الاحتياطي: الإنشاء والاستعادة والاحتفاظ', async () => {
  const w = await world(users);
  w.put('squares/A1', { regionId: 'A', name: 'م1' });
  w.put('tasks/t1', { title: 'مهمة', status: 'NEW', regionId: 'A', squareId: 'A1' });
  assert.equal((await w.call(handle, 'rmA', 'POST', '/v1/backups/run')).status, 403);
  const r = await w.call(handle, 'central', 'POST', '/v1/backups/run');
  assert.equal(r.status, 200);
  const info = await r.json();
  assert.equal(info.counts.tasks, 1);
  const dl = await w.call(handle, 'central', 'GET', `/v1/backups/${info.id}`);
  const parsed = JSON.parse(gunzipSync(Buffer.from(await dl.arrayBuffer())).toString());
  assert.equal(parsed.data.tasks[0].title, 'مهمة');
  assert.equal(parsed.data.users.length, Object.keys(users).length);
  for (let i = 0; i < 16; i++) await runBackup(w.env, w.fetch, NOW + i + 1);
  assert.equal(w.env.DB.raw.prepare('SELECT COUNT(*) c FROM backups').get().c, 14);
  assert.equal(w.env.DB.raw.prepare('SELECT COUNT(*) c FROM backup_chunks').get().c, 14);
  assert.equal(await readBackup(w.env, 'missing'), null);
});

test('التصعيد والتذكير مرة واحدة فقط', async () => {
  const w = await world(users);
  for (const u of ['smA1', 'rmA', 'staffA1']) w.put(`devices/${u}`, { token: `tok-${u}` });
  w.put('tasks/late', { title: 'متأخرة', status: 'IN_PROGRESS', regionId: 'A', squareId: 'A1', assigneeId: 'staffA1', deadline: NOW - 1000 });
  w.put('tasks/soon', { title: 'قريبة', status: 'NEW', regionId: 'A', squareId: 'A1', assigneeId: 'staffA1', deadline: NOW + 30 * 60_000 });
  w.put('tasks/far', { title: 'بعيدة', status: 'NEW', regionId: 'A', squareId: 'A1', assigneeId: 'staffA1', deadline: NOW + 5 * 3_600_000 });
  w.put('tasks/done', { title: 'منتهية', status: 'APPROVED', regionId: 'A', squareId: 'A1', assigneeId: 'staffA1', deadline: NOW - 1000 });
  w.put('tasks/other', { title: 'منطقة أخرى', status: 'IN_PROGRESS', regionId: 'B', squareId: 'B1', assigneeId: 'staffB1', deadline: NOW - 1000 });
  const r1 = await escalate(w.env, w.fetch, NOW);
  assert.deepEqual([r1.escalated, r1.reminded, r1.scanned], [2, 1, 4]);
  const lateTokens = w.calls.fcm.filter((m) => m.notification.title === 'مهمة متأخرة').map((m) => m.token).sort();
  assert.ok(lateTokens.includes('tok-rmA') && lateTokens.includes('tok-smA1') && !lateTokens.includes('tok-staffA1'));
  assert.equal(w.docs('tasks/late').escalatedAt, NOW);
  assert.equal(w.docs('tasks/soon').remindedAt, NOW);
  const r2 = await escalate(w.env, w.fetch, NOW + 1000);
  assert.deepEqual([r2.escalated, r2.reminded], [0, 0]);
});
