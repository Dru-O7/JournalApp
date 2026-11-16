import { describe, expect, it } from 'vitest';
import {
  decryptEntry,
  deriveKeys,
  encryptEntry,
  generateDataKey,
  passwordProblem,
  unwrapDataKey,
  wrapDataKey,
} from './crypto';

// Lower iteration count keeps the suite fast; the derivation logic is identical.
const IT = 1000;

describe('key derivation', () => {
  it('is deterministic, case-insensitive in the username, and separates auth from encryption', async () => {
    const a = await deriveKeys('Alice', 'correct horse battery', IT);
    const b = await deriveKeys('alice', 'correct horse battery', IT);
    expect(a.authKey).toMatch(/^[0-9a-f]{64}$/);
    expect(a.authKey).toBe(b.authKey);
    expect((await deriveKeys('alice', 'other password!!', IT)).authKey).not.toBe(a.authKey);
    expect((await deriveKeys('bob', 'correct horse battery', IT)).authKey).not.toBe(a.authKey);
  });

  it('never exposes the key-encryption key', async () => {
    const { kek } = await deriveKeys('alice', 'correct horse battery', IT);
    expect(kek.extractable).toBe(false);
  });
});

describe('data key wrapping', () => {
  it('unwraps with the right password and fails with the wrong one', async () => {
    const dek = await generateDataKey();
    const right = await deriveKeys('alice', 'correct horse battery', IT);
    const wrapped = await wrapDataKey(dek, right.kek);

    const unwrapped = await unwrapDataKey(wrapped, right.kek);
    expect(unwrapped.extractable).toBe(false);

    const wrong = await deriveKeys('alice', 'wrong password!!', IT);
    await expect(unwrapDataKey(wrapped, wrong.kek)).rejects.toThrow();
  });

  it('re-wrapping on password change keeps old entries readable', async () => {
    const dek = await generateDataKey();
    const oldKeys = await deriveKeys('alice', 'old password 123', IT);
    const wrappedOld = await wrapDataKey(dek, oldKeys.kek);
    const live = await unwrapDataKey(wrappedOld, oldKeys.kek);
    const entry = await encryptEntry(live, 'e1', { title: 'T', content: 'C', sentiment: 'HAPPY' });

    // password change: unwrap (extractable) with old KEK, wrap with new KEK
    const extractable = await unwrapDataKey(wrappedOld, oldKeys.kek, true);
    const newKeys = await deriveKeys('alice2', 'new password 456', IT);
    const wrappedNew = await wrapDataKey(extractable, newKeys.kek);
    const liveNew = await unwrapDataKey(wrappedNew, newKeys.kek);

    expect(await decryptEntry(liveNew, 'e1', entry.ciphertext, entry.iv)).toEqual({
      title: 'T',
      content: 'C',
      sentiment: 'HAPPY',
    });
  });
});

describe('entry encryption', () => {
  it('round-trips, hides the plaintext, and uses a fresh IV each time', async () => {
    const dek = await generateDataKey();
    const plain = { title: 'Dear diary', content: 'A secret thought 🌙', sentiment: 'ANXIOUS' as const };
    const a = await encryptEntry(dek, 'id-1', plain);
    const b = await encryptEntry(dek, 'id-1', plain);
    expect(a.ciphertext).not.toContain('secret');
    expect(atob(a.ciphertext)).not.toContain('secret');
    expect(a.iv).not.toBe(b.iv);
    expect(await decryptEntry(dek, 'id-1', a.ciphertext, a.iv)).toEqual(plain);
  });

  it('rejects ciphertext moved to another entry id (server cannot swap entries)', async () => {
    const dek = await generateDataKey();
    const a = await encryptEntry(dek, 'id-1', { title: 't', content: 'c', sentiment: null });
    await expect(decryptEntry(dek, 'id-2', a.ciphertext, a.iv)).rejects.toThrow();
  });

  it('rejects tampered ciphertext', async () => {
    const dek = await generateDataKey();
    const a = await encryptEntry(dek, 'id-1', { title: 't', content: 'c', sentiment: null });
    const bytes = Uint8Array.from(atob(a.ciphertext), (c) => c.charCodeAt(0));
    bytes[0] ^= 1;
    const tampered = btoa(String.fromCharCode(...bytes));
    await expect(decryptEntry(dek, 'id-1', tampered, a.iv)).rejects.toThrow();
  });

  it("another user's key cannot decrypt", async () => {
    const mine = await generateDataKey();
    const theirs = await generateDataKey();
    const a = await encryptEntry(mine, 'id-1', { title: 't', content: 'c', sentiment: null });
    await expect(decryptEntry(theirs, 'id-1', a.ciphertext, a.iv)).rejects.toThrow();
  });
});

describe('password rules', () => {
  it('requires length and rejects passwords containing the username', () => {
    expect(passwordProblem('short', 'alice')).not.toBeNull();
    expect(passwordProblem('alice-is-great', 'alice')).not.toBeNull();
    expect(passwordProblem('a long passphrase', 'alice')).toBeNull();
  });
});
