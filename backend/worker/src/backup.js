import { Firestore } from './firestore.js';

const COLLECTIONS = ['regions', 'squares', 'camps', 'users', 'tasks'];
const CHUNK = 900_000;
const KEEP = 14;

async function gzip(text) {
  const stream = new Blob([text]).stream().pipeThrough(new CompressionStream('gzip'));
  return new Uint8Array(await new Response(stream).arrayBuffer());
}

export async function runBackup(env, fetchFn = fetch, now = Date.now()) {
  const fs = new Firestore(env, fetchFn);
  const data = {};
  for (const c of COLLECTIONS) data[c] = await fs.listAll(c);
  const bytes = await gzip(JSON.stringify({ takenAt: now, data }));
  const id = crypto.randomUUID();
  const chunks = Math.max(1, Math.ceil(bytes.length / CHUNK));
  const stmts = [env.DB.prepare('INSERT INTO backups (id, created_at, bytes, chunks) VALUES (?, ?, ?, ?)').bind(id, now, bytes.length, chunks)];
  for (let i = 0; i < chunks; i++) {
    stmts.push(env.DB.prepare('INSERT INTO backup_chunks (backup_id, idx, data) VALUES (?, ?, ?)').bind(id, i, bytes.slice(i * CHUNK, (i + 1) * CHUNK)));
  }
  await env.DB.batch(stmts);
  const old = await env.DB.prepare('SELECT id FROM backups ORDER BY created_at DESC LIMIT -1 OFFSET ?').bind(KEEP).all();
  for (const r of old.results) {
    await env.DB.batch([
      env.DB.prepare('DELETE FROM backup_chunks WHERE backup_id = ?').bind(r.id),
      env.DB.prepare('DELETE FROM backups WHERE id = ?').bind(r.id),
    ]);
  }
  return { id, bytes: bytes.length, counts: Object.fromEntries(COLLECTIONS.map((c) => [c, data[c].length])) };
}

export async function readBackup(env, id) {
  const meta = await env.DB.prepare('SELECT chunks, bytes FROM backups WHERE id = ?').bind(id).first();
  if (!meta) return null;
  const rows = await env.DB.prepare('SELECT data FROM backup_chunks WHERE backup_id = ? ORDER BY idx').bind(id).all();
  const out = new Uint8Array(meta.bytes);
  let off = 0;
  for (const r of rows.results) { const part = new Uint8Array(r.data); out.set(part, off); off += part.length; }
  return out;
}
