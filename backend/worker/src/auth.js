import { dec, enc, fromB64url } from './b64.js';

const JWK_URL = 'https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com';
let jwks = null;

export class HttpError extends Error {
  constructor(status, message) { super(message); this.status = status; }
}

async function keys(fetchFn, now) {
  if (jwks && jwks.exp > now) return jwks.keys;
  const res = await fetchFn(JWK_URL);
  if (!res.ok) throw new HttpError(503, 'تعذّر جلب مفاتيح التحقق');
  const j = await res.json();
  jwks = { keys: j.keys, exp: now + 3_600_000 };
  return jwks.keys;
}

export function resetKeyCache() { jwks = null; }

export async function verifyIdToken(token, projectId, { fetchFn = fetch, now = Date.now() } = {}) {
  const parts = String(token || '').split('.');
  if (parts.length !== 3) throw new HttpError(401, 'رمز غير صالح');
  let header; let claims;
  try {
    header = JSON.parse(dec.decode(fromB64url(parts[0])));
    claims = JSON.parse(dec.decode(fromB64url(parts[1])));
  } catch { throw new HttpError(401, 'رمز غير صالح'); }
  if (header.alg !== 'RS256' || !header.kid) throw new HttpError(401, 'رمز غير صالح');
  const jwk = (await keys(fetchFn, now)).find((k) => k.kid === header.kid);
  if (!jwk) throw new HttpError(401, 'مفتاح غير معروف');
  const key = await crypto.subtle.importKey('jwk', jwk, { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' }, false, ['verify']);
  const ok = await crypto.subtle.verify('RSASSA-PKCS1-v1_5', key, fromB64url(parts[2]), enc.encode(`${parts[0]}.${parts[1]}`));
  if (!ok) throw new HttpError(401, 'توقيع غير صالح');
  const sec = Math.floor(now / 1000);
  if (claims.aud !== projectId || claims.iss !== `https://securetoken.google.com/${projectId}`) throw new HttpError(401, 'رمز لمشروع آخر');
  if (!claims.sub || typeof claims.sub !== 'string') throw new HttpError(401, 'رمز غير صالح');
  if (claims.exp <= sec || claims.iat > sec + 60) throw new HttpError(401, 'انتهت صلاحية الرمز');
  return claims.sub;
}
