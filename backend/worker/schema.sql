CREATE TABLE IF NOT EXISTS media (
  id TEXT PRIMARY KEY,
  scope TEXT NOT NULL,
  owner TEXT NOT NULL,
  mime TEXT NOT NULL,
  size INTEGER NOT NULL,
  created_at INTEGER NOT NULL,
  data BLOB NOT NULL
);
CREATE INDEX IF NOT EXISTS media_created ON media(created_at);

CREATE TABLE IF NOT EXISTS backups (
  id TEXT PRIMARY KEY,
  created_at INTEGER NOT NULL,
  bytes INTEGER NOT NULL,
  chunks INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS backup_chunks (
  backup_id TEXT NOT NULL,
  idx INTEGER NOT NULL,
  data BLOB NOT NULL,
  PRIMARY KEY (backup_id, idx)
);
