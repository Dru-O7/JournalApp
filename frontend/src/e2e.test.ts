/**
 * End-to-end check against a running backend, using the real client crypto.
 * Skipped unless E2E_BASE_URL is set, e.g.:
 *   E2E_BASE_URL=http://localhost:8081 npx vitest run src/e2e.test.ts
 * Optionally set E2E_DUMP_FILE to a path the test writes raw API responses to, for inspection.
 */
import { describe, expect, it } from 'vitest';
import { decryptEntry, deriveKeys, encryptEntry, generateDataKey, unwrapDataKey, wrapDataKey } from './crypto';

const BASE = process.env.E2E_BASE_URL;

async function call(method: string, path: string, body?: unknown, token?: string) {
  const res = await fetch(`${BASE}/api/v1${path}`, {
    method,
    headers: {
      ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await res.text();
  return { status: res.status, json: text ? JSON.parse(text) : null, raw: text };
}

describe.skipIf(!BASE)('end-to-end against a running server', () => {
  const run = Date.now().toString(36);
  const SECRET_TEXT = `my deepest secret ${run}`;

  it('stores only ciphertext and keeps users isolated', async () => {
    // --- alice signs up and writes an entry
    const alicePw = 'alice correct horse';
    const ak = await deriveKeys(`alice_${run}`, alicePw);
    const aliceWrapped = await wrapDataKey(await generateDataKey(), ak.kek);
    const su = await call('POST', '/auth/signup', {
      username: `alice_${run}`, authKey: ak.authKey, ...aliceWrapped,
      email: `alice_${run}@example.com`, city: 'Pune', weeklyReport: true,
    });
    expect(su.status).toBe(201);
    expect(su.raw).not.toContain(alicePw);

    const login = await call('POST', '/auth/login', { username: `alice_${run}`, authKey: ak.authKey });
    expect(login.status).toBe(200);
    const aliceToken: string = login.json.token;
    const aliceKey = await unwrapDataKey(login.json.user, ak.kek);

    const entryId = crypto.randomUUID();
    const sealed = await encryptEntry(aliceKey, entryId, { title: 'Private', content: SECRET_TEXT, sentiment: 'SAD' });
    const created = await call('POST', '/entries', { id: entryId, ...sealed, reportSentiment: 'SAD' }, aliceToken);
    expect(created.status).toBe(201);

    // --- what the server sends back is ciphertext only
    const fetched = await call('GET', `/entries/${entryId}`, undefined, aliceToken);
    expect(fetched.raw).not.toContain(SECRET_TEXT);
    expect((await decryptEntry(aliceKey, entryId, fetched.json.ciphertext, fetched.json.iv)).content).toBe(SECRET_TEXT);

    // --- bob cannot read, change or delete alice's entry
    const bk = await deriveKeys(`bob_${run}`, 'bob another pass');
    const bobSu = await call('POST', '/auth/signup', {
      username: `bob_${run}`, authKey: bk.authKey, ...(await wrapDataKey(await generateDataKey(), bk.kek)),
    });
    const bobToken: string = bobSu.json.token;
    expect((await call('GET', `/entries/${entryId}`, undefined, bobToken)).status).toBe(404);
    expect((await call('PUT', `/entries/${entryId}`, { ...sealed, version: 0 }, bobToken)).status).toBe(404);
    expect((await call('DELETE', `/entries/${entryId}`, undefined, bobToken)).status).toBe(404);
    expect((await call('GET', '/entries', undefined, bobToken)).json.totalElements).toBe(0);

    // --- even with alice's ciphertext in hand, bob's key cannot decrypt it
    const bobKey = await unwrapDataKey(bobSu.json.user, bk.kek);
    await expect(decryptEntry(bobKey, entryId, fetched.json.ciphertext, fetched.json.iv)).rejects.toThrow();

    // --- non-admins are refused the admin API
    expect((await call('GET', '/admin/users', undefined, bobToken)).status).toBe(403);

    // --- password change keeps the entry readable and revokes the old token
    const ak2 = await deriveKeys(`alice_${run}`, 'alice new passphrase');
    const exportable = await unwrapDataKey(login.json.user, ak.kek, true);
    const rewrapped = await wrapDataKey(exportable, ak2.kek);
    const changed = await call('PUT', '/me/credentials', {
      currentAuthKey: ak.authKey, newUsername: `alice_${run}`, newAuthKey: ak2.authKey,
      newWrappedKey: rewrapped.wrappedKey, newWrappedKeyIv: rewrapped.wrappedKeyIv,
    }, aliceToken);
    expect(changed.status).toBe(200);
    expect((await call('GET', '/me', undefined, aliceToken)).status).toBe(401);
    const relogin = await call('POST', '/auth/login', { username: `alice_${run}`, authKey: ak2.authKey });
    const newKey = await unwrapDataKey(relogin.json.user, ak2.kek);
    expect((await decryptEntry(newKey, entryId, fetched.json.ciphertext, fetched.json.iv)).content).toBe(SECRET_TEXT);

    if (process.env.E2E_DUMP_FILE) {
      const fs = await import('node:fs');
      fs.writeFileSync(process.env.E2E_DUMP_FILE, JSON.stringify({ run, secret: SECRET_TEXT, alice: `alice_${run}` }));
    }
  });
});
