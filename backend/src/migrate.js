import { readdir, readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import pg from "pg";

const migrationsDirectory = fileURLToPath(new URL("../migrations/", import.meta.url));
const databaseUrl = process.env.DATABASE_URL?.trim();

if (!databaseUrl) {
  throw new Error("DATABASE_URL is required to run database migrations");
}

const pool = new pg.Pool({ connectionString: databaseUrl, max: 1 });

try {
  const client = await pool.connect();
  try {
    await client.query("SELECT pg_advisory_lock(hashtext('swiftpay_backend_migrations'))");
    await client.query(`
    CREATE TABLE IF NOT EXISTS schema_migrations (
      version TEXT PRIMARY KEY,
      applied_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    )
  `);

    const migrations = (await readdir(migrationsDirectory))
      .filter((name) => /^\d+_[a-z0-9_]+\.sql$/.test(name))
      .sort();

    for (const version of migrations) {
      await client.query("BEGIN");
      const applied = await client.query(
        "SELECT 1 FROM schema_migrations WHERE version = $1",
        [version]
      );
      if (!applied.rowCount) {
        const sql = await readFile(new URL(`../migrations/${version}`, import.meta.url), "utf8");
        await client.query(sql);
        await client.query("INSERT INTO schema_migrations (version) VALUES ($1)", [version]);
        console.log(`Applied migration ${version}`);
      }
      await client.query("COMMIT");
    }
  } catch (error) {
    await client.query("ROLLBACK");
    throw error;
  } finally {
    client.release();
  }
} finally {
  await pool.end();
}
