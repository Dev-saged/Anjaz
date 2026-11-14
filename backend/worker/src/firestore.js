import { accessToken } from './google.js';

export function encodeValue(v) {
  if (v === null || v === undefined) return { nullValue: null };
  if (typeof v === 'boolean') return { booleanValue: v };
  if (typeof v === 'number') return Number.isInteger(v) ? { integerValue: String(v) } : { doubleValue: v };
  if (typeof v === 'string') return { stringValue: v };
  if (Array.isArray(v)) return { arrayValue: { values: v.map(encodeValue) } };
  return { mapValue: { fields: encodeFields(v) } };
}

export function encodeFields(o) {
  return Object.fromEntries(Object.entries(o).map(([k, v]) => [k, encodeValue(v)]));
}

export function decodeValue(v) {
  if ('nullValue' in v) return null;
  if ('booleanValue' in v) return v.booleanValue;
  if ('integerValue' in v) return Number(v.integerValue);
  if ('doubleValue' in v) return v.doubleValue;
  if ('stringValue' in v) return v.stringValue;
  if ('timestampValue' in v) return Date.parse(v.timestampValue);
  if ('arrayValue' in v) return (v.arrayValue.values || []).map(decodeValue);
  if ('mapValue' in v) return decodeFields(v.mapValue.fields || {});
  return null;
}

export function decodeFields(f) {
  return Object.fromEntries(Object.entries(f).map(([k, v]) => [k, decodeValue(v)]));
}

export function decodeDoc(d) {
  return { id: d.name.split('/').pop(), ...decodeFields(d.fields || {}) };
}

export class Firestore {
  constructor(env, fetchFn = (...a) => fetch(...a)) {
    this.env = env;
    this.fetch = fetchFn;
    this.base = `https://firestore.googleapis.com/v1/projects/${env.FIREBASE_PROJECT_ID}/databases/(default)/documents`;
  }

  async call(url, init = {}) {
    const token = await accessToken(this.env, Date.now(), this.fetch);
    const res = await this.fetch(url, {
      ...init,
      headers: { authorization: `Bearer ${token}`, 'content-type': 'application/json', ...(init.headers || {}) },
    });
    if (res.status === 404) return null;
    if (!res.ok) throw new Error(`firestore ${res.status}`);
    return res.json();
  }

  async get(path) {
    const d = await this.call(`${this.base}/${path}`);
    return d ? decodeDoc(d) : null;
  }

  async set(path, data) {
    await this.call(`${this.base}/${path}`, { method: 'PATCH', body: JSON.stringify({ fields: encodeFields(data) }) });
  }

  async patch(path, data) {
    const mask = Object.keys(data).map((k) => `updateMask.fieldPaths=${encodeURIComponent(k)}`).join('&');
    await this.call(`${this.base}/${path}?${mask}`, { method: 'PATCH', body: JSON.stringify({ fields: encodeFields(data) }) });
  }

  async add(collection, data) {
    const id = crypto.randomUUID();
    await this.set(`${collection}/${id}`, data);
    return id;
  }

  async query(collection, filters) {
    const fieldFilters = filters.map(([field, op, value]) => ({
      fieldFilter: { field: { fieldPath: field }, op, value: encodeValue(value) },
    }));
    const where = fieldFilters.length === 1 ? fieldFilters[0] : { compositeFilter: { op: 'AND', filters: fieldFilters } };
    const rows = await this.call(`${this.base}:runQuery`, {
      method: 'POST',
      body: JSON.stringify({ structuredQuery: { from: [{ collectionId: collection }], where } }),
    });
    return (rows || []).filter((r) => r.document).map((r) => decodeDoc(r.document));
  }

  async listAll(collection) {
    const out = [];
    let pageToken = '';
    do {
      const d = await this.call(`${this.base}/${collection}?pageSize=300${pageToken ? `&pageToken=${pageToken}` : ''}`);
      (d?.documents || []).forEach((x) => out.push(decodeDoc(x)));
      pageToken = d?.nextPageToken || '';
    } while (pageToken);
    return out;
  }
}
