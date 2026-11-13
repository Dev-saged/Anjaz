export const RANK = { CENTRAL: 5, REGION_MANAGER: 4, REGION_ADMIN: 3, SQUARE_MANAGER: 2, FIELD_STAFF: 1 };
export const ROLES = Object.keys(RANK);
const USERNAME = /^[a-z0-9._-]{3,32}$/;

export function validUsername(u) { return typeof u === 'string' && USERNAME.test(u); }
export function validPassword(p) { return typeof p === 'string' && p.length >= 8 && p.length <= 128; }

export function isActive(p) { return !!p && p.active === true && p.role in RANK; }
export function isLead(p) { return isActive(p) && p.role !== 'FIELD_STAFF'; }

// نطاق الإدارة: المركزي كل شيء، مدير/إداري المنطقة منطقته، مدير المربع مربعه
export function inScope(actor, target) {
  if (!isActive(actor)) return false;
  if (actor.role === 'CENTRAL') return true;
  if (actor.regionId == null || actor.regionId !== target.regionId) return false;
  if (actor.role === 'SQUARE_MANAGER') return actor.squareId != null && actor.squareId === target.squareId;
  return true;
}

export function shapeOk(t) {
  if (!(t.role in RANK)) return false;
  if (t.role === 'CENTRAL') return t.regionId == null && t.squareId == null;
  if (t.role === 'REGION_MANAGER' || t.role === 'REGION_ADMIN') return typeof t.regionId === 'string' && t.squareId == null;
  return typeof t.regionId === 'string' && typeof t.squareId === 'string';
}

export function canCreateUser(actor, target) {
  if (!isActive(actor) || !shapeOk(target)) return false;
  const higher = RANK[target.role] < RANK[actor.role] || (actor.role === 'CENTRAL' && target.role === 'CENTRAL');
  return higher && inScope(actor, target);
}

export function canManageUser(actor, target) {
  if (!isActive(actor) || !target || !(target.role in RANK)) return false;
  if (actor.role === 'CENTRAL') return true;
  return RANK[target.role] < RANK[actor.role] && inScope(actor, target);
}

export function taskReader(p, task, uid) {
  if (!isActive(p)) return false;
  if (p.role === 'CENTRAL') return true;
  if (task.assigneeId === uid || task.createdBy === uid) return true;
  if (p.regionId !== task.regionId) return false;
  if (p.role === 'REGION_MANAGER' || p.role === 'REGION_ADMIN') return true;
  return p.role === 'SQUARE_MANAGER' && p.squareId === task.squareId;
}

export function randomPassword(len = 10) {
  const alphabet = 'abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  const bytes = crypto.getRandomValues(new Uint8Array(len));
  return Array.from(bytes, (b) => alphabet[b % alphabet.length]).join('');
}
