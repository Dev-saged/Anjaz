import { test, before, after, beforeEach } from 'node:test';
import { readFileSync } from 'node:fs';
import { initializeTestEnvironment, assertSucceeds, assertFails } from '@firebase/rules-unit-testing';
import { doc, getDoc, setDoc, updateDoc, deleteDoc, collection, addDoc, getDocs, query, where } from 'firebase/firestore';

let env;
const U = {
  central: { role: 'CENTRAL', regionId: null, squareId: null },
  rmA: { role: 'REGION_MANAGER', regionId: 'A', squareId: null },
  adminA: { role: 'REGION_ADMIN', regionId: 'A', squareId: null },
  smA1: { role: 'SQUARE_MANAGER', regionId: 'A', squareId: 'A1' },
  staffA1: { role: 'FIELD_STAFF', regionId: 'A', squareId: 'A1' },
  staffA2: { role: 'FIELD_STAFF', regionId: 'A', squareId: 'A2' },
  rmB: { role: 'REGION_MANAGER', regionId: 'B', squareId: null },
  staffB1: { role: 'FIELD_STAFF', regionId: 'B', squareId: 'B1' },
};
const db = (uid) => env.authenticatedContext(uid).firestore();
const task = (o = {}) => ({
  title: 'مهمة', description: '', priority: 'NORMAL', status: 'NEW', regionId: 'A', squareId: 'A1',
  assigneeId: 'staffA1', createdBy: 'smA1', createdAt: 1, updatedAt: 1, ...o,
});

before(async () => {
  env = await initializeTestEnvironment({ projectId: 'demo-anjaz', firestore: { rules: readFileSync('firestore.rules', 'utf8') } });
});
after(async () => { await env.cleanup(); });
beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const d = ctx.firestore();
    for (const [id, u] of Object.entries(U)) await setDoc(doc(d, 'users', id), { ...u, active: true, username: id, displayName: id });
    await setDoc(doc(d, 'users', 'ghost'), { role: 'FIELD_STAFF', regionId: 'A', squareId: 'A1', active: false });
    await setDoc(doc(d, 'tasks', 't1'), task());
    await setDoc(doc(d, 'rooms', 'dA'), { type: 'direct', members: ['staffA1', 'smA1'] });
    await setDoc(doc(d, 'rooms/dA/messages/m1'), { senderId: 'smA1', type: 'text', text: 'مرحبا', readBy: ['smA1'] });
    await setDoc(doc(d, 'sos', 's1'), { senderId: 'staffA1', regionId: 'A', squareId: 'A1', status: 'OPEN' });
    await setDoc(doc(d, 'audit', 'a1'), { regionId: 'A', action: 'x' });
    await setDoc(doc(d, 'meetings', 'm1'), { members: ['rmA', 'smA1'] });
  });
});

// ---- users ----
test('المستخدم يقرأ نفسه ولا يكتب ملفه', async () => {
  await assertSucceeds(getDoc(doc(db('staffA1'), 'users', 'staffA1')));
  await assertFails(updateDoc(doc(db('staffA1'), 'users', 'staffA1'), { role: 'CENTRAL' }));
  await assertFails(setDoc(doc(db('staffA1'), 'users', 'new'), { role: 'CENTRAL', active: true }));
});
test('مدير المنطقة يرى مستخدمي منطقته فقط', async () => {
  await assertSucceeds(getDoc(doc(db('rmA'), 'users', 'staffA1')));
  await assertFails(getDoc(doc(db('rmA'), 'users', 'staffB1')));
  await assertFails(getDoc(doc(db('staffA1'), 'users', 'staffA2')));
});

