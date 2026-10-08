// csbridge formatter host (Formatter.kt NodeHost): runs the AIOStreams formatter bundle (the same
// files GraalJS loads) under V8. One JSON request per stdin line:
// {"id":1,"fn":"renderBatch","args":[...]} -> {"id":1,"ok":true,"v":"..."}
"use strict";
const vm = require("vm");
const fs = require("fs");
const path = require("path");
const dir = process.argv[2] || __dirname;
const read = (f) => fs.readFileSync(path.join(dir, f), "utf8");
const src = read("csb-prelude.js") + "\n" + read("aiostreams-formatter.js") + "\n" +
  "var __PENGUPLAY_PRESETS = " + read("penguplay-presets.json") + ";\n" + read("csb-glue.js");
const noop = () => {};
const ctx = vm.createContext({ console: { log: noop, info: noop, warn: noop, error: noop, debug: noop } });
vm.runInContext(src, ctx, { filename: "formatter.js" });
const api = ctx.__CSB;
if (!api) { process.stderr.write("formatter glue did not define __CSB\n"); process.exit(2); }
let buf = "";
process.stdin.setEncoding("utf8");
process.stdin.on("data", (chunk) => {
  buf += chunk;
  let i;
  while ((i = buf.indexOf("\n")) >= 0) {
    const line = buf.slice(0, i);
    buf = buf.slice(i + 1);
    if (!line) continue;
    let id = null, out;
    try {
      const req = JSON.parse(line);
      id = req.id;
      const v = api[req.fn].apply(api, req.args || []);
      out = { id, ok: true, v: v === null || v === undefined ? null : String(v) };
    } catch (e) {
      out = { id, ok: false, e: String((e && e.message) || e) };
    }
    process.stdout.write(JSON.stringify(out) + "\n");
  }
});
process.stdin.on("end", () => process.exit(0));
process.stdout.write(JSON.stringify({ id: 0, ok: true, v: "ready" }) + "\n");
