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
[data-base-theme="light"] { --bg:#f4f6fb; --surface:#ffffff; --surface-active:#e9edf5; --card-hover:#f0f3f9; --sidebar:#ffffff; --border:#d7dce6; --border-focus:#b5bccb; --text:#111827; --text-sub:#374151; --text-dim:#6b7280; }
.prov .badges { display:flex; align-items:center; gap:6px; flex-shrink:0; }
.prov .ordn { min-width:22px; height:22px; border-radius:11px; background:var(--accent); color:#fff; font-size:11px; font-weight:800; display:flex; align-items:center; justify-content:center; padding:0 6px; }
.prov .cat { font-size:10px; font-weight:800; letter-spacing:.04em; padding:3px 7px; border-radius:6px; border:1px solid var(--border); color:var(--text-dim); cursor:pointer; user-select:none; }
.prov .cat.on { color:var(--green); border-color:var(--green); }
.prof { display:flex; gap:8px; flex-wrap:wrap; align-items:center; }
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
.main { flex:1; padding:26px 30px 60px; max-width:1050px; }
.page { display:none; } .page.sel { display:block; }
h2 { font-size:19px; font-weight:800; letter-spacing:-.4px; margin-bottom:4px; }
.psub { color:var(--text-dim); font-size:13px; margin-bottom:18px; line-height:1.5; }
.hero { background:var(--surface); border:1px solid var(--border); border-radius:var(--radius); padding:20px; display:flex; gap:16px; align-items:center; margin-bottom:14px; }
.hero .logo { width:56px; height:56px; font-size:22px; }
.hero h1 { font-size:21px; font-weight:800; letter-spacing:-.4px; }
.hero .sub { color:var(--text-dim); font-size:12.5px; margin-top:3px; line-height:1.5; }
.stats { display:grid; grid-template-columns:repeat(auto-fit,minmax(150px,1fr)); gap:10px; margin-bottom:14px; }
.stat { background:var(--surface); border:1px solid var(--border); border-radius:12px; padding:14px 16px; }
.stat .v { font-size:21px; font-weight:800; }
.stat .k { color:var(--text-dim); font-size:11.5px; margin-top:3px; }
.stat .v.ok { color:var(--green); } .stat .v.acc { color:var(--accent); } .stat .v.err { color:var(--red); }
section.card { background:var(--surface); border:1px solid var(--border); border-radius:var(--radius); padding:18px; margin-bottom:14px; }
.sec-head { display:flex; align-items:center; justify-content:space-between; gap:10px; flex-wrap:wrap; margin-bottom:12px; }
.sec-title { font-size:15px; font-weight:700; }
.sec-title small { color:var(--text-dim); font-weight:500; margin-left:6px; }
.btn { background:var(--surface-active); color:var(--text-sub); border:1px solid var(--border); border-radius:8px; padding:7px 12px; font-size:12.5px; font-weight:600; cursor:pointer; font-family:inherit; transition:all .15s; }
.btn:hover { border-color:var(--border-focus); color:var(--text); }
.btn.primary { background:var(--accent); border-color:var(--accent); color:#fff; box-shadow:0 0 18px var(--accent-glow); }
.btn.primary:hover { filter:brightness(1.1); }
.btn.danger { color:var(--red); border-color:var(--red); }
.btn.small { padding:4px 9px; font-size:11.5px; }
.chipbtn { background:var(--bg); color:var(--text-sub); border:1px solid var(--border); border-radius:999px; padding:6px 13px; font-size:12.5px; font-weight:600; cursor:pointer; font-family:inherit; transition:all .15s; }
.chipbtn:hover { border-color:var(--border-focus); }
.chipbtn.sel { background:var(--accent); border-color:var(--accent); color:#fff; }
input, select { background:var(--bg); color:var(--text); border:1px solid var(--border); border-radius:8px; padding:8px 11px; font-family:inherit; font-size:13px; outline:none; }
input:focus, select:focus { border-color:var(--accent); }
.search { width:100%; border-radius:10px; padding:10px 14px; font-size:14px; margin-bottom:14px; }
.search:focus { border-color:var(--accent); }
.grid { display:grid; grid-template-columns:repeat(auto-fill,minmax(320px,1fr)); gap:10px; max-height:560px; overflow-y:auto; padding-right:4px; }
.grid::-webkit-scrollbar, .plist::-webkit-scrollbar { width:8px; }
.grid::-webkit-scrollbar-thumb, .plist::-webkit-scrollbar-thumb { background:var(--surface-active); border-radius:4px; }
.prov { display:flex; align-items:center; gap:11px; background:var(--bg); border:1px solid var(--border); border-radius:11px; padding:10px 12px; transition:all .15s; cursor:default; }
.prov:hover { background:var(--card-hover); }
.prov.on { border-color:var(--accent); box-shadow:0 0 10px var(--accent-glow); }
.prov img { width:34px; height:34px; border-radius:8px; object-fit:contain; background:var(--surface-active); flex-shrink:0; }
.prov .nm { font-weight:600; font-size:13px; }
.prov .ds { color:var(--text-dim); font-size:11.5px; margin-top:1px; display:-webkit-box; -webkit-line-clamp:2; -webkit-box-orient:vertical; overflow:hidden; line-height:1.4; }
.prov .chips { display:flex; gap:4px; margin-top:3px; flex-wrap:wrap; }
.chip { font-size:10px; padding:1px 6px; border-radius:999px; background:var(--surface-active); color:var(--text-dim); border:1px solid var(--border); }
.chip.lang { color:var(--text-sub); }
.chip.dead { color:var(--red); border-color:var(--red); }
.chip.up { color:var(--green); border-color:var(--green); }
.grow { flex:1; min-width:0; }
.switch { position:relative; width:38px; height:21px; flex-shrink:0; cursor:pointer; }
.switch input { opacity:0; width:0; height:0; }
.sl { position:absolute; inset:0; background:var(--surface-active); border-radius:999px; transition:.2s; border:1px solid var(--border); }
.sl:before { content:""; position:absolute; width:15px; height:15px; border-radius:50%; background:var(--text-dim); top:2px; left:2px; transition:.2s; }
.switch input:checked + .sl { background:var(--accent); border-color:var(--accent); }
.switch input:checked + .sl:before { transform:translateX(17px); background:#fff; }
.qchip { display:inline-flex; align-items:center; gap:7px; background:var(--bg); border:1px solid var(--border); border-radius:10px; padding:9px 13px; font-size:13px; font-weight:600; cursor:pointer; user-select:none; }
.qchip input { accent-color:var(--accent); }
.qchip.off { opacity:.45; }
.opt-row { display:flex; align-items:center; justify-content:space-between; gap:12px; padding:12px 0; border-bottom:1px solid var(--border); }
.opt-row:last-child { border-bottom:none; }
.opt-row .nm { font-weight:600; font-size:13.5px; }
.opt-row .ds { color:var(--text-dim); font-size:12px; margin-top:2px; line-height:1.5; }
.gen-url { width:100%; background:var(--bg); border:1px solid var(--border-focus); border-radius:10px; color:var(--text); padding:12px 14px; font-size:12px; font-family:ui-monospace,monospace; word-break:break-all; outline:none; }
.actions { display:flex; gap:10px; margin-top:12px; flex-wrap:wrap; }
.note { color:var(--text-dim); font-size:12.5px; margin-top:12px; line-height:1.6; }
.note b { color:var(--text-sub); }
.big { width:100%; padding:14px; font-size:15px; font-weight:700; border-radius:11px; }
table { width:100%; border-collapse:collapse; font-size:12.5px; }
th { text-align:left; color:var(--text-dim); font-weight:600; padding:8px 10px; border-bottom:1px solid var(--border); }
td { padding:9px 10px; border-bottom:1px solid var(--border); vertical-align:top; }
tr:last-child td { border-bottom:none; }
.st-ok { color:var(--green); font-weight:600; } .st-err { color:var(--red); font-weight:600; } .st-unk { color:var(--text-dim); font-weight:600; }
.errtext { color:var(--text-dim); font-size:11.5px; max-width:340px; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.repo-card { display:flex; align-items:center; gap:14px; background:var(--bg); border:1px solid var(--border); border-radius:12px; padding:14px 16px; margin-bottom:10px; }
.repo-card .ic { width:40px; height:40px; border-radius:10px; background:var(--surface-active); display:flex; align-items:center; justify-content:center; font-size:18px; flex-shrink:0; overflow:hidden; }
.repo-card .ic img { width:100%; height:100%; object-fit:contain; }
.repo-card .nm { font-weight:700; font-size:14px; }
.repo-card .ds { color:var(--text-dim); font-size:12px; margin-top:2px; }
.pill { background:var(--accent-glow); color:var(--accent); border-radius:999px; padding:3px 10px; font-size:11px; font-weight:700; flex-shrink:0; }
.order-row { display:flex; align-items:center; gap:8px; background:var(--bg); border:1px solid var(--border); border-radius:9px; padding:7px 11px; margin-bottom:6px; font-size:13px; font-weight:600; }
.arrow { background:var(--surface-active); border:1px solid var(--border); color:var(--text-sub); border-radius:6px; padding:3px 9px; cursor:pointer; font-family:inherit; }
.arrow:hover { color:var(--text); border-color:var(--border-focus); }
.preview { background:var(--bg); border:1px dashed var(--border-focus); border-radius:10px; padding:12px 14px; margin-top:12px; font-size:12.5px; line-height:1.7; }
.preview .l1 { font-weight:700; color:var(--text); white-space:pre-line; }
.preview .l2 { color:var(--text-dim); }
.chips-wrap { display:flex; flex-direction:column; gap:6px; }
.famlab { color:var(--text-dim); font-size:10.5px; font-weight:700; letter-spacing:1.2px; text-transform:uppercase; margin-top:8px; }
.chiprow { display:flex; gap:6px; flex-wrap:wrap; }
label.lbl { display:block; color:var(--text-sub); font-size:12.5px; font-weight:600; margin:14px 0 5px; }
textarea { background:var(--bg); color:var(--text); border:1px solid var(--border); border-radius:10px; padding:10px 12px; font-family:inherit; font-size:13px; outline:none; }
textarea:focus { border-color:var(--accent); }
textarea.tpl { width:100%; min-height:96px; font-family:ui-monospace,monospace; font-size:12px; line-height:1.65; resize:vertical; }
textarea.tpl.small { min-height:64px; }
.diag { color:var(--text-dim); font-size:11.5px; margin-top:4px; white-space:pre-line; }
.diag.bad { color:var(--red); }
.pvcard { background:var(--bg); border:1px solid var(--border); border-radius:10px; padding:10px 12px; margin-bottom:8px; }
.pvcard .tag { display:inline-block; background:var(--accent-glow); color:var(--accent); font-size:10px; font-weight:700; border-radius:999px; padding:2px 8px; margin-bottom:4px; }
.pvcard .nm { font-weight:700; font-size:13px; white-space:pre-line; }
.pvcard .ds { color:var(--text-dim); font-size:12px; margin-top:3px; white-space:pre-line; line-height:1.55; }
.fields { columns:2; column-gap:22px; font-size:10.5px; color:var(--text-dim); line-height:1.7; font-family:ui-monospace,monospace; margin-top:10px; }
@media (max-width:760px){ .fields { columns:1; } }
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
    <div class="nav" data-p="filters"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 6h16M7 12h10m-7 6h4"/></svg> Stream filters</div>
    <div class="nav" data-p="formatter"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 20h16M6 16 14 4m4 12-6-12"/></svg> Stream formatter</div>
    <div class="nav" data-p="status"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 12h4l3-8 4 16 3-8h4"/></svg> Source status</div>
    <div class="side-label">Repositories</div>
    <div class="nav" data-p="repos"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="4" width="18" height="16" rx="2"/><path d="M3 9h18M9 4v5"/></svg> Installed repositories</div>
    <div class="side-label">Preferences</div>
    <div class="nav" id="themeBtn"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="4"/><path d="M12 2v3m0 14v3M2 12h3m14 0h3M4.9 4.9l2.1 2.1m10 10 2.1 2.1M19.1 4.9 17 7M7 17l-2.1 2.1"/></svg> <span id="themeLabel">Theme: Slate</span></div>
    <div class="side-foot"><b>CloudStream Bridge</b><br><span id="footVer"></span> • Stremio Addon Gateway</div>
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
        <div class="stat"><div class="v" id="st-repos">–</div><div class="k">repositories</div></div>
        <div class="stat"><div class="v" id="st-q">–</div><div class="k">qualities</div></div>
        <div class="stat"><div class="v ok" id="st-state">Saved ✓</div><div class="k">auto-sync on</div></div>
      </div>
      <section class="card" id="profCard">
        <div class="sec-head"><div class="sec-title">Profiles<small>each profile is its own addon — own sources, catalogs, filters and formatter; install several side by side</small></div></div>
        <div class="prof">
          <select id="profSel" style="min-width:180px"></select>
          <button class="btn small" id="profNew">＋ New (copy of this one)</button>
          <button class="btn small" id="profRen">Rename</button>
          <button class="btn small" id="profDel">Remove</button>
        </div>
      </section>
      <section class="card">
        <div class="sec-head"><div class="sec-title">Install your personal addon</div></div>
        <input class="gen-url" id="murl" readonly value="loading…">
        <div class="actions">
          <a class="btn primary big" id="install" href="#" style="text-decoration:none;text-align:center">▶ Install in Stremio</a>
          <button class="btn primary big" id="copy">📋 Copy Addon URL</button>
          <button class="btn" id="open">Open manifest</button>
        </div>
        <div class="note">
          <b>Nuvio:</b> Settings → Addons → + → paste the URL (or add it remotely via the VPS Updates bot → /nuvio).<br>
          <b>Stremio:</b> paste the URL in the addon search bar. Every stream request fans out to all enabled sources.
        </div>
      </section>
      <section class="card">
        <div class="opt-row" style="border-bottom:none">
          <div><div class="nm">Show catalogs</div><div class="ds">Expose each provider's home rows as browseable catalogs + in-addon search. Turn off for streams-only mode (faster loading).</div></div>
          <label class="switch"><input type="checkbox" id="opt-c" checked><span class="sl"></span></label>
        </div>
      </section>
      <section class="card">
        <div class="sec-head">
          <div class="sec-title">Sources &amp; providers<small>select providers and presets configure sources instantly</small></div>
        </div>
        <div style="display:flex;gap:8px;flex-wrap:wrap;margin-bottom:10px">
          <button class="btn" id="all-on">✓ Enable all</button>
          <button class="btn" id="all-off">✕ Clear all</button>
          <button class="chipbtn" data-preset="dev">⭐ Developer's choice</button>
          <button class="chipbtn" data-preset="movies">🎬 Movies &amp; Series</button>
          <button class="chipbtn" data-preset="anime">🌸 Anime</button>
          <button class="chipbtn" data-preset="live">📺 Live TV</button>
          <button class="chipbtn" data-preset="sports">🏏 Live Sports</button>
          <button class="chipbtn" data-preset="core">⭐ Core repos</button>
        </div>
        <div style="display:flex;gap:8px;flex-wrap:wrap;margin-bottom:10px">
          <select id="f-repo"><option value="">All repositories</option></select>
          <select id="f-lang"><option value="">All languages</option></select>
          <select id="f-type"><option value="">All content types</option></select>
          <label class="qchip" style="padding:7px 12px"><input type="checkbox" id="f-dead" checked> Hide dead</label>
          <label class="qchip" style="padding:7px 12px"><input type="checkbox" id="f-failed"> Hide load-failed</label>
        </div>
        <input class="search" id="filter" placeholder="Search providers by name, repo, language…">
        <div class="grid" id="grid"></div>
      </section>
    </div>

    <!-- FILTERS -->
    <div class="page" id="page-filters">
      <h2>Stream filters</h2>
      <div class="psub">Choose which video qualities are displayed. Unchecked qualities will be filtered out, streams grouped by quality tier with per-tier caps and your preferred provider order.</div>
      <section class="card">
        <div class="sec-head"><div class="sec-title">Stream quality filters<small id="qLabel"></small></div></div>
        <div style="display:flex;gap:8px;flex-wrap:wrap">
          <label class="qchip"><input type="checkbox" id="q-2160" checked> 4K (2160p)</label>
          <label class="qchip"><input type="checkbox" id="q-1080" checked> 1080p FHD</label>
          <label class="qchip"><input type="checkbox" id="q-720" checked> 720p HD</label>
          <label class="qchip"><input type="checkbox" id="q-480" checked> 480p / SD</label>
          <label class="qchip"><input type="checkbox" id="q-360" checked> 360p</label>
        </div>
        <div class="note">Unknown-quality streams are always kept.</div>
      </section>
      <section class="card">
        <div class="sec-head"><div class="sec-title">Stream languages<small id="lgLabel"></small></div></div>
        <div class="psub" style="margin-bottom:10px">Pick your preferred languages — streams are sorted best-language first (provider order kept within a language). Filtering drops streams in other languages; unknown-language streams can be kept.</div>
        <div class="chiprow" id="lgChips" style="margin-bottom:8px"></div>
        <div id="lgSel"></div>
        <div class="opt-row" style="margin-top:8px">
          <div><div class="nm">Drop non-matching languages</div><div class="ds">Off = sort only. Keep unknown = drop other languages but keep streams with unknown audio. Strict = drop unknown too.</div></div>
          <select id="lg-f"><option value="0">Off (sort only)</option><option value="1">Keep unknown</option><option value="2">Strict</option></select>
        </div>
      </section>
      <section class="card">
        <div class="sec-head"><div class="sec-title">Filtering &amp; playback</div></div>
        <div class="opt-row">
          <div><div class="nm">Max streams per quality tier</div><div class="ds">After provider ordering, keep at most N streams per tier (4K / 1080p / …). 0 = unlimited.</div></div>
          <select id="opt-tier">
            <option value="0" selected>All streams</option>
            <option value="8">Top 8 per tier</option>
            <option value="5">Top 5 per tier</option>
            <option value="3">Top 3 per tier</option>
          </select>
        </div>
        <div class="opt-row">
          <div><div class="nm">File size limits (GB)</div><div class="ds">Hide streams smaller / larger than this. Streams whose size is unknown are never hidden. 0 = no limit.</div></div>
          <div style="display:flex;gap:6px;align-items:center"><input id="opt-smin" type="number" min="0" step="0.5" style="width:80px" placeholder="min"> – <input id="opt-smax" type="number" min="0" step="0.5" style="width:80px" placeholder="max"></div>
        </div>
        <div class="opt-row">
          <div><div class="nm">Group streams by</div><div class="ds">Default = your provider order (then language). Quality = 4K first, then 1080p, … Provider = all links of one provider together.</div></div>
          <select id="opt-grp"><option value="">Default</option><option value="quality">Quality</option><option value="provider">Provider</option></select>
        </div>
        <div class="opt-row">
          <div><div class="nm">Sort streams</div><div class="ds">Inside each group. Size = largest file first (unknown sizes last).</div></div>
          <select id="opt-sort"><option value="">Default</option><option value="size">Size (largest first)</option></select>
        </div>
        <div class="opt-row">
          <div><div class="nm">Block CAM / Screeners</div><div class="ds">Drop links detected as CAM, HDTS, TC or screener releases by name or quality.</div></div>
          <label class="switch"><input type="checkbox" id="opt-cam"><span class="sl"></span></label>
        </div>
        <div class="opt-row">
          <div><div class="nm">Torrent / magnet links</div><div class="ds">Include magnet links from torrent providers (needs an external torrent client).</div></div>
          <label class="switch"><input type="checkbox" id="opt-m"><span class="sl"></span></label>
        </div>
        <div class="opt-row">
          <div><div class="nm">Search deadline</div><div class="ds">How long a stream request waits for slow sources before returning what it has. Results are cached 6 h.</div></div>
          <select id="opt-d">
            <option value="15000">15 s — fastest</option>
            <option value="25000" selected>25 s — balanced</option>
            <option value="40000">40 s — max coverage</option>
          </select>
        </div>
      </section>
      <section class="card">
        <div class="sec-head">
          <div class="sec-title">Provider order</div>
          <button class="btn small" id="order-reset">Reset</button>
        </div>
        <div class="psub" style="margin-bottom:10px">Inside each quality tier, providers appear in this order. Only enabled providers are listed.</div>
        <div id="orderList"></div>
      </section>
    </div>

    <!-- FORMATTER -->
    <div class="page" id="page-formatter">
      <h2>Stream formatter</h2>
      <div class="psub">Choose how stream names and details look in Stremio. Templates use the <b>AIOStreams</b> custom-formatter syntax — PenguPlay uses the same engine — rendered by AIOStreams' own formatter engine. All community presets below work unchanged.</div>
      <section class="card">
        <div class="sec-head">
          <div class="sec-title">Formatter<small>pick a preset or write your own</small></div>
          <div style="display:flex;gap:8px">
            <button class="btn small" id="bImport">Import JSON</button>
            <button class="btn small" id="bExport">Copy as JSON</button>
          </div>
        </div>
        <div id="presetChips" class="chips-wrap"><span class="spin"></span> loading presets…</div>
        <div id="jsonBox" style="display:none;margin-top:10px">
          <textarea id="json" class="tpl small" placeholder='{"name": "...", "description": "..."}'></textarea>
          <div class="actions"><button class="btn primary small" id="bJsonApply">Apply JSON</button></div>
          <div class="diag bad" id="jsonErr"></div>
        </div>
        <label class="lbl" for="fmt-n">Stream name template</label>
        <textarea id="fmt-n" class="tpl" spellcheck="false" placeholder="e.g. 🎯 CSB {stream.resolution}"></textarea>
        <div class="diag" id="diag-n"></div>
        <label class="lbl" for="fmt-d">Stream description template</label>
        <textarea id="fmt-d" class="tpl" spellcheck="false" placeholder="e.g. {stream.filename}"></textarea>
        <div class="diag" id="diag-d"></div>
        <div class="note">Selecting a preset loads it into the editors — any edit turns it into a custom template. Changing the formatter generates a new addon URL; re-add the addon to switch an existing install.</div>
      </section>
      <section class="card">
        <div class="sec-head">
          <div class="sec-title">Preview<small>how Stremio lists the streams</small></div>
          <button class="btn small" id="pv-refresh">↻ Refresh</button>
        </div>
        <div id="pvCards"><div class="note">Pick a preset or write a template to see a live preview.</div></div>
      </section>
      <details class="card">
        <summary style="cursor:pointer;font-weight:700">Template syntax &amp; fields</summary>
        <div class="note" style="margin-top:10px">
          <code>{stream.resolution}</code> field · <code>{stream.size::sbytes}</code> modifier chain ·
          <code>{stream.fast["⚡"||"🐢"]}</code> conditional (true / false branches) · <code>{?📦 {stream.size}?}</code> optional group (hidden when empty) ·
          <code>{tools.newLine}</code> / <code>{tools.removeLine}</code> ·
          CloudStream Bridge extras: <code>stream.linkName</code>, <code>stream.source</code>.
          Full reference: <a style="color:var(--accent)" href="https://docs.aiostreams.viren070.me/reference/custom-formatter/" target="_blank" rel="noopener">AIOStreams custom formatter</a>.
          Note: this bridge doesn't measure link speed, so <code>stream.speed</code> / <code>stream.speedLabel</code> are always empty.
        </div>
        <div class="fields" id="fieldsList"></div>
      </details>
    </div>

    <!-- STATUS -->
    <div class="page" id="page-status">
      <h2>Source status</h2>
      <div class="psub">Live health of your sources, from this server's own checks.</div>
      <section class="card">
        <div class="sec-head">
          <div class="sec-title">All plugins<small id="syncLine"></small></div>
          <div style="display:flex;gap:8px">
            <button class="btn" id="resync">🔄 Resync repos</button>
            <button class="btn primary" id="healthbtn">▶ Run health check</button>
          </div>
        </div>
        <div class="stats">
          <div class="stat"><div class="v" id="hs-total">–</div><div class="k">Sources</div></div>
          <div class="stat"><div class="v ok" id="hs-up">–</div><div class="k">Up</div></div>
          <div class="stat"><div class="v err" id="hs-down">–</div><div class="k">Down</div></div>
          <div class="stat"><div class="v" id="hs-unk">–</div><div class="k">Unchecked</div></div>
        </div>
        <div style="display:flex;gap:8px;flex-wrap:wrap;margin-bottom:12px">
          <button class="chipbtn sel" data-hf="all">All</button>
          <button class="chipbtn" data-hf="up">Up</button>
          <button class="chipbtn" data-hf="down">Down</button>
          <button class="chipbtn" data-hf="unchecked">Unchecked</button>
        </div>
        <div style="overflow-x:auto">
        <table>
          <thead><tr><th>Source</th><th>Repo</th><th>Health</th><th>Detail</th></tr></thead>
          <tbody id="statusRows"></tbody>
        </table>
        </div>
      </section>
    </div>

    <!-- REPOS -->
    <div class="page" id="page-repos">
      <h2>Installed repositories</h2>
      <div class="psub">CloudStream plugin repos this bridge syncs. New plugin versions are picked up automatically every 6 h. All media streaming is powered by open-source CloudStream repository maintainers — please check out their original repositories and support them!</div>
      <section class="card">
        <div class="sec-head"><div class="sec-title">Repositories<small id="repoCount"></small></div></div>
        <div id="repoCards"></div>
        <div class="sec-head" style="margin-top:16px"><div class="sec-title">Add a repository</div></div>
        <div style="display:flex;gap:8px;flex-wrap:wrap">
          <input id="add-name" placeholder="Name (optional)" style="flex:1;min-width:180px">
          <input id="add-url" placeholder="repo.json link, cloudstreamrepo:// link or github.com/owner/repo" style="flex:2;min-width:260px">
          <button class="btn primary" id="add-btn">＋ Add</button>
        </div>
        <div class="note" id="addMsg"></div>
      </section>
    </div>
  </div>
</div>
<script>
let DATA = null;
// dl = search deadline; d = formatter description template. They used to share
// one key (state.d) — editing the deadline clobbered the description and the
// deadline itself never reached the generated URL
let state = { p: {}, c: true, m: false, dl: 25000, q: { on: [2160,1080,720,480,360], tier: 0, cam: 0 }, f: 'builtin', n: '', d: '', order: [], lg: [], lgf: 0, co: [], smin: 0, smax: 0, grp: '', sort: '' };
const B64 = s => btoa(unescape(encodeURIComponent(s))).replace(/\+/g,'-').replace(/\//g,'_').replace(/=+$/,'');
const UNB64 = s => decodeURIComponent(escape(atob(s.replace(/-/g,'+').replace(/_/g,'/') + '='.repeat((4 - s.length % 4) % 4))));
const THEMES = ['slate','charcoal','navy','forest','light'];
// providers that returned links most often in live tests (2026-10-08) — a
// lean default; enabling hundreds of sources loads them all into memory
const DEV_CHOICE = ['FourKHDHub','HDhub4u','MovieBoxProviderIN','CastleTvProvider','AllMovieLandProvider','CineStream','OttSource','UHDmoviesProvider','Moviesmod','Hindmoviez','CNC Verse','CNC Verse Mobile','OneTouchTV','Bollyflix','VegaMovies','DudeFilms','StreamFlixProvider','KisskhProvider'];
try { const s = localStorage.getItem('csb_state2'); if (s) state = Object.assign(state, JSON.parse(s)); } catch(e) {}
if (!Array.isArray(state.lg)) state.lg = [];
if (!Array.isArray(state.co)) state.co = [];
// ---------- install identity ----------
// The manifest URL carries a stable install id, not the config itself: the
// first save pins the config server-side (/api/installs/<id>) and from then on
// every configure-page change applies to the ALREADY INSTALLED addon — no
// re-adding. Nuvio picks manifest-visible changes up on its next manifest
// refetch; stream behavior changes immediately.
let INSTALL = null;
let FROM_URL = false;
(function pickInstall() {
  const m = location.pathname.match(/^\/([A-Za-z0-9_-]+)\/configure\/?$/);
  if (m) { INSTALL = m[1]; FROM_URL = true; return; }
  INSTALL = localStorage.getItem('csb_install');
  if (!INSTALL) { INSTALL = 'i' + Math.random().toString(36).slice(2, 12); localStorage.setItem('csb_install', INSTALL); }
})();
// migrate the pre-AIOStreams formatter keys to the preset/custom scheme
(function migrateFmt() {
  if (state.fmt !== undefined) {
    const old = String(state.fmt);
    const tok = t => String(t||'').replace('{provider}','{stream.provider}').replace('{quality}','{stream.resolution}').replace('{link}','{stream.filename}');
    if (old === 'modern') state.f = 'csb-modern';
    else if (old === 'minimal') state.f = 'csb-minimal';
    else if (old.startsWith('custom:')) {
      const parts = old.slice(7).split('|');
      state.f = 'custom';
      state.n = tok(parts[0]); state.d = tok(parts[1] || '');
    } else state.f = 'builtin';
    delete state.fmt; delete state.fmtName; delete state.fmtTitle;
  }
  if (!state.f) state.f = 'builtin';
  if (state.f === 'custom' && !state.n && !state.d) state.f = 'builtin';
  // deadline/description split: an old numeric state.d was the deadline
  if (typeof state.d === 'number' || (typeof state.d === 'string' && /^\d+$/.test(state.d))) { state.dl = parseInt(state.d); state.d = ''; }
  if (!state.dl) state.dl = 25000;
})();
document.documentElement.dataset.baseTheme = localStorage.getItem('csb_theme') || 'slate';
document.getElementById('themeLabel').textContent = 'Theme: ' + document.documentElement.dataset.baseTheme.charAt(0).toUpperCase() + document.documentElement.dataset.baseTheme.slice(1);
document.getElementById('themeBtn').onclick = () => {
  const next = THEMES[(THEMES.indexOf(document.documentElement.dataset.baseTheme) + 1) % THEMES.length];
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
function repoOf(p){ return ((DATA&&DATA.repos)||[]).find(r => r.plugins.indexOf(p) >= 0); }

// ---------- state <-> UI ----------
function save(){ localStorage.setItem('csb_state2', JSON.stringify(state)); schedulePush(); }
function importCfgObject(cfg) {
  if (!cfg || typeof cfg !== 'object' || !cfg.p || typeof cfg.p !== 'object') return false;
  const p = {};
  Object.keys(cfg.p).forEach(k => { if (cfg.p[k]) p[k] = 1; });
  state.p = p;
  if (cfg.c !== undefined) state.c = cfg.c == 1;
  if (cfg.m !== undefined) state.m = cfg.m == 1;
  if (typeof cfg.d === 'number') state.dl = cfg.d;
  if (cfg.q) {
    if (Array.isArray(cfg.q.on) && cfg.q.on.length) state.q.on = cfg.q.on.map(Number).filter(n => n > 0);
    if (cfg.q.tier !== undefined) state.q.tier = Number(cfg.q.tier) || 0;
    if (cfg.q.cam !== undefined) state.q.cam = cfg.q.cam == 1 ? 1 : 0;
  }
  if (Array.isArray(cfg.order)) state.order = cfg.order.filter(x => typeof x === 'string');
  if (Array.isArray(cfg.lg)) state.lg = cfg.lg.filter(x => typeof x === 'string');
  if (cfg.lgf !== undefined) state.lgf = Number(cfg.lgf) || 0;
  state.co = Array.isArray(cfg.co) ? cfg.co.filter(x => typeof x === 'string') : [];
  state.smin = Number(cfg.smin) || 0; state.smax = Number(cfg.smax) || 0;
  state.grp = typeof cfg.grp === 'string' ? cfg.grp : ''; state.sort = typeof cfg.sort === 'string' ? cfg.sort : '';
  if (typeof cfg.fmt === 'string') {
    if (cfg.fmt === 'modern') state.f = 'csb-modern';
    else if (cfg.fmt === 'minimal') state.f = 'csb-minimal';
    else if (cfg.fmt.startsWith('custom:')) { const parts = cfg.fmt.slice(7).split('|'); state.f = 'custom'; state.n = parts[0] || ''; state.d = parts[1] || ''; }
  } else if (cfg.fmt && typeof cfg.fmt === 'object') {
    if (cfg.fmt.f) state.f = String(cfg.fmt.f);
    if (typeof cfg.fmt.n === 'string') state.n = cfg.fmt.n;
    if (typeof cfg.fmt.d === 'string') state.d = cfg.fmt.d;
  }
  return true;
}
function buildCfg() {
  const cfg = Object.assign({}, state);
  delete cfg.f; delete cfg.n; delete cfg.d; delete cfg.dl;
  delete cfg.fmt; delete cfg.fmtName; delete cfg.fmtTitle;
  if (state.f === 'custom') cfg.fmt = { f: 'custom', n: state.n || '', d: state.d || '' };
  else if (state.f && state.f !== 'builtin') cfg.fmt = { f: state.f };
  if (state.dl) cfg.d = state.dl;
  if (!Array.isArray(cfg.lg) || !cfg.lg.length) delete cfg.lg;
  if (!cfg.lgf) delete cfg.lgf;
  if (!Array.isArray(cfg.co) || !cfg.co.length) delete cfg.co;
  if (!cfg.smin) delete cfg.smin;
  if (!cfg.smax) delete cfg.smax;
  if (!cfg.grp) delete cfg.grp;
  if (!cfg.sort) delete cfg.sort;
  return cfg;
}
// every change lands on the backend so the installed manifest URL never changes
let pushTimer = null;
function setSync(t, ok){ const el = document.getElementById('st-state'); if (el) { el.textContent = t; el.className = 'v ' + (ok ? 'ok' : 'err'); } }
function schedulePush(){ clearTimeout(pushTimer); setSync('Saving…', true); pushTimer = setTimeout(pushInstall, 600); }
function pushInstall(){
  clearTimeout(pushTimer);
  try {
    return fetch('/api/installs/' + INSTALL, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(buildCfg()) })
      .then(r => r.json()).then(d => { setSync(d && d.ok ? 'Saved ✓' : 'Not saved', !!(d && d.ok)); return d; })
      .catch(() => setSync('Offline', false));
  } catch(e) { return Promise.resolve(); }
}
function gen() {
  document.getElementById('murl').value = location.origin + '/' + INSTALL + '/manifest.json';
  const ins = document.getElementById('install');
  if (ins) ins.href = 'stremio://' + location.host + '/' + INSTALL + '/manifest.json';
  const q = state.q.on.slice().sort((a,b) => b-a);
  const lab = t => t >= 2160 ? '4K' : t + 'p';
  const sq = document.getElementById('st-q'); if (sq) sq.textContent = q.length ? (q.length === 1 ? lab(q[0]) : lab(q[0]) + ' → ' + lab(q[q.length-1])) : 'none';
}

// ---------- HOME ----------
function filterState() {
  return {
    q: document.getElementById('filter').value.toLowerCase(),
    repo: document.getElementById('f-repo').value,
    lang: document.getElementById('f-lang').value,
    type: document.getElementById('f-type').value,
    hideDead: document.getElementById('f-dead').checked,
    hideFailed: document.getElementById('f-failed').checked,
  };
}
function isVisible(p, fs) {
  if (fs.q && !(p.name+' '+p.internalName+' '+(p.description||'')).toLowerCase().includes(fs.q)) return false;
  if (fs.repo && (!repoOf(p) || repoOf(p).name !== fs.repo)) return false;
  if (fs.lang && (p.language||'') !== fs.lang) return false;
  if (fs.type && (p.tvTypes||[]).indexOf(fs.type) < 0) return false;
  if (fs.hideDead && p.loaded && p.health === 'down') return false;
  if (fs.hideFailed && !p.loaded) return false;
  return true;
}
function visiblePlugins(){ const fs = filterState(); return allPlugins().filter(p => isVisible(p, fs)); }
function renderGrid() {
  const root = document.getElementById('grid');
  const fs = filterState();
  root.innerHTML = '';
  visiblePlugins().forEach(p => {
    const row = document.createElement('div');
    row.className = 'prov' + (state.p[p.internalName] ? ' on' : '');
    const chips = (p.tvTypes||[]).slice(0,3).map(t => '<span class="chip">'+esc(t)+'</span>').join('')
      + (p.language ? '<span class="chip lang">'+esc(p.language)+'</span>' : '')
      + (p.loaded ? (p.health === 'up' ? '<span class="chip up">up</span>' : (p.health === 'down' ? '<span class="chip dead">down</span>' : '')) : '<span class="chip dead">load failed</span>');
    const on = !!state.p[p.internalName];
    const ordIdx = on ? state.order.indexOf(p.name) : -1;
    const catOn = state.c && state.co.indexOf(p.internalName) < 0;
    row.innerHTML =
      '<img src="' + esc(p.iconUrl||'/logo.svg') + '" onerror="this.src=\'/logo.svg\'">' +
      '<div class="grow"><div class="nm">' + esc(p.name) + ' <span style="color:var(--text-dim);font-weight:500">v'+p.version+'</span></div>' +
      '<div class="ds">' + esc(p.description||'') + '</div><div class="chips">' + chips + '</div></div>' +
      (on ? '<div class="badges">' + (state.c ? '<span class="cat' + (catOn ? ' on' : '') + '" title="Show this source\'s catalogs in Stremio">CAT ' + (catOn ? 'ON' : 'OFF') + '</span>' : '') +
        (ordIdx >= 0 ? '<span class="ordn" title="Position in your provider order">' + (ordIdx+1) + '</span>' : '') + '</div>' : '');
    row.onclick = () => { if (state.p[p.internalName]) delete state.p[p.internalName]; else state.p[p.internalName] = 1; save(); renderAll(); };
    const catEl = row.querySelector('.cat');
    if (catEl) catEl.onclick = e => {
      e.stopPropagation();
      const i = state.co.indexOf(p.internalName);
      if (i >= 0) state.co.splice(i, 1); else state.co.push(p.internalName);
      save(); renderGrid();
    };
    root.append(row);
  });
  updateStats();
}
function updateStats() {
  const all = allPlugins();
  document.getElementById('st-en').textContent = all.filter(p => state.p[p.internalName]).length;
  document.getElementById('st-av').textContent = all.filter(p => p.loaded).length + ' / ' + all.length;
  document.getElementById('st-repos').textContent = (DATA&&DATA.repos||[]).length;
}
// HTML5 drag-and-drop reorder for .order-row children (arrows stay as the
// touch/TV fallback)
function enableDrag(container, onReorder) {
  let dragIdx = null;
  Array.from(container.children).forEach(row => {
    if (!row.classList || !row.classList.contains('order-row')) return;
    row.draggable = true;
    row.addEventListener('dragstart', e => {
      dragIdx = +row.dataset.drag; row.style.opacity = '.4';
      e.dataTransfer.effectAllowed = 'move';
      try { e.dataTransfer.setData('text/plain', row.dataset.drag); } catch(err) {}
    });
    row.addEventListener('dragend', () => { dragIdx = null; row.style.opacity = ''; });
    row.addEventListener('dragover', e => { e.preventDefault(); e.dataTransfer.dropEffect = 'move'; });
    row.addEventListener('drop', e => {
      e.preventDefault();
      const to = +row.dataset.drag;
      if (dragIdx === null || isNaN(dragIdx) || dragIdx === to) return;
      onReorder(dragIdx, to); dragIdx = null;
    });
  });
}
function renderFiltersPage() {
  const list = document.getElementById('orderList');
  list.innerHTML = '';
  const enabled = allPlugins().filter(p => state.p[p.internalName] && p.loaded);
  const ordered = state.order.map(n => enabled.find(p => p.name === n)).filter(Boolean);
  const rest = enabled.filter(p => state.order.indexOf(p.name) < 0).sort((a,b) => a.name.localeCompare(b.name));
  const full = ordered.concat(rest);
  state.order = full.map(p => p.name);
  full.forEach((p, i) => {
    const row = document.createElement('div'); row.className = 'order-row'; row.dataset.drag = i;
    row.innerHTML = '<div class="grow">' + (i+1) + '. ' + esc(p.name) + '</div>';
    const up = document.createElement('button'); up.className='arrow'; up.textContent='↑';
    const dn = document.createElement('button'); dn.className='arrow'; dn.textContent='↓';
    up.onclick = () => { if (i > 0) { const a = state.order; const t = a[i-1]; a[i-1] = a[i]; a[i] = t; save(); renderFiltersPage(); } };
    dn.onclick = () => { const a = state.order; if (i < a.length-1) { const t = a[i+1]; a[i+1] = a[i]; a[i] = t; save(); renderFiltersPage(); } };
    row.append(up, dn); list.append(row);
  });
  enableDrag(list, (from, to) => {
    const a = state.order.slice(); const m = a.splice(from,1)[0]; a.splice(to,0,m);
    state.order = a; save(); renderFiltersPage();
  });
  // quality chips state
  [2160,1080,720,480,360].forEach(t => { const el = document.getElementById('q-'+t); el.checked = state.q.on.indexOf(t) >= 0; });
  document.getElementById('qLabel').textContent = state.q.on.length === 5 ? 'All qualities' : state.q.on.length + ' of 5 tiers';
  document.getElementById('opt-tier').value = String(state.q.tier || 0);
  document.getElementById('opt-cam').checked = !!state.q.cam;
  document.getElementById('opt-m').checked = !!state.m;
  document.getElementById('opt-d').value = String(state.dl || 25000);
  document.getElementById('opt-smin').value = state.smin ? String(state.smin) : '';
  document.getElementById('opt-smax').value = state.smax ? String(state.smax) : '';
  document.getElementById('opt-grp').value = state.grp || '';
  document.getElementById('opt-sort').value = state.sort || '';
}
// ---------- languages (filter + priority sort) ----------
const LG_LIST = ['Hindi','English','Tamil','Telugu','Malayalam','Kannada','Bengali','Punjabi','Marathi','Japanese','Chinese','Korean','Spanish','Arabic','Turkish','French','German','Russian','Portuguese','Italian'];
function renderLanguagesPage() {
  const chips = document.getElementById('lgChips');
  if (!chips.children.length) {
    LG_LIST.forEach(l => {
      const b = document.createElement('button'); b.className = 'chipbtn'; b.textContent = l; b.dataset.lg = l;
      b.onclick = () => {
        const i = state.lg.indexOf(l);
        if (i >= 0) state.lg.splice(i, 1); else state.lg.push(l);
        save(); renderLanguagesPage();
      };
      chips.append(b);
    });
  }
  chips.querySelectorAll('[data-lg]').forEach(b => b.classList.toggle('sel', state.lg.indexOf(b.dataset.lg) >= 0));
  const sel = document.getElementById('lgSel');
  sel.innerHTML = '';
  if (state.lg.length) {
    const lab = document.createElement('div'); lab.className = 'famlab'; lab.textContent = 'Priority — drag or use arrows, best language first'; sel.append(lab);
    state.lg.forEach((l, i) => {
      const row = document.createElement('div'); row.className = 'order-row'; row.dataset.drag = i;
      row.innerHTML = '<div class="grow">' + (i+1) + '. ' + esc(l) + '</div>';
      const up = document.createElement('button'); up.className='arrow'; up.textContent='↑';
      const dn = document.createElement('button'); dn.className='arrow'; dn.textContent='↓';
      up.onclick = () => { if (i > 0) { const t = state.lg[i-1]; state.lg[i-1] = state.lg[i]; state.lg[i] = t; save(); renderLanguagesPage(); } };
      dn.onclick = () => { if (i < state.lg.length-1) { const t = state.lg[i+1]; state.lg[i+1] = state.lg[i]; state.lg[i] = t; save(); renderLanguagesPage(); } };
      row.append(up, dn); sel.append(row);
    });
    enableDrag(sel, (from, to) => {
      const m = state.lg.splice(from,1)[0]; state.lg.splice(to,0,m);
      save(); renderLanguagesPage();
    });
  }
  document.getElementById('lg-f').value = String(state.lgf || 0);
  document.getElementById('lgLabel').textContent = state.lg.length
    ? (state.lg[0] + ' first' + (state.lgf ? (state.lgf === 2 ? ' · strict filter' : ' · filtering, unknown kept') : ''))
    : 'none picked (no language sort/filter)';
}
// ---------- formatter (AIOStreams engine, presets + custom + preview) ----------
let PRESETS = [];
function presetById(id){ return PRESETS.find(p => p.id === id); }
async function loadFormatter() {
  try {
    const r = await fetch('/api/formatter/presets'); const d = await r.json();
    PRESETS = d.presets || [];
    renderPresetChips();
    const fl = document.getElementById('fieldsList');
    fl.innerHTML = Object.entries(d.fields || {}).map(([sec, props]) => props.map(p => sec + '.' + p).join('<br>')).join('<br>');
  } catch(e) {
    document.getElementById('presetChips').innerHTML = '<div class="diag bad">could not load presets</div>';
  }
  fmtApplySelection();
}
function renderPresetChips() {
  const wrap = document.getElementById('presetChips');
  let html = '<div class="famlab">Built-in</div><div class="chiprow">'
    + '<button class="chipbtn" data-f="builtin">Original names (no formatter)</button>'
    + '<button class="chipbtn" data-f="custom">✏️ Custom template</button></div>';
  for (const fam of ['CloudStream Bridge', 'AIOStreams', 'PenguPlay']) {
    const ps = PRESETS.filter(p => p.family === fam);
    if (!ps.length) continue;
    html += '<div class="famlab">' + esc(fam) + '</div><div class="chiprow">'
      + ps.map(p => '<button class="chipbtn" data-f="' + esc(p.id) + '" title="' + esc((p.name||'').slice(0, 120)) + '">' + esc(p.label) + '</button>').join('')
      + '</div>';
  }
  wrap.innerHTML = html;
  wrap.querySelectorAll('[data-f]').forEach(b => b.onclick = () => chooseFmt(b.dataset.f));
  markFmtChips();
}
function markFmtChips() {
  document.querySelectorAll('#presetChips [data-f]').forEach(b => b.classList.toggle('sel', b.dataset.f === state.f));
}
function chooseFmt(f) {
  state.f = f;
  if (f === 'custom') {
    // keep whatever is in the editors
  } else if (f !== 'builtin') {
    const p = presetById(f);
    if (p) { document.getElementById('fmt-n').value = p.name || ''; document.getElementById('fmt-d').value = p.description || ''; }
  }
  save(); markFmtChips(); gen(); schedulePreview();
}
function fmtApplySelection() {
  if (state.f === 'custom') {
    document.getElementById('fmt-n').value = state.n || '';
    document.getElementById('fmt-d').value = state.d || '';
  } else if (state.f && state.f !== 'builtin') {
    const p = presetById(state.f);
    if (p) { document.getElementById('fmt-n').value = p.name || ''; document.getElementById('fmt-d').value = p.description || ''; }
    else state.f = 'builtin';
  }
  markFmtChips(); gen(); schedulePreview();
}
function onEditFmt() {
  if (state.f !== 'custom') { state.f = 'custom'; markFmtChips(); }
  state.n = document.getElementById('fmt-n').value;
  state.d = document.getElementById('fmt-d').value;
  save(); gen(); schedulePreview();
}
let pvTimer = null;
function schedulePreview(){ clearTimeout(pvTimer); pvTimer = setTimeout(refreshPreview, 350); }
async function refreshPreview() {
  const n = document.getElementById('fmt-n').value, d = document.getElementById('fmt-d').value;
  const box = document.getElementById('pvCards');
  if (!n.trim() && !d.trim()) {
    if (state.f === 'builtin') box.innerHTML = '<div class="note">Built-in naming selected — streams show their original "CSB <quality>" names. Pick a preset to format them.</div>';
    else box.innerHTML = '<div class="note">Nothing to preview — pick a preset or write a template.</div>';
    return;
  }
  box.innerHTML = '<div class="note"><span class="spin"></span> rendering…</div>';
  try {
    const r = await fetch('/api/formatter/preview', { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ name: n, description: d }) });
    const d2 = await r.json();
    if (!d2.ok) { box.innerHTML = '<div class="diag bad">' + esc(d2.error || 'preview failed') + '</div>'; }
    else box.innerHTML = d2.samples.map(s =>
      '<div class="pvcard"><span class="tag">' + esc(s.label) + '</span><div class="nm">' + esc(s.name) + '</div><div class="ds">' + esc(s.description) + '</div></div>').join('');
    const fmtDiag = list => (list || []).map(x => '⚠ ' + (x.message || JSON.stringify(x))).join('\n');
    document.getElementById('diag-n').textContent = d2.diagnostics ? fmtDiag(d2.diagnostics.name) : '';
    document.getElementById('diag-d').textContent = d2.diagnostics ? fmtDiag(d2.diagnostics.description) : '';
  } catch(e) { box.innerHTML = '<div class="diag bad">preview failed: ' + esc(e.message) + '</div>'; }
}
document.getElementById('fmt-n').addEventListener('input', onEditFmt);
document.getElementById('fmt-d').addEventListener('input', onEditFmt);
document.getElementById('pv-refresh').onclick = refreshPreview;
document.getElementById('bExport').onclick = () => {
  navigator.clipboard.writeText(JSON.stringify({ name: document.getElementById('fmt-n').value, description: document.getElementById('fmt-d').value }, null, 2));
  document.getElementById('bExport').textContent = 'Copied!';
  setTimeout(() => document.getElementById('bExport').textContent = 'Copy as JSON', 1500);
};
document.getElementById('bImport').onclick = () => { const on = document.getElementById('jsonBox').style.display === 'none'; document.getElementById('jsonBox').style.display = on ? 'block' : 'none'; };
document.getElementById('bJsonApply').onclick = () => {
  try {
    const j = JSON.parse(document.getElementById('json').value);
    const n = typeof j.name === 'string' ? j.name : ''; const d = typeof j.description === 'string' ? j.description : '';
    if (!n.trim() && !d.trim()) throw new Error('no "name" or "description" template in that JSON');
    document.getElementById('fmt-n').value = n; document.getElementById('fmt-d').value = d;
    state.f = 'custom'; state.n = n; state.d = d;
    document.getElementById('jsonErr').textContent = '';
    save(); markFmtChips(); gen(); schedulePreview();
  } catch(e) { document.getElementById('jsonErr').textContent = 'Could not import: ' + e.message; }
};
let healthFilter = 'all';
document.querySelectorAll('[data-hf]').forEach(b => b.onclick = () => {
  healthFilter = b.dataset.hf;
  document.querySelectorAll('[data-hf]').forEach(x => x.classList.toggle('sel', x === b));
  renderStatus();
});
function renderStatus() {
  const all = allPlugins();
  document.getElementById('hs-total').textContent = all.length;
  document.getElementById('hs-up').textContent = all.filter(p => p.loaded && p.health === 'up').length;
  document.getElementById('hs-down').textContent = all.filter(p => p.loaded && p.health === 'down').length;
  document.getElementById('hs-unk').textContent = all.filter(p => !p.loaded || (p.health !== 'up' && p.health !== 'down')).length;
  document.getElementById('syncLine').textContent = DATA.syncing ? 'syncing…' : (DATA.healthRunning ? 'health check running…' : ('last sync ' + new Date(DATA.lastSync).toLocaleTimeString()));
  document.getElementById('statusRows').innerHTML = '';
  allPlugins().sort((a,b) => (a.loaded===b.loaded) ? a.internalName.localeCompare(b.internalName) : (a.loaded?1:-1)).forEach(p => {
    const h = p.health || 'unchecked';
    // load-failed plugins count as unchecked (they never got a health verdict)
    const match = healthFilter === 'all'
      || (healthFilter === 'up' && p.loaded && h === 'up')
      || (healthFilter === 'down' && p.loaded && h === 'down')
      || (healthFilter === 'unchecked' && (!p.loaded || (h !== 'up' && h !== 'down')));
    if (!match) return;
    const tr = document.createElement('tr');
    const cls = h === 'up' ? 'st-ok' : (h === 'down' ? 'st-err' : 'st-unk');
    const label = !p.loaded ? 'load failed' : (h === 'up' ? 'UP' : (h === 'down' ? 'DOWN' : 'unchecked'));
    const ago = p.lastOk ? ' · worked ' + agoStr(p.lastOk) : '';
    tr.innerHTML = '<td style="font-weight:600">' + esc(p.name) + '</td>' +
      '<td style="color:var(--text-dim)">' + esc((repoOf(p)||{}).name||'') + '</td>' +
      '<td class="' + cls + '">' + label + ago + '</td>' +
      '<td><div class="errtext" title="' + esc(p.error||'') + '">' + esc(p.error||(p.providers||[]).slice(0,2).join(', ')) + '</div></td>';
    document.getElementById('statusRows').append(tr);
  });
  document.getElementById('healthbtn').disabled = !!DATA.healthRunning;
  document.getElementById('healthbtn').innerHTML = DATA.healthRunning ? '<span class="spin"></span> checking…' : '▶ Run health check';
}
function agoStr(ts) {
  const m = Math.round((Date.now() - ts) / 60000);
  if (m < 1) return 'just now';
  if (m < 60) return m + 'm ago';
  const h = Math.round(m / 60); if (h < 24) return h + 'h ago';
  return Math.round(h/24) + 'd ago';
}
function renderRepos() {
  const rc = document.getElementById('repoCards'); rc.innerHTML = '';
  (DATA.repos||[]).forEach(repo => {
    const loaded = repo.plugins.filter(p => p.loaded).length;
    const div = document.createElement('div'); div.className = 'repo-card';
    const icon = repo.url.indexOf('CNCVerse') >= 0 ? 'https://raw.githubusercontent.com/NivinCNC/CNCVerse-Cloud-Stream-Extension/refs/heads/builds/cnc.png' : '/logo.svg';
    div.innerHTML = '<div class="ic"><img src="' + esc(icon) + '" onerror="this.src=\'/logo.svg\'"></div>' +
      '<div class="grow"><div class="nm">' + esc(repo.name) + '</div><div class="ds">' + esc(repo.description||'') + '</div></div>' +
      '<span class="pill">' + loaded + ' / ' + repo.plugins.length + ' sources</span>';
    const gh = document.createElement('button'); gh.className = 'btn small'; gh.textContent = 'Open ↗';
    gh.onclick = () => window.open(repo.url.replace('/raw.githubusercontent.com/','/github.com/').split('/refs/')[0].replace(/\/builds\/.*$/,'').replace(/\/main\/.*$/,'').replace(/\/master\/.*$/,''), '_blank');
    const rm = document.createElement('button'); rm.className = 'btn small danger'; rm.textContent = 'Remove';
    rm.onclick = () => {
      if (!confirm('Remove repo ' + repo.name + ' and stop syncing its plugins?')) return;
      fetch('/api/repos/remove?url=' + encodeURIComponent(repo.url)).then(r => r.json()).then(d => { document.getElementById('addMsg').textContent = d.message; poll(); });
    };
    div.append(gh, rm); rc.append(div);
  });
  document.getElementById('repoCount').textContent = (DATA.repos||[]).length + ' repos';
}
function renderAll() { renderGrid(); renderFiltersPage(); renderLanguagesPage(); renderStatus(); renderRepos(); gen(); }

// ---------- wiring ----------
document.getElementById('filter').oninput = renderGrid;
document.getElementById('f-repo').onchange = renderGrid;
document.getElementById('f-lang').onchange = renderGrid;
document.getElementById('f-dead').onchange = renderGrid;
document.getElementById('f-failed').onchange = renderGrid;
// Enable all / presets only enable what passes the active filters (Hide dead,
// Hide load-failed, repo/language/search) — enabling everything including the
// dead ones made every stream request fan out to 420+ providers
document.getElementById('all-on').onclick = () => {
  const vis = visiblePlugins();
  // every enabled source is loaded into server memory on first use
  if (vis.length > 120 && !confirm('Enable all ' + vis.length + ' visible sources? Every enabled source runs on every stream request and uses server memory. Narrow the list with the filters first for a faster addon.')) return;
  vis.forEach(p => state.p[p.internalName] = 1); save(); renderAll();
};
document.getElementById('all-off').onclick = () => { state.p = {}; save(); renderAll(); };
document.querySelectorAll('[data-preset]').forEach(b => b.onclick = () => {
  const preset = b.dataset.preset;
  state.p = {};
  visiblePlugins().forEach(p => {
    const t = (p.tvTypes||[]).join(',');
    if (preset === 'movies' && /Movie|TvSeries|AsianDrama|Documentary/.test(t)) state.p[p.internalName] = 1;
    if (preset === 'anime' && /Anime|Cartoon|OVA/.test(t)) state.p[p.internalName] = 1;
    if (preset === 'live' && /Live/.test(t)) state.p[p.internalName] = 1;
    if (preset === 'core' && /CNC Repo|Phisher|Megix|raghav/.test((repoOf(p)||{}).name||'')) state.p[p.internalName] = 1;
    if (preset === 'dev' && DEV_CHOICE.indexOf(p.internalName) >= 0 && p.loaded) state.p[p.internalName] = 1;
    if (preset === 'sports' && /Live/.test(t) && /sport|cric|football|fifa|ipl|fancode|nba|f1|race|replay|match|live events/i.test(p.name + ' ' + (p.description||''))) state.p[p.internalName] = 1;
  });
  save(); renderAll();
});
[2160,1080,720,480,360].forEach(t => { document.getElementById('q-'+t).onchange = e => {
  const on = new Set(state.q.on);
  if (e.target.checked) on.add(t); else on.delete(t);
  state.q.on = Array.from(on); save(); renderFiltersPage(); gen();
}; });
document.getElementById('opt-tier').onchange = e => { state.q.tier = parseInt(e.target.value); save(); gen(); };
document.getElementById('opt-smin').onchange = e => { state.smin = Math.max(0, parseFloat(e.target.value) || 0); save(); };
document.getElementById('opt-smax').onchange = e => { state.smax = Math.max(0, parseFloat(e.target.value) || 0); save(); };
document.getElementById('opt-grp').onchange = e => { state.grp = e.target.value; save(); };
document.getElementById('opt-sort').onchange = e => { state.sort = e.target.value; save(); };
document.getElementById('f-type').onchange = () => renderGrid();
document.getElementById('opt-cam').onchange = e => { state.q.cam = e.target.checked ? 1 : 0; save(); gen(); };
document.getElementById('opt-c').onchange = e => { state.c = e.target.checked; save(); gen(); };
document.getElementById('opt-m').onchange = e => { state.m = e.target.checked; save(); gen(); };
document.getElementById('opt-d').onchange = e => { state.dl = parseInt(e.target.value); save(); gen(); };
document.getElementById('lg-f').onchange = e => { state.lgf = parseInt(e.target.value); save(); gen(); };
document.getElementById('order-reset').onclick = () => { state.order = []; save(); renderFiltersPage(); gen(); };
document.getElementById('resync').onclick = () => {
  const b = document.getElementById('resync'); b.disabled = true; b.innerHTML = '<span class="spin"></span> syncing…';
  fetch('/api/resync').then(() => poll()).catch(() => { b.disabled = false; b.textContent = '🔄 Resync repos'; });
};
document.getElementById('healthbtn').onclick = () => {
  fetch('/api/health').then(() => poll()).catch(() => {});
};
document.getElementById('add-btn').onclick = () => {
  const url = document.getElementById('add-url').value.trim();
  if (!url) return;
  document.getElementById('addMsg').textContent = 'Looking up repository…';
  fetch('/api/repos/add?url=' + encodeURIComponent(url) + '&name=' + encodeURIComponent(document.getElementById('add-name').value.trim()))
    .then(r => r.json()).then(d => { document.getElementById('addMsg').textContent = d.message; if (d.ok) { REIMPORT_AFTER_SYNC = true; document.getElementById('add-url').value = ''; document.getElementById('add-name').value = ''; poll(); } });
};
document.getElementById('copy').onclick = () => {
  pushInstall();  // make sure the backend has the config before they paste it
  const v = document.getElementById('murl').value;
  (navigator.clipboard ? navigator.clipboard.writeText(v) : Promise.reject()).then(() => {
    const b = document.getElementById('copy'); const t = b.textContent; b.textContent = '✅ Copied!';
    setTimeout(() => b.textContent = t, 1500);
  }).catch(() => { document.getElementById('murl').select(); document.execCommand('copy'); });
};
document.getElementById('open').onclick = () => window.open(document.getElementById('murl').value, '_blank');

// ---------- profiles (several installs from one browser) ----------
function profiles() {
  let l = []; try { l = JSON.parse(localStorage.getItem('csb_profiles') || '[]'); } catch(e) {}
  if (!Array.isArray(l)) l = [];
  if (!FROM_URL && !l.some(x => x.id === INSTALL)) { l.unshift({ id: INSTALL, name: 'Main' }); localStorage.setItem('csb_profiles', JSON.stringify(l)); }
  return l;
}
function saveProfiles(l){ localStorage.setItem('csb_profiles', JSON.stringify(l)); }
function switchProfile(id){ localStorage.setItem('csb_install', id); localStorage.removeItem('csb_state2'); location.reload(); }
function renderProfiles() {
  const card = document.getElementById('profCard');
  // opened from an installed addon's Configure button: that one install only
  if (FROM_URL) { card.style.display = 'none'; return; }
  const sel = document.getElementById('profSel'); sel.innerHTML = '';
  profiles().forEach(pr => { const o = document.createElement('option'); o.value = pr.id; o.textContent = pr.name; if (pr.id === INSTALL) o.selected = true; sel.append(o); });
  sel.onchange = () => switchProfile(sel.value);
  document.getElementById('profNew').onclick = async () => {
    const name = (prompt('Name for the new profile:', 'Profile ' + (profiles().length + 1)) || '').trim();
    if (!name) return;
    const id = 'i' + Math.random().toString(36).slice(2, 12);
    // new profile starts as a copy of this one (its own addon URL)
    await fetch('/api/installs/' + id, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(buildCfg()) }).catch(() => {});
    const l = profiles(); l.push({ id, name }); saveProfiles(l); switchProfile(id);
  };
  document.getElementById('profRen').onclick = () => {
    const l = profiles(); const cur = l.find(x => x.id === INSTALL); if (!cur) return;
    const name = (prompt('Rename profile:', cur.name) || '').trim(); if (!name) return;
    cur.name = name; saveProfiles(l); renderProfiles();
  };
  document.getElementById('profDel').onclick = () => {
    const l = profiles(); if (l.length <= 1) { alert('This is your only profile.'); return; }
    const cur = l.find(x => x.id === INSTALL);
    if (!confirm('Remove profile "' + (cur ? cur.name : INSTALL) + '" from this page? An addon already installed with its URL keeps working.')) return;
    const rest = l.filter(x => x.id !== INSTALL); saveProfiles(rest); switchProfile(rest[0].id);
  };
}
async function boot() {
  renderProfiles();
  document.getElementById('syncLine').innerHTML = '<span class="spin"></span> loading…';
  {
    // the server record is the source of truth for every install (configure
    // button or a profile picked on this page); a legacy URL whose config is
    // still only embedded in the b64 segment is the fallback (pinned on save)
    let imported = false;
    try {
      const r = await fetch('/api/installs/' + INSTALL); const d = await r.json();
      if (d && d.config) imported = importCfgObject(d.config);
    } catch(e) {}
    if (!imported && FROM_URL) {
      const m = location.pathname.match(/^\/([A-Za-z0-9_-]+)\/configure\/?$/);
      if (m) { try { imported = importCfgObject(JSON.parse(UNB64(m[1]))); } catch(e) {} }
    }
    if (imported) save();  // keeps localStorage in sync + pins the config
  }
  try {
    const r = await fetch('/api/repos'); DATA = await r.json();
    // first-visit default only — when a selection was imported (URL install or
    // saved state) it is the user's exact set and must not be padded
    if (Object.keys(state.p).length === 0) {
      allPlugins().forEach(p => { if (p.loaded && DEV_CHOICE.indexOf(p.internalName) >= 0) state.p[p.internalName] = 1; });
    }
    // populate repo + language filters
    const fr = document.getElementById('f-repo');
    (DATA.repos||[]).forEach(r2 => { const o = document.createElement('option'); o.value = r2.name; o.textContent = r2.name; fr.append(o); });
    const fl = document.getElementById('f-lang');
    Array.from(new Set(allPlugins().map(p => p.language).filter(Boolean))).sort().forEach(l => { const o = document.createElement('option'); o.value = l; o.textContent = l; fl.append(o); });
    const ft = document.getElementById('f-type');
    Array.from(new Set(allPlugins().flatMap(p => p.tvTypes||[]))).sort().forEach(t => { const o = document.createElement('option'); o.value = t; o.textContent = t + ' (' + allPlugins().filter(p => (p.tvTypes||[]).indexOf(t) >= 0).length + ')'; ft.append(o); });
    renderAll();
  } catch(e) {
    document.getElementById('syncLine').textContent = '⚠ failed to load repo data';
  }
  loadFormatter();
  pushInstall();  // make sure a record exists so the URL always serves what this page shows
}
let REIMPORT_AFTER_SYNC = false;
function poll() {
  fetch('/api/repos').then(r => r.json()).then(d => {
    DATA = d; renderAll();
    // a repo was just added: the server switches its sources on in the saved
    // install once the sync ends — pull that config back in so the next save
    // from this page does not switch them off again
    if (REIMPORT_AFTER_SYNC && !d.syncing && INSTALL) {
      REIMPORT_AFTER_SYNC = false;
      setTimeout(() => fetch('/api/installs/' + INSTALL).then(r => r.json()).then(x => { if (x && x.config && importCfgObject(x.config)) renderAll(); }).catch(() => {}), 4000);
    }
    if (d.syncing || d.healthRunning || REIMPORT_AFTER_SYNC) setTimeout(poll, 5000);
  }).catch(() => {});
}
boot();
setInterval(() => { fetch('/api/repos').then(r => r.json()).then(d => { DATA = d; renderAll(); }); }, 20000);
</script>
</body>
</html>""".trimIndent()
}