// ---- عزل المهام ----
test('عزل المناطق والمربعات في قراءة المهام', async () => {
  await assertSucceeds(getDoc(doc(db('rmA'), 'tasks', 't1')));
  await assertSucceeds(getDoc(doc(db('central'), 'tasks', 't1')));
  await assertSucceeds(getDoc(doc(db('smA1'), 'tasks', 't1')));
  await assertSucceeds(getDoc(doc(db('staffA1'), 'tasks', 't1')));
  await assertFails(getDoc(doc(db('staffA2'), 'tasks', 't1')));
  await assertFails(getDoc(doc(db('rmB'), 'tasks', 't1')));
  await assertFails(getDoc(doc(db('staffB1'), 'tasks', 't1')));
});
test('إنشاء المهام حسب النطاق', async () => {
  await assertSucceeds(setDoc(doc(db('smA1'), 'tasks', 'n1'), task({ createdBy: 'smA1' })));
  await assertSucceeds(setDoc(doc(db('rmA'), 'tasks', 'n2'), task({ createdBy: 'rmA', squareId: 'A2' })));
  await assertFails(setDoc(doc(db('staffA1'), 'tasks', 'n3'), task({ createdBy: 'staffA1' })));
  await assertFails(setDoc(doc(db('smA1'), 'tasks', 'n4'), task({ createdBy: 'smA1', squareId: 'A2' })));
  await assertFails(setDoc(doc(db('rmB'), 'tasks', 'n5'), task({ createdBy: 'rmB' })));
  await assertFails(setDoc(doc(db('smA1'), 'tasks', 'n6'), task({ createdBy: 'rmA' })));
  await assertFails(setDoc(doc(db('smA1'), 'tasks', 'n7'), task({ createdBy: 'smA1', status: 'APPROVED' })));
  await assertFails(setDoc(doc(db('smA1'), 'tasks', 'n8'), task({ createdBy: 'smA1', title: 'x'.repeat(121) })));
  await assertFails(setDoc(doc(db('smA1'), 'tasks', 'n9'), task({ createdBy: 'smA1', title: '' })));
});
test('المُكلَّف يتقدّم بالحالة فقط ولا يعتمد', async () => {
  const t = doc(db('staffA1'), 'tasks', 't1');
  await assertSucceeds(updateDoc(t, { status: 'SEEN', updatedAt: 2 }));
  await assertSucceeds(updateDoc(t, { status: 'IN_PROGRESS', updatedAt: 3 }));
  await assertFails(updateDoc(t, { status: 'APPROVED', updatedAt: 4 }));
  await assertSucceeds(updateDoc(t, { status: 'AWAITING_REVIEW', updatedAt: 5 }));
  await assertFails(updateDoc(t, { status: 'APPROVED', updatedAt: 6 }));
  await assertFails(updateDoc(t, { title: 'تعديل', updatedAt: 7 }));
});
test('الإداري يعتمد ويرفض ويلغي والحالات النهائية مغلقة', async () => {
  await env.withSecurityRulesDisabled((c) => updateDoc(doc(c.firestore(), 'tasks', 't1'), { status: 'AWAITING_REVIEW' }));
  await assertFails(updateDoc(doc(db('rmB'), 'tasks', 't1'), { status: 'APPROVED' }));
  await assertFails(updateDoc(doc(db('staffA2'), 'tasks', 't1'), { status: 'APPROVED' }));
  await assertSucceeds(updateDoc(doc(db('smA1'), 'tasks', 't1'), { status: 'APPROVED', updatedAt: 9 }));
  await assertFails(updateDoc(doc(db('rmA'), 'tasks', 't1'), { status: 'IN_PROGRESS' }));
  await assertFails(deleteDoc(doc(db('central'), 'tasks', 't1')));
});
test('لا تغيير لمنطقة المهمة ولا قفز على آلة الحالات', async () => {
  await assertFails(updateDoc(doc(db('rmA'), 'tasks', 't1'), { regionId: 'B' }));
  await assertFails(updateDoc(doc(db('rmA'), 'tasks', 't1'), { status: 'APPROVED' }));
  await assertSucceeds(updateDoc(doc(db('rmA'), 'tasks', 't1'), { title: 'عنوان جديد' }));
});
test('سجل الأحداث للإلحاق فقط', async () => {
  const ev = { actorId: 'staffA1', toStatus: 'SEEN', at: 1 };
  await assertSucceeds(setDoc(doc(db('staffA1'), 'tasks/t1/events/e1'), ev));
  await assertFails(updateDoc(doc(db('staffA1'), 'tasks/t1/events/e1'), { note: 'x' }));
  await assertFails(deleteDoc(doc(db('staffA1'), 'tasks/t1/events/e1')));
  await assertFails(setDoc(doc(db('staffA1'), 'tasks/t1/events/e2'), { ...ev, actorId: 'smA1' }));
  await assertFails(setDoc(doc(db('rmB'), 'tasks/t1/events/e3'), { ...ev, actorId: 'rmB' }));
});

