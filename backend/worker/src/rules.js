import { accessToken } from './google.js';
import RULES from './rules.generated.js';

const API = 'https://firebaserules.googleapis.com/v1';

async function sha256(text) {
  const d = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(text));
  return [...new Uint8Array(d)].map((b) => b.toString(16).padStart(2, '0')).join('');
}

// ينشر القواعد المضمّنة إن تغيّرت عن آخر نشر؛ لا يقبل أي مدخل خارجي فلا يمكن نشر غير ما في الكود
export async function publishRules(env, fs, fetchFn = (...a) => fetch(...a), source = RULES) {
  const hash = await sha256(source);
  const last = await fs.get('system/rules').catch(() => null);
  if (last?.hash === hash) return { published: false, hash, reason: 'unchanged' };
  const token = await accessToken(env, Date.now(), fetchFn);
  const headers = { authorization: `Bearer ${token}`, 'content-type': 'application/json' };
  const project = env.FIREBASE_PROJECT_ID;
  const created = await fetchFn(`${API}/projects/${project}/rulesets`, {
    method: 'POST', headers,
    body: JSON.stringify({ source: { files: [{ name: 'firestore.rules', content: source }] } }),
  });
  const rs = await created.json().catch(() => ({}));
  if (!created.ok || !rs.name) throw new Error(`ruleset ${created.status}: ${rs.error?.message || ''}`);
  const release = `projects/${project}/releases/cloud.firestore`;
  const upd = await fetchFn(`${API}/${release}`, {
    method: 'PATCH', headers,
    body: JSON.stringify({ release: { name: release, rulesetName: rs.name } }),
  });
  if (!upd.ok) {
    const e = await upd.json().catch(() => ({}));
    throw new Error(`release ${upd.status}: ${e.error?.message || ''}`);
  }
  await fs.set('system/rules', { hash, ruleset: rs.name, at: Date.now() });
  return { published: true, hash, ruleset: rs.name };
}
