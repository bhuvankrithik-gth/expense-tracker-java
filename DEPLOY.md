# Deploying Ledgerline (free tier)

The app is a single Docker container; it needs a MySQL-compatible database.
Everything is configured through environment variables — no code changes.

## 1. Create a free hosted MySQL database

Pick one (both have free tiers):

**Option A — Aiven (free trial credits, MySQL 8):**
1. Sign up at https://aiven.io (GitHub login works).
2. Create a service → MySQL → free plan → pick a region near you.
3. Wait until status is "Running", open the service → copy the **Service URI**
   (looks like `mysql://avnadmin:xxxx@host:port/defaultdb`).

**Option B — TiDB Serverless (generous free tier, MySQL-compatible):**
1. Sign up at https://tidbcloud.com (GitHub login works).
2. Create a Serverless cluster → note the host, port (4000), user, password.
3. In the cluster's SQL editor, run: `CREATE DATABASE expensetracker;`
   (TiDB gives you a `test` database by default; the app needs `expensetracker`,
   or set `DB_NAME=test`.)

The app creates all tables itself on first boot (`Db.initSchema()` runs
`schema.sql`), so you only need the empty database + credentials.

## 2. Deploy the app on Render (free)

1. Push this repo to GitHub (see README quick start).
2. Sign up at https://render.com (GitHub login works).
3. **New → Web Service** → connect the `expense-tracker-java` repo.
4. Settings:
   - **Runtime:** Docker
   - **Instance type:** Free
   - **Dockerfile path:** `./Dockerfile` (default)
5. **Environment variables** (Render dashboard → Environment):
   | Key | Value |
   |---|---|
   | `DB_HOST` | your MySQL host (from step 1) |
   | `DB_PORT` | `3306` (Aiven) or `4000` (TiDB) |
   | `DB_NAME` | `expensetracker` |
   | `DB_USER` | your MySQL user |
   | `DB_PASSWORD` | your MySQL password |
   - Render sets `PORT` automatically; the Dockerfile maps it to `APP_PORT`.
   - Do **NOT** set `DB_SOCKET` (that's only for the sandbox dev setup).
6. **Deploy.** First boot creates the tables; open the URL Render gives you
   (e.g. `https://ledgerline-xxxx.onrender.com`) and sign up.

> Free-tier note: Render spins the service down after inactivity; the first
> visit after idle takes ~30–60 s to wake. Data lives in your hosted MySQL,
> so nothing is lost.

## 3. Alternative: Railway

1. Sign up at https://railway.app → **New Project → Deploy from GitHub repo**.
2. Add the same `DB_*` environment variables (Railway also injects `PORT`).
3. Railway can also provision a MySQL plugin (free trial credits) if you'd
   rather keep everything in one place: **New → Database → MySQL**, then copy
   its connection values into the app's env vars.

## 4. Verify the live deployment

1. Open the live URL → sign up a new account.
2. Add an expense + an income → check they appear on `/transactions`.
3. Set a monthly budget lower than your spend → confirm the warning banner.
4. Visit `/reports` → confirm the SVG charts render.
5. Export CSV from `/settings` → confirm it downloads.

## Troubleshooting

| Symptom | Likely cause |
|---|---|
| App boots but pages show "Database error" | `DB_HOST`/`DB_PORT`/`DB_USER`/`DB_PASSWORD` wrong, or the DB firewall blocks Render's IPs (Aiven: add `0.0.0.0/0` in "IP allowlist" for testing, tighten later) |
| `Unknown database 'expensetracker'` | Create the database first (step 1); the app creates *tables*, not the database |
| Build fails on Render | Check the deploy logs; `mvn -q -DskipTests package` must succeed — it does locally, so this is usually a transient network issue; redeploy |
