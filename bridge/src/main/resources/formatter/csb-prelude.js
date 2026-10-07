/*
 * GraalJS prelude for the AIOStreams formatter bundle.
 * The bundle is esbuild CommonJS built for Node, so it needs a `module`/
 * `exports` pair (read back by csb-glue.js) and a minimal `Buffer` — the only
 * Node API it touches (utils/constants.ts base64-decodes a header constant at
 * top level, and the ::base64 modifier). Pure-JS base64 keeps this independent
 * of any GraalJS option set.
 */
var module = { exports: {} };
var exports = module.exports;
var __B64C = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
function __b64encode(bytes) {
  var out = "";
  for (var i = 0; i < bytes.length; i += 3) {
    var b1 = bytes[i], b2 = bytes[i + 1], b3 = bytes[i + 2];
    var has2 = i + 1 < bytes.length, has3 = i + 2 < bytes.length;
    out += __B64C[b1 >> 2]
      + __B64C[((b1 & 3) << 4) | (has2 ? b2 >> 4 : 0)]
      + (has2 ? __B64C[((b2 & 15) << 2) | (has3 ? b3 >> 6 : 0)] : "=")
      + (has3 ? __B64C[b3 & 63] : "=");
  }
  return out;
}
function __b64decode(str) {
  var clean = String(str).replace(/[^A-Za-z0-9+/=]/g, "");
  var out = [];
  var buf = 0, bits = 0;
  for (var i = 0; i < clean.length; i++) {
    var c = clean.charAt(i);
    if (c === "=") break;
    var v = __B64C.indexOf(c);
    if (v < 0) continue;
    buf = (buf << 6) | v;
    bits += 6;
    if (bits >= 8) {
      bits -= 8;
      out.push((buf >> bits) & 0xff);
    }
  }
  return out;
}
function __utf8encode(s) {
  s = String(s);
  var out = [];
  for (var i = 0; i < s.length; i++) {
    var c = s.codePointAt(i);
    if (c > 0xffff) i++; // surrogate pair consumed by codePointAt
    if (c < 0x80) out.push(c);
    else if (c < 0x800) out.push(0xc0 | (c >> 6), 0x80 | (c & 63));
    else if (c < 0x10000) out.push(0xe0 | (c >> 12), 0x80 | ((c >> 6) & 63), 0x80 | (c & 63));
    else out.push(0xf0 | (c >> 18), 0x80 | ((c >> 12) & 63), 0x80 | ((c >> 6) & 63), 0x80 | (c & 63));
  }
  return out;
}
function __utf8decode(bytes) {
  var out = "";
  for (var i = 0; i < bytes.length;) {
    var b = bytes[i];
    var cp;
    if (b < 0x80) { cp = b; i += 1; }
    else if (b < 0xe0) { cp = ((b & 31) << 6) | (bytes[i + 1] & 63); i += 2; }
    else if (b < 0xf0) { cp = ((b & 15) << 12) | ((bytes[i + 1] & 63) << 6) | (bytes[i + 2] & 63); i += 3; }
    else { cp = ((b & 7) << 18) | ((bytes[i + 1] & 63) << 12) | ((bytes[i + 2] & 63) << 6) | (bytes[i + 3] & 63); i += 4; }
    out += String.fromCodePoint(cp);
  }
  return out;
}
globalThis.Buffer = {
  from: function (value, enc) {
    var bytes = enc === "base64" ? __b64decode(value) : __utf8encode(value);
    return {
      toString: function (e) {
        return e === "base64" ? __b64encode(bytes) : __utf8decode(bytes);
      }
    };
  },
};