// ---- المحادثات ----
test('خصوصية الرسائل: الأعضاء فقط حتى المدير المركزي', async () => {
  await assertSucceeds(getDoc(doc(db('staffA1'), 'rooms/dA/messages/m1')));
  await assertFails(getDoc(doc(db('rmA'), 'rooms/dA/messages/m1')));
  await assertFails(getDoc(doc(db('central'), 'rooms/dA/messages/m1')));
  await assertFails(getDoc(doc(db('central'), 'rooms', 'dA')));
});
test('إنشاء محادثة مباشرة داخل المنطقة فقط (والمركز استثناء)', async () => {
  await assertSucceeds(setDoc(doc(db('staffA1'), 'rooms', 'x1'), { type: 'direct', members: ['staffA1', 'staffA2'] }));
  await assertFails(setDoc(doc(db('staffA1'), 'rooms', 'x2'), { type: 'direct', members: ['staffA1', 'staffB1'] }));
  await assertSucceeds(setDoc(doc(db('staffA1'), 'rooms', 'x3'), { type: 'direct', members: ['staffA1', 'central'] }));
  await assertSucceeds(setDoc(doc(db('central'), 'rooms', 'x4'), { type: 'direct', members: ['central', 'staffB1'] }));
  await assertFails(setDoc(doc(db('staffA1'), 'rooms', 'x5'), { type: 'direct', members: ['staffA2', 'smA1'] }));
  await assertFails(setDoc(doc(db('staffA1'), 'rooms', 'x6'), { type: 'group', members: ['staffA1', 'staffA2'] }));
});
test('الرسائل: انتحال المرسل وتعديل النص والحذف مرفوضة', async () => {
  const m = { senderId: 'staffA1', type: 'text', text: 'تمام', readBy: ['staffA1'] };
  await assertSucceeds(setDoc(doc(db('staffA1'), 'rooms/dA/messages/m2'), m));
  await assertFails(setDoc(doc(db('staffA1'), 'rooms/dA/messages/m3'), { ...m, senderId: 'smA1' }));
  await assertFails(setDoc(doc(db('rmA'), 'rooms/dA/messages/m4'), { ...m, senderId: 'rmA', readBy: ['rmA'] }));
  await assertFails(setDoc(doc(db('staffA1'), 'rooms/dA/messages/m5'), { ...m, type: 'exe' }));
  await assertFails(updateDoc(doc(db('smA1'), 'rooms/dA/messages/m1'), { text: 'معدّل' }));
  await assertFails(deleteDoc(doc(db('smA1'), 'rooms/dA/messages/m1')));
});
test('إيصال القراءة يضيف المستخدم نفسه فقط', async () => {
  const m = doc(db('staffA1'), 'rooms/dA/messages/m1');
  await assertSucceeds(updateDoc(m, { readBy: ['smA1', 'staffA1'] }));
  await assertFails(updateDoc(doc(db('staffA1'), 'rooms/dA/messages/m1'), { readBy: ['smA1', 'staffA1', 'rmA'] }));
  await assertFails(updateDoc(doc(db('staffA1'), 'rooms/dA/messages/m1'), { readBy: ['smA1', 'rmA'] }));
  await assertFails(updateDoc(doc(db('rmA'), 'rooms/dA/messages/m1'), { readBy: ['smA1', 'rmA'] }));
});

// ---- SOS ----
test('SOS: الإرسال والاستلام حسب النطاق', async () => {
  const s = { senderId: 'staffA1', regionId: 'A', squareId: 'A1', status: 'OPEN' };
  await assertSucceeds(setDoc(doc(db('staffA1'), 'sos', 's2'), s));
  await assertFails(setDoc(doc(db('staffA1'), 'sos', 's3'), { ...s, regionId: 'B' }));
  await assertFails(setDoc(doc(db('staffA1'), 'sos', 's4'), { ...s, senderId: 'smA1' }));
  await assertFails(setDoc(doc(db('staffA1'), 'sos', 's5'), { ...s, status: 'RESOLVED' }));
  await assertSucceeds(getDoc(doc(db('rmA'), 'sos', 's1')));
  await assertSucceeds(getDoc(doc(db('smA1'), 'sos', 's1')));
  await assertFails(getDoc(doc(db('rmB'), 'sos', 's1')));
  await assertFails(getDoc(doc(db('staffA2'), 'sos', 's1')));
});
test('SOS: التأكيد والإغلاق للإداريين وبالترتيب', async () => {
  await assertFails(updateDoc(doc(db('staffA1'), 'sos', 's1'), { status: 'ACK' }));
  await assertFails(updateDoc(doc(db('rmB'), 'sos', 's1'), { status: 'ACK' }));
  await assertFails(updateDoc(doc(db('smA1'), 'sos', 's1'), { status: 'RESOLVED' }));
  await assertSucceeds(updateDoc(doc(db('smA1'), 'sos', 's1'), { status: 'ACK', ackBy: 'smA1', updatedAt: 2 }));
  await assertFails(updateDoc(doc(db('smA1'), 'sos', 's1'), { status: 'OPEN' }));
  await assertSucceeds(updateDoc(doc(db('rmA'), 'sos', 's1'), { status: 'RESOLVED', updatedAt: 3 }));
});

