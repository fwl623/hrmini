/**
 * 登录密码传输加密：RSA-OAEP(SHA-256) + 服务端公钥。
 * Network 面板将只看到 Base64 密文，不再出现明文密码。
 */

function b64ToBytes(b64: string): ArrayBuffer {
  const bin = atob(b64);
  const bytes = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i += 1) {
    bytes[i] = bin.charCodeAt(i);
  }
  return bytes.buffer;
}

function bytesToB64(buf: ArrayBuffer): string {
  const bytes = new Uint8Array(buf);
  let bin = '';
  for (let i = 0; i < bytes.length; i += 1) {
    bin += String.fromCharCode(bytes[i]);
  }
  return btoa(bin);
}

async function importSpkiPublicKey(spkiBase64: string): Promise<CryptoKey> {
  return crypto.subtle.importKey(
    'spki',
    b64ToBytes(spkiBase64),
    { name: 'RSA-OAEP', hash: 'SHA-256' },
    false,
    ['encrypt'],
  );
}

/** 用服务端公钥加密登录密码，返回 Base64 密文 */
export async function encryptLoginPassword(
  plainPassword: string,
  publicKeySpkiBase64: string,
): Promise<string> {
  if (!plainPassword) {
    throw new Error('密码不能为空');
  }
  if (!window.crypto?.subtle) {
    throw new Error('当前浏览器不支持 Web Crypto，无法加密登录');
  }
  const key = await importSpkiPublicKey(publicKeySpkiBase64);
  const encoded = new TextEncoder().encode(plainPassword);
  const cipher = await crypto.subtle.encrypt({ name: 'RSA-OAEP' }, key, encoded);
  return bytesToB64(cipher);
}
