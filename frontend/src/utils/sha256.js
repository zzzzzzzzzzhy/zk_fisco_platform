// Minimal SHA-256 implementation (hex output) for environments without Web Crypto API.
// Accepts ArrayBuffer/Uint8Array and returns lowercase hex string.

function rotr(x, n) {
  return (x >>> n) | (x << (32 - n))
}

function toUint32(x) {
  return x >>> 0
}

const K = new Uint32Array([
  0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
  0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
  0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
  0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
  0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
  0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
  0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
  0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2
])

function bytesFrom(input) {
  if (input instanceof Uint8Array) return input
  if (input instanceof ArrayBuffer) return new Uint8Array(input)
  throw new Error('sha256: unsupported input type')
}

export function sha256Hex(input) {
  const bytes = bytesFrom(input)
  const bitLen = bytes.length * 8

  // Pad: 0x80, then zeros, then 64-bit length
  const withOne = bytes.length + 1
  const padZeros = (64 - ((withOne + 8) % 64)) % 64
  const totalLen = withOne + padZeros + 8
  const padded = new Uint8Array(totalLen)
  padded.set(bytes, 0)
  padded[bytes.length] = 0x80

  // append length in bits as big-endian 64-bit
  const view = new DataView(padded.buffer)
  const high = Math.floor(bitLen / 0x100000000)
  const low = bitLen >>> 0
  view.setUint32(totalLen - 8, high, false)
  view.setUint32(totalLen - 4, low, false)

  // Initial hash values
  let h0 = 0x6a09e667
  let h1 = 0xbb67ae85
  let h2 = 0x3c6ef372
  let h3 = 0xa54ff53a
  let h4 = 0x510e527f
  let h5 = 0x9b05688c
  let h6 = 0x1f83d9ab
  let h7 = 0x5be0cd19

  const w = new Uint32Array(64)

  for (let offset = 0; offset < padded.length; offset += 64) {
    // message schedule
    for (let i = 0; i < 16; i++) {
      w[i] = view.getUint32(offset + i * 4, false)
    }
    for (let i = 16; i < 64; i++) {
      const s0 = rotr(w[i - 15], 7) ^ rotr(w[i - 15], 18) ^ (w[i - 15] >>> 3)
      const s1 = rotr(w[i - 2], 17) ^ rotr(w[i - 2], 19) ^ (w[i - 2] >>> 10)
      w[i] = toUint32(w[i - 16] + s0 + w[i - 7] + s1)
    }

    // working vars
    let a = h0
    let b = h1
    let c = h2
    let d = h3
    let e = h4
    let f = h5
    let g = h6
    let h = h7

    for (let i = 0; i < 64; i++) {
      const S1 = rotr(e, 6) ^ rotr(e, 11) ^ rotr(e, 25)
      const ch = (e & f) ^ (~e & g)
      const temp1 = toUint32(h + S1 + ch + K[i] + w[i])
      const S0 = rotr(a, 2) ^ rotr(a, 13) ^ rotr(a, 22)
      const maj = (a & b) ^ (a & c) ^ (b & c)
      const temp2 = toUint32(S0 + maj)

      h = g
      g = f
      f = e
      e = toUint32(d + temp1)
      d = c
      c = b
      b = a
      a = toUint32(temp1 + temp2)
    }

    h0 = toUint32(h0 + a)
    h1 = toUint32(h1 + b)
    h2 = toUint32(h2 + c)
    h3 = toUint32(h3 + d)
    h4 = toUint32(h4 + e)
    h5 = toUint32(h5 + f)
    h6 = toUint32(h6 + g)
    h7 = toUint32(h7 + h)
  }

  const out = new Uint32Array([h0, h1, h2, h3, h4, h5, h6, h7])
  let hex = ''
  for (let i = 0; i < out.length; i++) {
    hex += out[i].toString(16).padStart(8, '0')
  }
  return hex
}