// ---- التدقيق والأجهزة والاجتماعات ----
test('سجل التدقيق للقراءة فقط من الإداريين', async () => {
  await assertSucceeds(getDoc(doc(db('central'), 'audit', 'a1')));
  await assertSucceeds(getDoc(doc(db('rmA'), 'audit', 'a1')));
  await assertFails(getDoc(doc(db('rmB'), 'audit', 'a1')));
  await assertFails(getDoc(doc(db('staffA1'), 'audit', 'a1')));
  await assertFails(setDoc(doc(db('central'), 'audit', 'a2'), { regionId: 'A' }));
  await assertFails(deleteDoc(doc(db('central'), 'audit', 'a1')));
});
test('رموز الإشعارات: لكل مستخدم رمزه', async () => {
  await assertSucceeds(setDoc(doc(db('staffA1'), 'devices', 'staffA1'), { token: 'abc' }));
  await assertFails(setDoc(doc(db('staffA1'), 'devices', 'smA1'), { token: 'abc' }));
  await assertFails(getDoc(doc(db('smA1'), 'devices', 'staffA1')));
});
test('الاجتماعات: الأعضاء فقط والإنشاء من الـ Worker', async () => {
  await assertSucceeds(getDoc(doc(db('rmA'), 'meetings', 'm1')));
  await assertFails(getDoc(doc(db('staffA1'), 'meetings', 'm1')));
  await assertFails(setDoc(doc(db('rmA'), 'meetings', 'm2'), { members: ['rmA'] }));
  await assertSucceeds(setDoc(doc(db('rmA'), 'meetings/m1/signals/s1'), { from: 'rmA', to: 'smA1', payload: '{}' }));
  await assertFails(setDoc(doc(db('rmA'), 'meetings/m1/signals/s2'), { from: 'smA1', to: 'rmA', payload: '{}' }));
  await assertFails(setDoc(doc(db('staffA1'), 'meetings/m1/signals/s3'), { from: 'staffA1', to: 'all', payload: '{}' }));
  await assertSucceeds(getDoc(doc(db('smA1'), 'meetings/m1/signals/s1')));
  await assertFails(getDoc(doc(db('rmA'), 'meetings/m1/signals/s1')));
});

// ---- عام ----
test('المستخدم المعطّل وغير المسجّل والمجموعات المجهولة مرفوضون', async () => {
  await assertFails(getDoc(doc(db('ghost'), 'tasks', 't1')));
  await assertFails(setDoc(doc(db('ghost'), 'sos', 'g1'), { senderId: 'ghost', regionId: 'A', squareId: 'A1', status: 'OPEN' }));
  await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(), 'tasks', 't1')));
  await assertFails(getDoc(doc(db('central'), 'secrets', 'x')));
  await assertFails(setDoc(doc(db('central'), 'secrets', 'x'), { a: 1 }));
});
test('الدليل: المنطقة والمربعات والمخيمات', async () => {
  await env.withSecurityRulesDisabled(async (c) => {
    const d = c.firestore();
    await setDoc(doc(d, 'squares', 'A1'), { regionId: 'A', name: 'م1' });
    await setDoc(doc(d, 'camps', 'c1'), { regionId: 'A', squareId: 'A1', name: 'الأمل' });
  });
  await assertSucceeds(getDoc(doc(db('staffA2'), 'squares', 'A1')));
  await assertFails(getDoc(doc(db('rmB'), 'squares', 'A1')));
  await assertSucceeds(getDoc(doc(db('staffA1'), 'camps', 'c1')));
  await assertFails(getDoc(doc(db('staffA2'), 'camps', 'c1')));
  await assertSucceeds(setDoc(doc(db('rmA'), 'squares', 'A3'), { regionId: 'A', name: 'م3' }));
  await assertFails(setDoc(doc(db('rmA'), 'squares', 'B9'), { regionId: 'B', name: 'x' }));
  await assertFails(setDoc(doc(db('smA1'), 'squares', 'A4'), { regionId: 'A', name: 'x' }));
  await assertSucceeds(setDoc(doc(db('smA1'), 'camps', 'c2'), { regionId: 'A', squareId: 'A1', name: 'ن' }));
  await assertFails(setDoc(doc(db('smA1'), 'camps', 'c3'), { regionId: 'A', squareId: 'A2', name: 'ن' }));
});
