/**
 * Client-side encryption. Nothing in this file talks to the network.
 *
 *   password ──PBKDF2-SHA256 (600k, salt = app + username)──▶ master key
 *   master key ──HKDF "auth"──▶ authKey   (sent to the server, which stores only bcrypt(authKey))
 *   master key ──HKDF "enc" ──▶ KEK       (never leaves the browser)
 *   KEK wraps a random 256-bit data key (DEK); the server stores only the wrapped DEK.
 *   DEK encrypts each entry with AES-256-GCM, binding the entry id as associated data.
 *
 * The server and database therefore never hold anything that decrypts journal entries.
 * Consequence: if a user forgets their password, their entries cannot be recovered by anyone.
 */

export const KDF_ITERATIONS = 600_000;
const enc = new TextEncoder();
const dec = new TextDecoder();
const subtle = globalThis.crypto.subtle;

export type Sentiment = 'HAPPY' | 'SAD' | 'ANGRY' | 'ANXIOUS';
export const SENTIMENTS: Sentiment[] = ['HAPPY', 'SAD', 'ANGRY', 'ANXIOUS'];

export interface EntryPlaintext {
  title: string;
  content: string;
  sentiment: Sentiment | null;
}

export interface DerivedKeys {
  /** 64 lowercase hex chars; proves knowledge of the password without revealing it. */
  authKey: string;
  /** Key-encryption key; wraps/unwraps the data key. Non-extractable. */
  kek: CryptoKey;
}

export interface WrappedKey {
  wrappedKey: string;
  wrappedKeyIv: string;
}

// ---------- encoding helpers ----------

export function toBase64(bytes: ArrayBuffer | Uint8Array): string {
  const u8 = bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes);
  let s = '';
  for (let i = 0; i < u8.length; i++) s += String.fromCharCode(u8[i]);
  return btoa(s);
}

export function fromBase64(b64: string): Uint8Array<ArrayBuffer> {
  const s = atob(b64);
  const u8 = new Uint8Array(new ArrayBuffer(s.length));
  for (let i = 0; i < s.length; i++) u8[i] = s.charCodeAt(i);
  return u8;
}

function toHex(buf: ArrayBuffer): string {
  return Array.from(new Uint8Array(buf), (b) => b.toString(16).padStart(2, '0')).join('');
}

function randomIv(): Uint8Array<ArrayBuffer> {
  return globalThis.crypto.getRandomValues(new Uint8Array(new ArrayBuffer(12)));
}

// ---------- key derivation ----------

export async function deriveKeys(username: string, password: string, iterations = KDF_ITERATIONS): Promise<DerivedKeys> {
  const passwordKey = await subtle.importKey('raw', enc.encode(password), 'PBKDF2', false, ['deriveBits']);
  const master = await subtle.deriveBits(
    { name: 'PBKDF2', hash: 'SHA-256', salt: enc.encode(`journal-app/v1/${username.toLowerCase()}`), iterations },
    passwordKey,
    256,
  );
  const hkdfBase = await subtle.importKey('raw', master, 'HKDF', false, ['deriveBits', 'deriveKey']);
  const hkdf = (info: string) => ({ name: 'HKDF', hash: 'SHA-256', salt: new Uint8Array(32), info: enc.encode(info) });

  const authKey = toHex(await subtle.deriveBits(hkdf('journal-app/auth'), hkdfBase, 256));
  const kek = await subtle.deriveKey(hkdf('journal-app/kek'), hkdfBase, { name: 'AES-GCM', length: 256 }, false, [
    'wrapKey',
    'unwrapKey',
  ]);
  return { authKey, kek };
}

// ---------- data key ----------

const DEK_AAD = enc.encode('journal-app/dek/v1');

/** A fresh data key. Extractable only so that it can be wrapped once; use the unwrapped (non-extractable) copy afterwards. */
export function generateDataKey(): Promise<CryptoKey> {
  return subtle.generateKey({ name: 'AES-GCM', length: 256 }, true, ['encrypt', 'decrypt']);
}

export async function wrapDataKey(dataKey: CryptoKey, kek: CryptoKey): Promise<WrappedKey> {
  const iv = randomIv();
  const wrapped = await subtle.wrapKey('raw', dataKey, kek, { name: 'AES-GCM', iv, additionalData: DEK_AAD });
  return { wrappedKey: toBase64(wrapped), wrappedKeyIv: toBase64(iv) };
}

/** Throws if the password (and hence the KEK) is wrong: AES-GCM authentication fails. */
export function unwrapDataKey(w: WrappedKey, kek: CryptoKey, extractable = false): Promise<CryptoKey> {
  return subtle.unwrapKey(
    'raw',
    fromBase64(w.wrappedKey),
    kek,
    { name: 'AES-GCM', iv: fromBase64(w.wrappedKeyIv), additionalData: DEK_AAD },
    { name: 'AES-GCM', length: 256 },
    extractable,
    ['encrypt', 'decrypt'],
  );
}

// ---------- entries ----------

const entryAad = (entryId: string) => enc.encode(`journal-app/entry/v1/${entryId}`);

export async function encryptEntry(dataKey: CryptoKey, entryId: string, entry: EntryPlaintext) {
  const iv = randomIv();
  const ct = await subtle.encrypt(
    { name: 'AES-GCM', iv, additionalData: entryAad(entryId) },
    dataKey,
    enc.encode(JSON.stringify({ t: entry.title, c: entry.content, s: entry.sentiment })),
  );
  return { ciphertext: toBase64(ct), iv: toBase64(iv) };
}

/** Throws if the ciphertext was modified or moved to another entry id. */
export async function decryptEntry(dataKey: CryptoKey, entryId: string, ciphertext: string, iv: string): Promise<EntryPlaintext> {
  const pt = await subtle.decrypt(
    { name: 'AES-GCM', iv: fromBase64(iv), additionalData: entryAad(entryId) },
    dataKey,
    fromBase64(ciphertext),
  );
  const o = JSON.parse(dec.decode(pt)) as { t?: unknown; c?: unknown; s?: unknown };
  return {
    title: typeof o.t === 'string' ? o.t : '',
    content: typeof o.c === 'string' ? o.c : '',
    sentiment: SENTIMENTS.includes(o.s as Sentiment) ? (o.s as Sentiment) : null,
  };
}

// ---------- password rules ----------

export const MIN_PASSWORD_LENGTH = 10;

export function passwordProblem(password: string, username: string): string | null {
  if (password.length < MIN_PASSWORD_LENGTH) return `Use at least ${MIN_PASSWORD_LENGTH} characters.`;
  if (password.toLowerCase().includes(username.toLowerCase()) && username.length >= 3)
    return 'Your password should not contain your username.';
  return null;
}
