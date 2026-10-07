package com.kissmissi.csbridge

object Ui {
    fun page(): String = """<!DOCTYPE html>
<html lang="en" data-base-theme="slate">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
<title>CloudStream Bridge</title>
<link rel="icon" type="image/svg+xml" href="/logo.svg">
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&display=swap" rel="stylesheet">
<style>
:root, [data-base-theme="slate"] {
  --bg:#0f172a; --surface:#1e293b; --surface-active:#334155; --card-hover:#243248;
  --border:#334155; --border-focus:#475569; --text:#ffffff; --text-sub:#cbd5e1; --text-dim:#94a3b8;
  --accent:#8b5cf6; --accent-glow:rgba(139,92,246,.28); --green:#10b981; --red:#f43f5e; --amber:#f59e0b;
  --radius:14px;
}
[data-base-theme="charcoal"] { --bg:#18181b; --surface:#27272a; --surface-active:#3f3f46; --card-hover:#323236; --border:#3f3f46; --border-focus:#52525b; --text:#fff; --text-sub:#d4d4d8; --text-dim:#a1a1aa; }
[data-base-theme="navy"] { --bg:#0d1117; --surface:#161b22; --surface-active:#21262d; --card-hover:#1c2128; --border:#30363d; --border-focus:#484f58; --text:#fff; --text-sub:#cbd5e1; --text-dim:#94a3b8; }
[data-base-theme="forest"] { --bg:#0c1512; --surface:#13221d; --surface-active:#1a2f28; --card-hover:#172a24; --border:#223c33; --border-focus:#2f5246; --text:#fff; --text-sub:#c7eedd; --text-dim:#8ecbb0; }
* { box-sizing:border-box; margin:0; padding:0; }
body { background:var(--bg); color:var(--text); font-family:'Inter',system-ui,sans-serif; min-height:100vh; padding:24px 16px 80px; }
.wrap { max-width:980px; margin:0 auto; }
header { display:flex; align-items:center; gap:14px; margin-bottom:8px; }
.logo { width:52px; height:52px; border-radius:12px; background:var(--accent); display:flex; align-items:center; justify-content:center; font-weight:800; font-size:22px; color:#fff; box-shadow:0 0 24px var(--accent-glow); }
h1 { font-size:24px; font-weight:800; letter-spacing:-.5px; }
.sub { color:var(--text-dim); font-size:13px; margin-top:2px; }
.status { display:flex; gap:10px; flex-wrap:wrap; margin:16px 0 8px; font-size:12.5px; color:var(--text-dim); }
.pill { background:var(--surface); border:1px solid var(--border); border-radius:999px; padding:5px 12px; }
.pill b { color:var(--text); }
.pill.ok b { color:var(--green); } .pill.warn b { color:var(--amber); }
section { background:var(--surface); border:1px solid var(--border); border-radius:var(--radius); padding:18px; margin-top:16px; }
.sec-head { display:flex; align-items:center; justify-content:space-between; gap:10px; flex-wrap:wrap; margin-bottom:12px; }
.sec-title { font-size:15px; font-weight:700; }
.sec-title small { color:var(--text-dim); font-weight:500; margin-left:6px; }
.btn { background:var(--surface-active); color:var(--text-sub); border:1px solid var(--border); border-radius:8px; padding:7px 12px; font-size:12.5px; font-weight:600; cursor:pointer; font-family:inherit; transition:all .15s; }
.btn:hover { border-color:var(--border-focus); color:var(--text); }
.btn.primary { background:var(--accent); border-color:var(--accent); color:#fff; box-shadow:0 0 18px var(--accent-glow); }
.btn.primary:hover { filter:brightness(1.1); }
.search { width:100%; background:var(--bg); border:1px solid var(--border); border-radius:10px; color:var(--text); padding:10px 14px; font-size:14px; font-family:inherit; outline:none; margin-bottom:14px; }
.search:focus { border-color:var(--accent); }
.plist { display:flex; flex-direction:column; gap:8px; max-height:420px; overflow-y:auto; padding-right:4px; }
.plist::-webkit-scrollbar { width:8px; } .plist::-webkit-scrollbar-thumb { background:var(--surface-active); border-radius:4px; }
.prov { display:flex; align-items:center; gap:12px; background:var(--bg); border:1px solid var(--border); border-radius:10px; padding:10px 12px; transition:all .15s; }
.prov:hover { background:var(--card-hover); }
.prov img { width:36px; height:36px; border-radius:8px; object-fit:contain; background:var(--surface-active); flex-shrink:0; }
.prov .nm { font-weight:600; font-size:13.5px; }
.prov .ds { color:var(--text-dim); font-size:12px; margin-top:1px; display:-webkit-box; -webkit-line-clamp:1; -webkit-box-orient:vertical; overflow:hidden; }
.prov .chips { display:flex; gap:5px; margin-top:4px; flex-wrap:wrap; }
.chip { font-size:10.5px; padding:2px 7px; border-radius:999px; background:var(--surface-active); color:var(--text-dim); border:1px solid var(--border); }
.chip.lang { color:var(--text-sub); }
.chip.err { color:var(--red); border-color:var(--red); }
.chip.ok { color:var(--green); border-color:var(--green); }
.grow { flex:1; min-width:0; }
.switch { position:relative; width:40px; height:22px; flex-shrink:0; cursor:pointer; }
.switch input { opacity:0; width:0; height:0; }
.sl { position:absolute; inset:0; background:var(--surface-active); border-radius:999px; transition:.2s; border:1px solid var(--border); }
.sl:before { content:""; position:absolute; width:16px; height:16px; border-radius:50%; background:var(--text-dim); top:2px; left:2px; transition:.2s; }
.switch input:checked + .sl { background:var(--accent); border-color:var(--accent); }
.switch input:checked + .sl:before { transform:translateX(18px); background:#fff; }
.opt-row { display:flex; align-items:center; justify-content:space-between; gap:12px; padding:10px 0; border-bottom:1px solid var(--border); }
.opt-row:last-child { border-bottom:none; }
.opt-row .nm { font-weight:600; font-size:13.5px; }
.opt-row .ds { color:var(--text-dim); font-size:12px; margin-top:2px; }
.gen-url { width:100%; background:var(--bg); border:1px solid var(--border-focus); border-radius:10px; color:var(--text); padding:12px 14px; font-size:12.5px; font-family:ui-monospace,monospace; word-break:break-all; outline:none; }
.actions { display:flex; gap:10px; margin-top:12px; flex-wrap:wrap; }
.note { color:var(--text-dim); font-size:12.5px; margin-top:12px; line-height:1.6; }
.note b { color:var(--text-sub); }
.themes { display:flex; gap:8px; }
.th { width:22px; height:22px; border-radius:50%; border:2px solid var(--border); cursor:pointer; }
.th.sel { border-color:var(--accent); }
.th.slate { background:#1e293b; } .th.charcoal { background:#27272a; } .th.navy { background:#161b22; } .th.forest { background:#13221d; }
footer { text-align:center; color:var(--text-dim); font-size:11.5px; margin-top:24px; }
.spin { display:inline-block; width:12px; height:12px; border:2px solid var(--text-dim); border-top-color:var(--accent); border-radius:50%; animation:sp 1s linear infinite; vertical-align:-2px; }
@keyframes sp { to { transform:rotate(360deg); } }
</style>
</head>
<body>
<div class="wrap">
  <header>
    <div class="logo">CS</div>
    <div>
      <h1>CloudStream Bridge</h1>
      <div class="sub">Runs CloudStream extension repos on this server and serves them as a Stremio/Nuvio stream addon</div>
    </div>
  </header>

  <div class="status">
    <span class="pill" id="st-plugins"><b>…</b> plugins</span>
    <span class="pill" id="st-sync">status unknown</span>
    <span class="pill" style="margin-left:auto">
      theme
      <span class="themes" style="display:inline-flex;gap:6px;vertical-align:middle;margin-left:6px">
        <span class="th slate sel" data-t="slate" title="Slate"></span>
        <span class="th charcoal" data-t="charcoal" title="Charcoal"></span>
        <span class="th navy" data-t="navy" title="Navy"></span>
        <span class="th forest" data-t="forest" title="Forest"></span>
      </span>
    </span>
  </div>

  <section>
    <div class="sec-head">
      <div class="sec-title">Providers<small>pick what this addon serves</small></div>
      <div style="display:flex;gap:8px">
        <button class="btn" id="all-on">Enable all</button>
        <button class="btn" id="all-off">Disable all</button>
      </div>
    </div>
    <input class="search" id="filter" placeholder="Filter providers…">
    <div id="repos"></div>
  </section>

  <section>
    <div class="sec-head"><div class="sec-title">Options</div></div>
    <div class="opt-row">
      <div><div class="nm">Catalogs</div><div class="ds">Expose each provider's home rows as browseable catalogs (plus search)</div></div>
      <label class="switch"><input type="checkbox" id="opt-c" checked><span class="sl"></span></label>
    </div>
    <div class="opt-row">
      <div><div class="nm">Torrent / magnet links</div><div class="ds">Include magnet links from torrent providers (needs an external torrent client)</div></div>
      <label class="switch"><input type="checkbox" id="opt-m"><span class="sl"></span></label>
    </div>
  </section>

  <section>
    <div class="sec-head"><div class="sec-title">Your addon URL</div></div>
    <input class="gen-url" id="murl" readonly value="loading…">
    <div class="actions">
      <button class="btn primary" id="copy">📋 Copy manifest URL</button>
      <button class="btn" id="open">Open manifest</button>
    </div>
    <div class="note">
      <b>Nuvio:</b> Settings → Addons → + → paste the URL (or add it remotely from the VPS Updates bot → /nuvio).<br>
      <b>Stremio:</b> paste the URL in the addons search bar. Streams are searched across all enabled providers per title.
    </div>
  </section>

  <footer>CloudStream Bridge • runs phisher / CSX / raghav CloudStream extensions server-side via dex2jar</footer>
</div>
<script>
let DATA = null;
let state = { p: {}, c: true, m: false };
const B64 = s => btoa(unescape(encodeURIComponent(s))).replace(/\+/g,'-').replace(/\//g,'_').replace(/=+$/,'');
try { const s = localStorage.getItem('csb_state'); if (s) state = Object.assign(state, JSON.parse(s)); } catch(e) {}
document.documentElement.dataset.baseTheme = localStorage.getItem('csb_theme') || 'slate';
document.querySelectorAll('.th').forEach(t => t.classList.toggle('sel', t.dataset.t === document.documentElement.dataset.baseTheme));
document.querySelectorAll('.th').forEach(t => t.onclick = () => {
  document.documentElement.dataset.baseTheme = t.dataset.t;
  localStorage.setItem('csb_theme', t.dataset.t);
  document.querySelectorAll('.th').forEach(x => x.classList.toggle('sel', x === t));
});

function esc(s){ return (s||'').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;'); }

function render() {
  const root = document.getElementById('repos');
  const q = document.getElementById('filter').value.toLowerCase();
  root.innerHTML = '';
  (DATA.repos||[]).forEach(repo => {
    const sec = document.createElement('div');
    sec.style.marginBottom = '14px';
    const head = document.createElement('div');
    head.className = 'sec-head';
    const on = repo.plugins.filter(p => state.p[p.internalName]).length;
    head.innerHTML = '<div class="sec-title">' + esc(repo.name) + '<small>' + on + '/' + repo.plugins.length + ' enabled</small></div>';
    const btns = document.createElement('div');
    btns.style.cssText = 'display:flex;gap:8px';
    const bOn = document.createElement('button'); bOn.className='btn'; bOn.textContent='All';
    const bOff = document.createElement('button'); bOff.className='btn'; bOff.textContent='None';
    bOn.onclick = () => { repo.plugins.forEach(p => state.p[p.internalName] = 1); save(); render(); };
    bOff.onclick = () => { repo.plugins.forEach(p => delete state.p[p.internalName]); save(); render(); };
    btns.append(bOn, bOff); head.append(btns); sec.append(head);
    const list = document.createElement('div'); list.className = 'plist';
    repo.plugins.forEach(p => {
      if (q && !(p.name+' '+p.internalName+' '+(p.description||'')).toLowerCase().includes(q)) return;
      const row = document.createElement('div'); row.className = 'prov';
      const chips = (p.tvTypes||[]).slice(0,3).map(t => '<span class="chip">'+esc(t)+'</span>').join('')
        + (p.language ? '<span class="chip lang">'+esc(p.language)+'</span>' : '')
        + (p.loaded ? '<span class="chip ok">loaded</span>' : '<span class="chip err">load failed</span>');
      row.innerHTML =
        '<img src="' + esc(p.iconUrl||'/logo.svg') + '" onerror="this.src=\'/logo.svg\'">' +
        '<div class="grow"><div class="nm">' + esc(p.name) + ' <span style="color:var(--text-dim);font-weight:500">v'+p.version+'</span></div>' +
        '<div class="ds">' + esc(p.description||'') + '</div><div class="chips">' + chips + '</div></div>';
      const sw = document.createElement('label'); sw.className = 'switch';
      const inp = document.createElement('input'); inp.type = 'checkbox';
      inp.checked = !!state.p[p.internalName];
      inp.onchange = () => { if (inp.checked) state.p[p.internalName] = 1; else delete state.p[p.internalName]; save(); updateCount(); gen(); };
      const sl = document.createElement('span'); sl.className = 'sl';
      sw.append(inp, sl); row.append(sw);
      list.append(row);
    });
    sec.append(list); root.append(sec);
  });
  updateCount(); gen();
}
function updateCount() {
  const all = (DATA.repos||[]).flatMap(r => r.plugins);
  const on = all.filter(p => state.p[p.internalName]).length;
  document.getElementById('st-plugins').innerHTML = '<b>' + on + '</b> / ' + all.length + ' plugins enabled';
}
function save(){ localStorage.setItem('csb_state', JSON.stringify(state)); }
function gen() {
  const url = location.origin + '/' + B64(JSON.stringify(state)) + '/manifest.json';
  document.getElementById('murl').value = url;
}
document.getElementById('filter').oninput = render;
document.getElementById('all-on').onclick = () => { (DATA.repos||[]).flatMap(r=>r.plugins).forEach(p => state.p[p.internalName] = 1); save(); render(); };
document.getElementById('all-off').onclick = () => { state.p = {}; save(); render(); };
document.getElementById('opt-c').onchange = e => { state.c = e.target.checked; save(); gen(); };
document.getElementById('opt-m').onchange = e => { state.m = e.target.checked; save(); gen(); };
document.getElementById('copy').onclick = () => {
  const v = document.getElementById('murl').value;
  (navigator.clipboard ? navigator.clipboard.writeText(v) : Promise.reject()).then(() => {
    const b = document.getElementById('copy'); const t = b.textContent; b.textContent = '✅ Copied!';
    setTimeout(() => b.textContent = t, 1500);
  }).catch(() => { document.getElementById('murl').select(); document.execCommand('copy'); });
};
document.getElementById('open').onclick = () => window.open(document.getElementById('murl').value, '_blank');

async function boot() {
  document.getElementById('st-sync').innerHTML = '<span class="spin"></span> loading repos…';
  try {
    const r = await fetch('/api/repos'); DATA = await r.json();
    (DATA.repos||[]).flatMap(r2=>r2.plugins).forEach(p => {
      if (!(p.internalName in state.p) && p.loaded) state.p[p.internalName] = 1;
    });
    document.getElementById('opt-c').checked = !!state.c;
    document.getElementById('opt-m').checked = !!state.m;
    document.getElementById('st-sync').innerHTML = DATA.syncing
      ? '<span class="spin"></span> syncing…' 
      : 'sync ' + new Date(DATA.lastSync).toLocaleTimeString();
    render();
  } catch(e) {
    document.getElementById('st-sync').innerHTML = '⚠ failed to load repo data';
  }
}
boot();
setInterval(async () => {
  try { const r = await fetch('/api/repos'); const d = await r.json();
    document.getElementById('st-sync').innerHTML = d.syncing ? '<span class="spin"></span> syncing…' : 'sync ' + new Date(d.lastSync).toLocaleTimeString();
    if (!d.syncing && d.lastSync !== DATA.lastSync) { DATA = d; render(); }
  } catch(e) {}
}, 10000);
</script>
</body>
</html>""".trimIndent()
}
