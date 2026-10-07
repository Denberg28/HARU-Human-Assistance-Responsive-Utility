import test from 'node:test';
import assert from 'node:assert/strict';
import { createHandler } from '../supabase/functions/haru-live-location/handler.ts';

function database() {
  const rows = new Map();
  const db = {
    rows, failDelete: false, missUpdate: false,
    from() {
      let operation = 'read', values, filters = [];
      const query = {
        insert(value) { rows.set(value.id, { ...value }); return Promise.resolve({ error: null }); },
        select() { return query; },
        update(value) { operation = 'update'; values = value; return query; },
        delete() { operation = 'delete'; return query; },
        eq(key, value) { filters.push(row => row[key] === value); return query; },
        gt(key, value) { filters.push(row => row[key] > value); return query; },
        async maybeSingle() {
          const row = [...rows.values()].find(row => filters.every(f => f(row)));
          if (operation === 'update' && row && !db.missUpdate) Object.assign(row, values);
          return { data: operation === 'update' && db.missUpdate ? null : row ?? null, error: null };
        },
        then(resolve, reject) {
          if (db.failDelete) return Promise.resolve({ error: { message: 'Test failure' } }).then(resolve, reject);
          for (const [id, row] of rows) if (filters.every(f => f(row))) rows.delete(id);
          return Promise.resolve({ error: null }).then(resolve, reject);
        },
      };
      return query;
    },
  };
  return db;
}

const id = '12345678-1234-4123-8123-123456789abc';
const readToken = 'r'.repeat(32), writeToken = 'w'.repeat(32);
const body = { action: 'create', session_id: id, read_token: readToken, write_token: writeToken,
  payload: 'encrypted-placeholder', ttl_minutes: 60 };
const request = value => new Request('https://test.invalid', { method: 'POST', body: JSON.stringify(value) });

test('bounded JSON body rejects oversized input without a Content-Length header', async () => {
  const handle = createHandler(database());
  assert.equal((await handle(request({ padding: 'x'.repeat(9000) }))).status, 413);
  assert.equal((await handle(request(null))).status, 400);
  assert.equal((await handle(request({ ...body, ttl_minutes: 'invalid' }))).status, 400);
});

test('create, authorize, sequence update, revoke, and missing session', async () => {
  const db = database(); let now = Date.now();
  const handle = createHandler(db, () => now);
  assert.equal((await handle(request(body))).status, 201);
  assert.equal((await handle(request({ action: 'read', session_id: id, read_token: 'x'.repeat(32) }))).status, 403);
  assert.equal((await handle(request({ action: 'read', session_id: id, read_token: readToken }))).status, 200);
  // A wrong writer is rejected before the rate limit, even immediately after create.
  assert.equal((await handle(request({ action: 'update', session_id: id, write_token: readToken }))).status, 403);
  now += 4000;
  const update = { action: 'update', session_id: id, write_token: writeToken, payload: 'updated-ciphertext', expected_seq: 1 };
  assert.equal((await handle(request(update))).status, 200);
  now += 4000;
  assert.equal((await handle(request(update))).status, 409);
  db.missUpdate = true;
  assert.equal((await handle(request({ ...update, expected_seq: 2 }))).status, 409);
  db.failDelete = true;
  const stop = { action: 'stop', session_id: id, write_token: writeToken };
  assert.equal((await handle(request(stop))).status, 500);
  db.failDelete = false;
  assert.equal((await handle(request(stop))).status, 200);
  assert.equal((await handle(request({ action: 'read', session_id: id, read_token: readToken }))).status, 404);
});

test('expired shares require valid credentials and cannot be read', async () => {
  const db = database(); let now = Date.now();
  const handle = createHandler(db, () => now);
  await handle(request(body));
  now += 3600_001;
  assert.equal((await handle(request({ action: 'read', session_id: id, read_token: 'x'.repeat(32) }))).status, 403);
  assert.equal(db.rows.size, 1);
  assert.equal((await handle(request({ action: 'read', session_id: id, read_token: readToken }))).status, 410);
  assert.equal(db.rows.size, 0);
});
