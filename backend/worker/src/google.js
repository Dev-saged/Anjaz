import { b64url, enc, pemToDer } from './b64.js';

const SCOPES = [
  'https://www.googleapis.com/auth/cloud-platform',
  'https://www.googleapis.com/auth/datastore',
  'https://www.googleapis.com/auth/firebase.messaging',
].join(' ');

let cached = null;

export function serviceAccount(env) {
  const sa = JSON.parse(env.SERVICE_ACCOUNT_JSON);
  if (!sa.client_email || !sa.private_key) throw new Error('bad service account');
  return sa;
}

export async function signJwt(claims, pem) {
  const header = b64url(enc.encode(JSON.stringify({ alg: 'RS256', typ: 'JWT' })));
  const payload = b64url(enc.encode(JSON.stringify(claims)));
  const key = await crypto.subtle.importKey('pkcs8', pemToDer(pem), { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' }, false, ['sign']);
  const sig = await crypto.subtle.sign('RSASSA-PKCS1-v1_5', key, enc.encode(`${header}.${payload}`));
  return `${header}.${payload}.${b64url(sig)}`;
}

export async function accessToken(env, now = Date.now(), fetchFn = fetch) {
  if (cached && cached.exp - 60_000 > now) return cached.token;
  const sa = serviceAccount(env);
  const iat = Math.floor(now / 1000);
  const jwt = await signJwt(
    { iss: sa.client_email, scope: SCOPES, aud: 'https://oauth2.googleapis.com/token', iat, exp: iat + 3600 },
    sa.private_key,
  );
  const res = await fetchFn('https://oauth2.googleapis.com/token', {
    method: 'POST',
    headers: { 'content-type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer', assertion: jwt }),
  });
  if (!res.ok) throw new Error(`token ${res.status}`);
  const j = await res.json();
  cached = { token: j.access_token, exp: now + j.expires_in * 1000 };
  return cached.token;
}

export function resetTokenCache() { cached = null; }
