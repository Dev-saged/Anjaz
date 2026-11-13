import { DatabaseSync } from 'node:sqlite';
import { readFileSync } from 'node:fs';
import { b64url, enc } from '../src/b64.js';
import { encodeFields, decodeDoc } from '../src/firestore.js';
import { resetTokenCache } from '../src/google.js';
import { resetKeyCache } from '../src/auth.js';

export const PROJECT = 'demo-anjaz';
export const NOW = 1_760_000_000_000;

export async function keys() {
  const pair = await crypto.subtle.generateKey({ name: 'RSASSA-PKCS1-v1_5', modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: 'SHA-256' }, true, ['sign', 'verify']);
  const pkcs8 = Buffer.from(await crypto.subtle.exportKey('pkcs8', pair.privateKey)).toString('base64');
  const jwk = { ...(await crypto.subtle.exportKey('jwk', pair.publicKey)), kid: 'k1', alg: 'RS256', use: 'sig' };
  return { pair, jwk, pem: `-----BEGIN PRIVATE KEY-----\n${pkcs8}\n-----END PRIVATE KEY-----` };
}

export async function idToken(k, uid, over = {}) {
  const sec = Math.floor(NOW / 1000);
  const h = b64url(enc.encode(JSON.stringify({ alg: 'RS256', kid: 'k1', typ: 'JWT' })));
  const p = b64url(enc.encode(JSON.stringify({ iss: `https://securetoken.google.com/${PROJECT}`, aud: PROJECT, sub: uid, iat: sec - 10, exp: sec + 3000, ...over })));
  const sig = await crypto.subtle.sign('RSASSA-PKCS1-v1_5', k.pair.privateKey, enc.encode(`${h}.${p}`));
  return `${h}.${p}.${b64url(sig)}`;
}

export function d1() {
  const db = new DatabaseSync(':memory:');
  db.exec(readFileSync(new URL('../schema.sql', import.meta.url), 'utf8'));
  const stmt = (sql, args = []) => ({
    bind: (...a) => stmt(sql, a),
    run: async () => { db.prepare(sql).run(...args); return {}; },
    first: async () => db.prepare(sql).get(...args) ?? null,
    all: async () => ({ results: db.prepare(sql).all(...args) }),
    _exec: () => db.prepare(sql).run(...args),
  });
  return {
    prepare: (sql) => stmt(sql),
    batch: async (list) => { db.exec('BEGIN'); try { list.forEach((s) => s._exec()); db.exec('COMMIT'); } catch (e) { db.exec('ROLLBACK'); throw e; } },
    raw: db,
  };
}

export async function world(seedUsers = {}) {
  resetTokenCache(); resetKeyCache();
  const k = await keys();
  const store = new Map();
  const calls = { fcm: [], identity: [], emails: new Map() };
  const prefix = `/v1/projects/${PROJECT}/databases/(default)/documents`;
  const put = (path, obj) => store.set(path, encodeFields(obj));
  Object.entries(seedUsers).forEach(([id, u]) => put(`users/${id}`, { active: true, ...u }));

  const docOf = (path) => ({ name: `projects/${PROJECT}/databases/(default)/documents/${path}`, fields: store.get(path) });
  const matches = (doc, where) => {
    if (where.compositeFilter) return where.compositeFilter.filters.every((f) => matches(doc, f));
    const { field, op, value } = where.fieldFilter;
    const actual = doc[field.fieldPath];
    const want = decodeDoc({ name: 'x/x', fields: { v: value } }).v;
    return op === 'EQUAL' ? actual === want : op === 'IN' ? want.includes(actual) : false;
  };

  const fetch = async (url, init = {}) => {
    const u = new URL(url);
    const ok = (b, s = 200) => new Response(JSON.stringify(b), { status: s });
    if (u.host === 'www.googleapis.com') return ok({ keys: [k.jwk] });
    if (u.host === 'oauth2.googleapis.com') return ok({ access_token: 'sa-token', expires_in: 3600 });
    if (u.host === 'fcm.googleapis.com') { calls.fcm.push(JSON.parse(init.body).message); return ok({ name: 'm' }); }
    if (u.host === 'identitytoolkit.googleapis.com') {
      const body = JSON.parse(init.body);
      calls.identity.push({ path: u.pathname.split(':').pop(), body });
      if (u.pathname.endsWith('/accounts')) {
        if (calls.emails.has(body.email)) return ok({ error: { message: 'EMAIL_EXISTS' } }, 400);
        const uid = `uid-${calls.emails.size + 1}`;
        calls.emails.set(body.email, uid);
        return ok({ localId: uid });
      }
      return ok({});
    }
    if (u.host === 'firestore.googleapis.com') {
      const rel = decodeURI(u.pathname).slice(prefix.length).replace(/^\//, '');
      if (rel === ':runQuery') {
        const { structuredQuery: q } = JSON.parse(init.body);
        const col = q.from[0].collectionId;
        const rows = [...store.keys()].filter((p) => p.split('/').length === 2 && p.startsWith(`${col}/`))
          .map(docOf).filter((d) => matches(decodeDoc(d), q.where)).map((document) => ({ document }));
        return ok(rows.length ? rows : [{}]);
      }
      const segs = rel.split('/');
      if (init.method === 'PATCH') {
        const body = JSON.parse(init.body);
        const mask = u.searchParams.getAll('updateMask.fieldPaths');
        const cur = store.get(rel) || {};
        store.set(rel, mask.length ? { ...cur, ...body.fields } : body.fields);
        return ok({});
      }
      if (segs.length % 2 === 1) {
        const docs = [...store.keys()].filter((p) => p.split('/').length === segs.length + 1 && p.startsWith(`${rel}/`)).map(docOf);
        return ok({ documents: docs });
      }
      return store.has(rel) ? ok(docOf(rel)) : new Response('{}', { status: 404 });
    }
    return new Response('unexpected ' + url, { status: 500 });
  };

  const env = { FIREBASE_PROJECT_ID: PROJECT, EMAIL_DOMAIN: 'users.anjaz.local', DB: d1(), SERVICE_ACCOUNT_JSON: JSON.stringify({ client_email: 'sa@x', private_key: k.pem }) };
  const call = async (handle, who, method, path, body, headers = {}) => {
    const token = who ? await idToken(k, who) : null;
    const isBin = body instanceof Uint8Array;
    const req = new Request(`https://api.test${path}`, {
      method, headers: { ...(token ? { authorization: `Bearer ${token}` } : {}), ...(isBin ? {} : { 'content-type': 'application/json' }), ...headers },
      body: body === undefined ? undefined : isBin ? body : JSON.stringify(body),
    });
    const res = await handle(req, env, { fetch, now: NOW });
    return res;
  };
  return { k, store, calls, env, fetch, put, call, docs: (p) => (store.has(p) ? decodeDoc(docOf(p)) : null) };
}
