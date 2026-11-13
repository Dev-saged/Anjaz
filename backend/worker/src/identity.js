import { accessToken } from './google.js';

export class Identity {
  constructor(env, fetchFn = fetch) {
    this.env = env;
    this.fetch = fetchFn;
    this.base = `https://identitytoolkit.googleapis.com/v1/projects/${env.FIREBASE_PROJECT_ID}`;
  }

  async call(path, body) {
    const token = await accessToken(this.env, Date.now(), this.fetch);
    const res = await this.fetch(`${this.base}${path}`, {
      method: 'POST',
      headers: { authorization: `Bearer ${token}`, 'content-type': 'application/json' },
      body: JSON.stringify(body),
    });
    const j = await res.json().catch(() => ({}));
    if (!res.ok) {
      const msg = j?.error?.message || '';
      if (msg.includes('EMAIL_EXISTS') || msg.includes('DUPLICATE')) return { error: 'exists' };
      throw new Error(`identity ${res.status}`);
    }
    return j;
  }

  email(username) { return `${username}@${this.env.EMAIL_DOMAIN || 'users.anjaz.local'}`; }

  async createUser(username, password, displayName) {
    const r = await this.call('/accounts', { email: this.email(username), password, displayName, emailVerified: true });
    return r.error ? r : { uid: r.localId };
  }

  setPassword(uid, password) { return this.call('/accounts:update', { localId: uid, password }); }

  setDisabled(uid, disabled) { return this.call('/accounts:update', { localId: uid, disableUser: disabled }); }
}
