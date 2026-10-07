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
  --bg:#0f172a; --surface:#1e293b; --surface-active:#334155; --card-hover:#243248; --sidebar:#131c2e;
  --border:#334155; --border-focus:#475569; --text:#ffffff; --text-sub:#cbd5e1; --text-dim:#94a3b8;
  --accent:#8b5cf6; --accent-glow:rgba(139,92,246,.28); --green:#10b981; --red:#f43f5e; --amber:#f59e0b; --radius:14px;
}
[data-base-theme="charcoal"] { --bg:#18181b; --surface:#27272a; --surface-active:#3f3f46; --card-hover:#323236; --sidebar:#141416; --border:#3f3f46; --border-focus:#52525b; --text:#fff; --text-sub:#d4d4d8; --text-dim:#a1a1aa; }
[data-base-theme="navy"] { --bg:#0d1117; --surface:#161b22; --surface-active:#21262d; --card-hover:#1c2128; --sidebar:#10151c; --border:#30363d; --border-focus:#484f58; --text:#fff; --text-sub:#cbd5e1; --text-dim:#94a3b8; }
[data-base-theme="forest"] { --bg:#0c1512; --surface:#13221d; --surface-active:#1a2f28; --card-hover:#172a24; --sidebar:#0e1714; --border:#223c33; --border-focus:#2f5246; --text:#fff; --text-sub:#c7eedd; --text-dim:#8ecbb0; }
* { box-sizing:border-box; margin:0; padding:0; }
body { background:var(--bg); color:var(--text); font-family:'Inter',system-ui,sans-serif; min-height:100vh; }
.layout { display:flex; min-height:100vh; }
.sidebar { width:250px; flex-shrink:0; background:var(--sidebar); border-right:1px solid var(--border); padding:18px 12px; display:flex; flex-direction:column; gap:4px; position:sticky; top:0; height:100vh; overflow-y:auto; }
.side-logo { display:flex; align-items:center; gap:11px; padding:4px 8px 14px; }
.logo { width:40px; height:40px; border-radius:11px; background:var(--accent); display:flex; align-items:center; justify-content:center; font-weight:800; font-size:17px; color:#fff; box-shadow:0 0 18px var(--accent-glow); flex-shrink:0; }
.side-logo .t { font-weight:800; font-size:15.5px; letter-spacing:-.3px; }
.side-logo .s { color:var(--text-dim); font-size:11px; margin-top:1px; }
.side-label { color:var(--text-dim); font-size:10.5px; font-weight:700; letter-spacing:1.2px; padding:14px 10px 6px; text-transform:uppercase; }
.nav { display:flex; align-items:center; gap:10px; padding:10px 12px; border-radius:10px; color:var(--text-sub); font-size:13.5px; font-weight:600; cursor:pointer; border:1px solid transparent; transition:all .15s; user-select:none; }
.nav:hover { background:var(--surface); }
.nav.sel { background:linear-gradient(135deg,var(--accent),#7c3aed); color:#fff; box-shadow:0 0 16px var(--accent-glow); }
.nav svg { width:16px; height:16px; flex-shrink:0; }
.side-foot { margin-top:auto; padding:12px 10px 4px; color:var(--text-dim); font-size:11px; text-align:center; line-height:1.6; }
.side-foot b { color:var(--text-sub); }
.main { flex:1; padding:26px 30px 60px; max-width:1000px; }
.page { display:none; } .page.sel { display:block; }
h2 { font-size:19px; font-weight:800; letter-spacing:-.4px; margin-bottom:4px; }
.psub { color:var(--text-dim); font-size:13px; margin-bottom:18px; }
.hero { background:var(--surface); border:1px solid var(--border); border-radius:var(--radius); padding:20px; display:flex; gap:16px; align-items:center; margin-bottom:14px; }
.hero .logo { width:56px; height:56px; font-size:22px; }
.hero h1 { font-size:21px; font-weight:800; letter-spacing:-.4px; }
.hero .sub { color:var(--text-dim); font-size:12.5px; margin-top:3px; line-height:1.5; }
.stats { display:grid; grid-template-columns:repeat(auto-fit,minmax(150px,1fr)); gap:10px; margin-bottom:14px; }
.stat { background:var(--surface); border:1px solid var(--border); border-radius:12px; padding:14px 16px; }
.stat .v { font-size:21px; font-weight:800; }
.stat .k { color:var(--text-dim); font-size:11.5px; margin-top:3px; }
.stat .v.ok { color:var(--green); } .stat .v.acc { color:var(--accent); }
section.card { background:var(--surface); border:1px solid var(--border); border-radius:var(--radius); padding:18px; margin-bottom:14px; }
.sec-head { display:flex; align-items:center; justify-content:space-between; gap:10px; flex-wrap:wrap; margin-bottom:12px; }
.sec-title { font-size:15px; font-weight:700; }
.sec-title small { color:var(--text-dim); font-weight:500; margin-left:6px; }
.btn { background:var(--surface-active); color:var(--text-sub); border:1px solid var(--border); border-radius:8px; padding:7px 12px; font-size:12.5px; font-weight:600; cursor:pointer; font-family:inherit; transition:all .15s; }
.btn:hover { border-color:var(--border-focus); color:var(--text); }
.btn.primary { background:var(--accent); border-color:var(--accent); color:#fff; box-shadow:0 0 18px var(--accent-glow); }
.btn.primary:hover { filter:brightness(1.1); }
.btn.danger { color:var(--red); border-color:var(--red); }
.search { width:100%; background:var(--bg); border:1px solid var(--border); border-radius:10px; color:var(--text); padding:10px 14px; font-size:14px; font-family:inherit; outline:none; margin-bottom:14px; }
.search:focus { border-color:var(--accent); }
.plist { display:flex; flex-direction:column; gap:8px; max-height:430px; overflow-y:auto; padding-right:4px; }
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
.opt-row { display:flex; align-items:center; justify-content:space-between; gap:12px; padding:12px 0; border-bottom:1px solid var(--border); }
.opt-row:last-child { border-bottom:none; }
.opt-row .nm { font-weight:600; font-size:13.5px; }
.opt-row .ds { color:var(--text-dim); font-size:12px; margin-top:2px; line-height:1.5; }
select { background:var(--bg); color:var(--text); border:1px solid var(--border); border-radius:8px; padding:7px 10px; font-family:inherit; font-size:13px; outline:none; }
.gen-url { width:100%; background:var(--bg); border:1px solid var(--border-focus); border-radius:10px; color:var(--text); padding:12px 14px; font-size:12.5px; font-family:ui-monospace,monospace; word-break:break-all; outline:none; }
.actions { display:flex; gap:10px; margin-top:12px; flex-wrap:wrap; }
.note { color:var(--text-dim); font-size:12.5px; margin-top:12px; line-height:1.6; }
.note b { color:var(--text-sub); }
table { width:100%; border-collapse:collapse; font-size:12.5px; }
th { text-align:left; color:var(--text-dim); font-weight:600; padding:8px 10px; border-bottom:1px solid var(--border); }
td { padding:9px 10px; border-bottom:1px solid var(--border); vertical-align:top; }
tr:last-child td { border-bottom:none; }
.st-ok { color:var(--green); font-weight:600; } .st-err { color:var(--red); font-weight:600; }
.errtext { color:var(--text-dim); font-size:11.5px; max-width:340px; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.repo-card { display:flex; align-items:center; gap:14px; background:var(--bg); border:1px solid var(--border); border-radius:12px; padding:14px 16px; margin-bottom:10px; }
.repo-card .ic { width:40px; height:40px; border-radius:10px; background:var(--surface-active); display:flex; align-items:center; justify-content:center; font-size:18px; flex-shrink:0; }
.repo-card .nm { font-weight:700; font-size:14px; }
.repo-card .ds { color:var(--text-dim); font-size:12px; margin-top:2px; }
.spin { display:inline-block; width:12px; height:12px; border:2px solid var(--text-dim); border-top-color:var(--accent); border-radius:50%; animation:sp 1s linear infinite; vertical-align:-2px; }
@keyframes sp { to { transform:rotate(360deg); } }
@media (max-width:760px) {
  .layout { flex-direction:column; }
  .sidebar { width:100%; height:auto; position:static; flex-direction:row; flex-wrap:wrap; align-items:center; }
  .side-label, .side-foot { display:none; }
  .nav { padding:8px 10px; font-size:12.5px; }
  .main { padding:18px 14px 50px; }
}
</style>
</head>
<body>
<div class="layout">
  <div class="sidebar">
    <div class="side-logo">
      <div class="logo">CS</div>
      <div><div class="t">CloudStream Bridge</div><div class="s">Stremio Addon Gateway</div></div>
    </div>
    <div class="side-label">Pages</div>
    <div class="nav sel" data-p="home"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 10.5 12 3l9 7.5"/><path d="M5 9.5V21h14V9.5"/></svg> Home &amp; sources</div>
    <div class="nav" data-p="status"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 12h4l3-8 4 16 3-8h4"/></svg> Source status</div>
    <div class="nav" data-p="filters"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 6h16M7 12h10m-7 6h4"/></svg> Stream filters</div>
    <div class="side-label">Repositories</div>
    <div class="nav" data-p="repos"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="4" width="18" height="16" rx="2"/><path d="M3 9h18M9 4v5"/></svg> Installed repositories</div>
    <div class="side-label">Preferences</div>
    <div class="nav" id="themeBtn"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="4"/><path d="M12 2v3m0 14v3M2 12h3m14 0h3M4.9 4.9l2.1 2.1m10 10 2.1 2.1M19.1 4.9 17 7M7 17l-2.1 2.1"/></svg> <span id="themeLabel">Theme: Slate</span></div>
    <div class="side-foot"><b>CloudStream Bridge</b><br><span id="footVer">v1.0</span> • Stremio Addon Gateway</div>
  </div>

  <div class="main">
    <!-- HOME -->
    <div class="page sel" id="page-home">
      <div class="hero">
        <div class="logo">CS</div>
        <div class="grow">
          <h1>CloudStream Bridge</h1>
          <div class="sub">Runs CloudStream extension repos server-side and serves them as one Stremio / Nuvio stream addon. Pick your sources, copy the URL, install.</div>
        </div>
      </div>
      <div class="stats">
        <div class="stat"><div class="v acc" id="st-en">–</div><div class="k">sources enabled</div></div>
        <div class="stat"><div class="v" id="st-av">–</div><div class="k">plugins loaded</div></div>
        <div class="stat"><div class="v" id="st-repos">3</div><div class="k">repositories</div></div>
        <div class="stat"><div class="v ok" id="st-state">Ready ✓</div><div class="k">config state</div></div>
      </div>
      <section class="card">
        <div class="sec-head"><div class="sec-title">Your addon URL</div></div>
        <input class="gen-url" id="murl" readonly value="loading…">
        <div class="actions">
          <button class="btn primary" id="copy">📋 Copy manifest URL</button>
          <button class="btn" id="open">Open manifest</button>
        </div>
        <div class="note">
          <b>Nuvio:</b> Settings → Addons → + → paste the URL (or add it remotely via the VPS Updates bot → /nuvio).<br>
          <b>Stremio:</b> paste the URL in the addon search bar. Every stream request is fanned out to all enabled sources.
        </div>
      </section>
      <section class="card">
        <div class="sec-head">
          <div class="sec-title">Home &amp; sources<small>pick what this addon serves</small></div>
          <div style="display:flex;gap:8px">
            <button class="btn" id="all-on">Enable all</button>
            <button class="btn" id="all-off">Disable all</button>
          </div>
        </div>
        <input class="search" id="filter" placeholder="Filter sources…">
        <div id="repos"></div>
      </section>
    </div>

    <!-- SOURCE STATUS -->
    <div class="page" id="page-status">
      <h2>Source status</h2>
      <div class="psub">Live load state of every plugin from the installed repositories. Failed plugins are skipped at stream time.</div>
      <section class="card">
        <div class="sec-head">
          <div class="sec-title">Plugins<small id="syncLine"></small></div>
          <div style="display:flex;gap:8px">
            <button class="btn" id="resync">🔄 Resync repos</button>
          </div>
        </div>
        <div style="overflow-x:auto">
        <table>
          <thead><tr><th>Source</th><th>Repo</th><th>Version</th><th>Status</th><th>Detail</th></tr></thead>
          <tbody id="statusRows"></tbody>
        </table>
        </div>
      </section>
    </div>

    <!-- FILTERS -->
    <div class="page" id="page-filters">
      <h2>Stream filters</h2>
      <div class="psub">Applies to every config generated from this page.</div>
      <section class="card">
        <div class="opt-row">
          <div><div class="nm">Catalogs</div><div class="ds">Expose each provider's home rows as browseable catalogs (plus in-addon search)</div></div>
          <label class="switch"><input type="checkbox" id="opt-c" checked><span class="sl"></span></label>
        </div>
        <div class="opt-row">
          <div><div class="nm">Torrent / magnet links</div><div class="ds">Include magnet links from torrent providers (needs an external torrent client)</div></div>
          <label class="switch"><input type="checkbox" id="opt-m"><span class="sl"></span></label>
        </div>
        <div class="opt-row">
          <div><div class="nm">Search deadline</div><div class="ds">How long a stream request waits for slow sources before returning what it has</div></div>
          <select id="opt-d">
            <option value="15000">15 s — fastest</option>
            <option value="25000" selected>25 s — balanced</option>
            <option value="40000">40 s — max coverage</option>
          </select>
        </div>
      </section>
      <section class="card">
        <div class="note" style="margin:0">Results are cached for 6 h per title, so repeat plays answer instantly. Links are deduplicated across sources and sorted by the provider that found them.</div>
      </section>
    </div>

    <!-- REPOS -->
    <div class="page" id="page-repos">
      <h2>Installed repositories</h2>
      <div class="psub">CloudStream plugin repos this bridge syncs. New plugin versions are picked up automatically every 6 h.</div>
      <section class="card">
        <div id="repoCards"></div>
        <div class="note" style="margin:0">Want another CloudStream repo here? Add its raw <b>plugins.json</b> URL and it can be wired into the bridge.</div>
      </section>
    </div>
  </div>
</div>
<script>
let DATA = null;
let state = { p: {}, c: true, m: false };
const B64 = s => btoa(unescape(encodeURIComponent(s))).replace(/\+/g,'-').replace(/\//g,'_').replace(/=+$/,'');
const THEMES = ['slate','charcoal','navy','forest'];
try { const s = localStorage.getItem('csb_state'); if (s) state = Object.assign(state, JSON.parse(s)); } catch(e) {}
document.documentElement.dataset.baseTheme = localStorage.getItem('csb_theme') || 'slate';
document.getElementById('themeLabel').textContent = 'Theme: ' + (document.documentElement.dataset.baseTheme.charAt(0).toUpperCase() + document.documentElement.dataset.baseTheme.slice(1));
document.getElementById('themeBtn').onclick = () => {
  const cur = document.documentElement.dataset.baseTheme;
  const next = THEMES[(THEMES.indexOf(cur) + 1) % THEMES.length];
  document.documentElement.dataset.baseTheme = next;
  localStorage.setItem('csb_theme', next);
  document.getElementById('themeLabel').textContent = 'Theme: ' + next.charAt(0).toUpperCase() + next.slice(1);
};
document.querySelectorAll('.nav[data-p]').forEach(n => n.onclick = () => {
  document.querySelectorAll('.nav').forEach(x => x.classList.remove('sel'));
  n.classList.add('sel');
  document.querySelectorAll('.page').forEach(p => p.classList.remove('sel'));
  document.getElementById('page-' + n.dataset.p).classList.add('sel');
});

function esc(s){ return (s||'').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;'); }
function allPlugins(){ return (DATA&&DATA.repos||[]).flatMap(r => r.plugins); }

function render() {
  if (!DATA) return;
  const root = document.getElementById('repos');
  const q = document.getElementById('filter').value.toLowerCase();
  root.innerHTML = '';
  DATA.repos.forEach(repo => {
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
    bOn.onclick = () => { repo.plugins.forEach(p => state.p[p.internalName] = 1); save(); render(); gen(); };
    bOff.onclick = () => { repo.plugins.forEach(p => delete state.p[p.internalName]); save(); render(); gen(); };
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
      inp.onchange = () => { if (inp.checked) state.p[p.internalName] = 1; else delete state.p[p.internalName]; save(); updateStats(); gen(); };
      const sl = document.createElement('span'); sl.className = 'sl';
      sw.append(inp, sl); row.append(sw);
      list.append(row);
    });
    sec.append(list); root.append(sec);
  });
  updateStats();
  // status table
  const tb = document.getElementById('statusRows');
  tb.innerHTML = '';
  allPlugins().sort((a,b) => (a.loaded===b.loaded) ? a.internalName.localeCompare(b.internalName) : (a.loaded?1:-1)).forEach(p => {
    const tr = document.createElement('tr');
    tr.innerHTML = '<td style="font-weight:600">' + esc(p.name) + '</td>' +
      '<td style="color:var(--text-dim)">' + esc((DATA.repos.find(r => r.plugins.includes(p))||{}).name||'') + '</td>' +
      '<td style="color:var(--text-dim)">' + p.version + '</td>' +
      '<td class="' + (p.loaded?'st-ok':'st-err') + '">' + (p.loaded?'loaded':'failed') + '</td>' +
      '<td><div class="errtext" title="' + esc(p.error||'') + '">' + esc(p.error||(p.providers||[]).slice(0,2).join(', ')) + '</div></td>';
    tb.append(tr);
  });
  document.getElementById('syncLine').textContent = DATA.syncing ? 'syncing…' : ('last sync ' + new Date(DATA.lastSync).toLocaleTimeString());
  // repo cards
  const rc = document.getElementById('repoCards'); rc.innerHTML = '';
  DATA.repos.forEach(repo => {
    const loaded = repo.plugins.filter(p => p.loaded).length;
    const div = document.createElement('div'); div.className = 'repo-card';
    div.innerHTML = '<div class="ic">📦</div><div class="grow"><div class="nm">' + esc(repo.name) + '</div>' +
      '<div class="ds">' + loaded + ' / ' + repo.plugins.length + ' plugins loaded</div></div>';
    const link = document.createElement('button'); link.className = 'btn'; link.textContent = 'Open repo ↗';
    link.onclick = () => window.open('https://github.com/search?q=' + encodeURIComponent(repo.name + ' cloudstream'), '_blank');
    div.append(link); rc.append(div);
  });
}
function updateStats() {
  const all = allPlugins();
  document.getElementById('st-en').textContent = all.filter(p => state.p[p.internalName]).length;
  document.getElementById('st-av').textContent = all.filter(p => p.loaded).length + ' / ' + all.length;
}
function save(){ localStorage.setItem('csb_state', JSON.stringify(state)); }
function gen() {
  const url = location.origin + '/' + B64(JSON.stringify(state)) + '/manifest.json';
  document.getElementById('murl').value = url;
}
document.getElementById('filter').oninput = render;
document.getElementById('all-on').onclick = () => { allPlugins().forEach(p => state.p[p.internalName] = 1); save(); render(); gen(); };
document.getElementById('all-off').onclick = () => { state.p = {}; save(); render(); gen(); };
document.getElementById('opt-c').onchange = e => { state.c = e.target.checked; save(); gen(); };
document.getElementById('opt-m').onchange = e => { state.m = e.target.checked; save(); gen(); };
document.getElementById('opt-d').onchange = e => { state.d = parseInt(e.target.value); save(); gen(); };
document.getElementById('resync').onclick = () => {
  const b = document.getElementById('resync'); b.disabled = true; b.innerHTML = '<span class="spin"></span> syncing…';
  fetch('/api/resync').then(() => poll()).catch(() => { b.disabled = false; b.textContent = '🔄 Resync repos'; });
};
document.getElementById('copy').onclick = () => {
  const v = document.getElementById('murl').value;
  (navigator.clipboard ? navigator.clipboard.writeText(v) : Promise.reject()).then(() => {
    const b = document.getElementById('copy'); const t = b.textContent; b.textContent = '✅ Copied!';
    setTimeout(() => b.textContent = t, 1500);
  }).catch(() => { document.getElementById('murl').select(); document.execCommand('copy'); });
};
document.getElementById('open').onclick = () => window.open(document.getElementById('murl').value, '_blank');

async function boot() {
  document.getElementById('syncLine').innerHTML = '<span class="spin"></span> loading…';
  try {
    const r = await fetch('/api/repos'); DATA = await r.json();
    allPlugins().forEach(p => { if (!(p.internalName in state.p) && p.loaded) state.p[p.internalName] = 1; });
    document.getElementById('opt-c').checked = !!state.c;
    document.getElementById('opt-m').checked = !!state.m;
    document.getElementById('opt-d').value = String(state.d || 25000);
    render(); gen();
  } catch(e) {
    document.getElementById('syncLine').textContent = '⚠ failed to load repo data';
  }
}
function poll() {
  fetch('/api/repos').then(r => r.json()).then(d => {
    DATA = d; render(); gen();
    const b = document.getElementById('resync');
    if (d.syncing) setTimeout(poll, 5000);
    else { b.disabled = false; b.textContent = '🔄 Resync repos'; }
  }).catch(() => {});
}
boot();
setInterval(() => { fetch('/api/repos').then(r => r.json()).then(d => { DATA = d; render(); gen(); }); }, 15000);
</script>
</body>
</html>""".trimIndent()
}
