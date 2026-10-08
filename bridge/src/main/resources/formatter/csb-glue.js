/*
 * CloudStream Bridge formatter glue — runs inside GraalJS right after the
 * AIOStreams engine bundle (reads module.exports). Loader order:
 *   csb-prelude.js → aiostreams-formatter.js → penguplay JSON → csb-glue.js
 * (stream.linkName / stream.source are registered inside the bundle's
 * FIELD_REGISTRY itself — the engine's canonical-field map is built at eval
 * time, so later pushes would not take).
 *
 * Ported from Stream Master's providers/lib/formatter.js, adapted to
 * CloudStream ExtractorLink input:
 *   meta  = { provider, linkName, source, quality (int), url, kind }
 *   ctx   = { mediaType, title, year, seasonNum, episodeNum }
 * render()/preview() run buildMeta() on the raw link first — everything
 * crosses the polyglot boundary as JSON strings.
 *
 * Template syntax = AIOStreams' custom formatter (PenguPlay uses the same
 * engine). CloudStream Bridge extras on top of the AIOStreams fields:
 *   stream.linkName  — the extractor's own label for this link
 *   stream.source    — the extractor class that produced the link
 */

(function () {
  const ENGINE = module.exports;
  const PENGUPLAY_PRESETS = typeof __PENGUPLAY_PRESETS !== "undefined" ? __PENGUPLAY_PRESETS : {};
  const ADDON_NAME = "CloudStream Bridge";

  // ── Presets ─────────────────────────────────────────────────────────────
  const CSB_PRESETS = {
    "csb-default": {
      label: "CloudStream Bridge · Default",
      name: `🎯 CSB{stream.resolution::exists[" {stream.resolution}"||""]}{? • {stream.quality}?}`,
      description: `{stream.filename::default('CloudStream stream')}
{stream.provider::exists["🛰️ {stream.provider}{? • {stream.server}?}"||"{tools.removeLine}"]}
{stream.languages::exists["🗣️ {stream.languages::join(' / ')}"||"{tools.removeLine}"]}
{stream.specs::exists["🏷️ {stream.specs::join(' • ')}"||"{tools.removeLine}"]}`,
    },
    "csb-modern": {
      label: "CloudStream Bridge · Modern",
      name: `{stream.provider}\n{stream.resolution::default('')}`,
      description: `{stream.filename::exists["{stream.filename}"||"{stream.linkName::default('CloudStream stream')}"]}`,
    },
    "csb-minimal": {
      label: "CloudStream Bridge · Minimal",
      name: `CSB {stream.resolution::default('')}`,
      description: `{stream.filename::exists["{stream.filename}"||"{stream.linkName::default('CloudStream stream')}"]}`,
    },
  };

  const CSB_LABELS = {
    "csb-default": "CloudStream Bridge · Default",
    "csb-modern": "CloudStream Bridge · Modern",
    "csb-minimal": "CloudStream Bridge · Minimal",
  };
  const AIOSTREAMS_LABELS = {
    torrentio: "AIOStreams · Torrentio", torbox: "AIOStreams · TorBox", gdrive: "AIOStreams · Google Drive",
    lightgdrive: "AIOStreams · Light Google Drive", minimalisticgdrive: "AIOStreams · Minimalistic Google Drive",
    prism: "AIOStreams · Prism", tamtaro: "AIOStreams · TamTaro",
  };

  function allPresets() {
    const out = {};
    for (const [id, p] of Object.entries(CSB_PRESETS)) out[id] = { ...p, family: "CloudStream Bridge" };
    for (const [id, p] of Object.entries(ENGINE.BUILTIN_FORMATTER_DEFINITIONS || {})) {
      out[`aio-${id}`] = { label: AIOSTREAMS_LABELS[id] || `AIOStreams · ${id}`, name: p.name, description: p.description, family: "AIOStreams" };
    }
    for (const [id, p] of Object.entries(PENGUPLAY_PRESETS)) out[id] = { ...p, family: "PenguPlay" };
    return out;
  }

  function templatesFor(f, n, d) {
    if (!f || f === "builtin") return null;
    if (f === "custom") {
      const name = typeof n === "string" ? n : "";
      const description = typeof d === "string" ? d : "";
      if (!name.trim() && !description.trim()) return null;
      return { name, description };
    }
    const p = allPresets()[f];
    return p ? { name: p.name, description: p.description } : null;
  }

  // ── Field extraction from a CloudStream link ────────────────────────────
  function pick(re, text) {
    const m = String(text || "").match(re);
    return m ? m[0] : null;
  }

  function decodeSafe(s) {
    try { return decodeURIComponent(s); } catch (e) { return s; }
  }

  function parseSizeBytes(text) {
    const m = String(text || "").match(/(\d+(?:\.\d+)?)\s*(TB|GB|MB)\b/i);
    if (!m) return null;
    const mult = { TB: 1e12, GB: 1e9, MB: 1e6 }[m[2].toUpperCase()];
    const bytes = Math.round(parseFloat(m[1]) * mult);
    return bytes >= 50e6 ? bytes : null;
  }

  function parseQuality(t) {
    const rules = [
      [/\bremux\b/i, "BluRay REMUX"], [/blu-?ray|bdrip|brrip/i, "BluRay"], [/web-?dl/i, "WEB-DL"],
      [/web-?rip/i, "WEBRip"], [/hdrip/i, "HDRip"], [/hdtv/i, "HDTV"], [/dvdrip/i, "DVDRip"],
      [/\bhdtc\b|\bhd-?ts\b|\bcam\b|\btelesync\b/i, "CAM"], [/\bweb\b/i, "WEB"],
    ];
    for (const [re, q] of rules) if (re.test(t)) return q;
    return null;
  }

  function parseEncode(t) {
    if (/\bav1\b/i.test(t)) return "AV1";
    if (/x265|h\.?265|\bhevc\b/i.test(t)) return "HEVC";
    if (/x264|h\.?264|\bavc\b/i.test(t)) return "AVC";
    return null;
  }

  function parseVisualTags(t) {
    const out = [];
    if (/\bdv\b|dolby.?vision|\bdovi\b/i.test(t)) out.push("DV");
    if (/hdr10\+|hdr10plus/i.test(t)) out.push("HDR10+");
    else if (/hdr10\b/i.test(t)) out.push("HDR10");
    else if (/\bhdr\b/i.test(t)) out.push("HDR");
    if (/10.?bit/i.test(t)) out.push("10bit");
    if (/\bimax\b/i.test(t)) out.push("IMAX");
    return out.length ? out : null;
  }

  function parseAudioTags(t) {
    const out = [];
    if (/atmos/i.test(t)) out.push("Atmos");
    if (/truehd/i.test(t)) out.push("TrueHD");
    if (/dts-?hd.?ma/i.test(t)) out.push("DTS-HD MA");
    else if (/\bdts\b/i.test(t)) out.push("DTS");
    if (/ddp|dd\+|e-?ac-?3/i.test(t)) out.push("DD+");
    else if (/\bdd\b|\bac-?3\b|dolby.?digital/i.test(t)) out.push("DD");
    if (/\baac\b/i.test(t)) out.push("AAC");
    if (/\bopus\b/i.test(t)) out.push("Opus");
    return out.length ? out : null;
  }

  function parseChannels(t) {
    const m = String(t).match(/\b([257])[.\s]?([01])(?:ch)?\b/);
    return m ? [`${m[1]}.${m[2]}`] : null;
  }

  const KNOWN_LANGS = ["Hindi", "English", "Tamil", "Telugu", "Malayalam", "Kannada", "Bengali",
    "Punjabi", "Marathi", "Japanese", "Korean", "Chinese", "Mandarin", "Cantonese", "Spanish",
    "French", "German", "Turkish", "Arabic", "Portuguese", "Russian", "Italian", "Indonesian",
    "Thai", "Vietnamese", "Urdu", "Kurdish", "Persian", "Filipino", "Ukrainian", "Polish", "Dutch"];

  function parseLanguages(text) {
    const out = [];
    for (const lang of KNOWN_LANGS) {
      if (new RegExp(`\\b${lang}\\b`, "i").test(text)) out.push(lang === "Mandarin" ? "Chinese" : lang);
    }
    return out.length ? out : null;
  }

  function langMeta(names) {
    if (!names || !names.length) return { names: null, emojis: null, codes: null, small: null };
    const codes = names.map((n) => { try { return ENGINE.languageToCode(n) || null; } catch (e) { return null; } });
    return {
      names,
      emojis: names.map((n) => { try { return ENGINE.languageToEmoji(n) || "🏳️"; } catch (e) { return "🏳️"; } }),
      codes: codes.map((c, i) => (c || names[i].slice(0, 2)).toUpperCase()),
      small: codes.map((c, i) => ENGINE.makeSmall((c || names[i].slice(0, 2)).toUpperCase())),
    };
  }

  function hostOf(url) {
    try { return new URL(url).hostname.replace(/^www\./, ""); } catch (e) { return null; }
  }

  // ExtractorLink -> formatter meta (same shape Stream Master builds from its
  // validated links; there is no link-check data here, so speed fields are
  // null and presets that print them render their missing branches).
  function buildMeta(m) {
    const url = String(m.url || "");
    let filenameRaw = "";
    try {
      const clean = url.split("?")[0].split("#")[0];
      const base = clean.substring(clean.lastIndexOf("/") + 1);
      const dec = decodeSafe(base);
      // real file basenames only — HLS manifests and DASH MPDs are noise
      if (/\.[a-z0-9]{2,5}$/i.test(dec) && !/^(playlist|index|master|manifest)[.\-\s]?\d*\.(m3u8|mpd)$/i.test(dec)) filenameRaw = dec;
    } catch (e) { /* magnet / weird urls have no filename */ }
    const linkName = String(m.linkName || "");
    const source = String(m.source || "");
    const text = `${linkName} ${source} ${filenameRaw}`;
    const kind = String(m.kind || "VIDEO");
    const streamType = kind === "M3U8" ? "HLS"
      : kind === "DASH" ? "DASH"
        : (pick(/\.(mkv|mp4|avi|webm|mov|ts)\b/i, `${filenameRaw}`) || "").replace(".", "").toUpperCase() || null;
    const q = Number(m.quality) || 0;
    const group = pick(/-([A-Za-z0-9]{2,12})(?:\.(?:mkv|mp4|avi))?$/, filenameRaw);
    const yearM = filenameRaw.match(/\b(19[3-9]\d|20[0-4]\d)\b/);
    const langs = parseLanguages(text);
    return {
      filename: filenameRaw || null,
      size: parseSizeBytes(text),
      resolution: q ? `${q >= 2160 ? 2160 : q >= 1440 ? 1440 : q >= 1080 ? 1080 : q >= 720 ? 720 : q >= 480 ? 480 : q >= 360 ? 360 : q}p` : null,
      quality: parseQuality(text),
      encode: parseEncode(text),
      visualTags: parseVisualTags(text),
      audioTags: parseAudioTags(text),
      audioChannels: parseChannels(text),
      languages: langs,
      subtitles: null,
      releaseGroup: group ? group.replace(/^-/, "").replace(/\.(mkv|mp4|avi)$/i, "") : null,
      year: yearM ? yearM[1] : null,
      title: filenameRaw ? (filenameRaw.split(/[.\s(\[]+(?:19[3-9]\d|20[0-4]\d|S\d{2}|2160p|1080p|720p)/i)[0].replace(/[._]+/g, " ").replace(/[\s\-–:|(\[]+$/, "").trim() || null) : null,
      streamType,
      proxied: false,
      provider: String(m.provider || "") || null,
      server: hostOf(url),
      linkName: linkName || null,
      source: source || null,
    };
  }

  function specsOf(m) {
    const out = [];
    if (m.resolution) out.push(m.resolution === "2160p" ? "4K" : m.resolution);
    if (m.quality) out.push(m.quality);
    for (const x of m.visualTags || []) out.push(x);
    for (const x of m.audioTags || []) out.push(x);
    if (m.encode) out.push(m.encode === "AVC" ? "H.264" : m.encode === "HEVC" ? "H.265" : m.encode);
    return out.length ? out : null;
  }

  // Full AIOStreams ParseValue (every field present, null when unknown — a
  // missing key would render as {unknown_propertyName(...)}), plus the
  // PenguPlay fields and the CloudStream Bridge extras.
  function buildParseValue(meta, ctx = {}) {
    const m = meta || {};
    const langs = langMeta(m.languages && m.languages.length ? m.languages : null);
    const subs = langMeta(m.subtitles);
    const season = ctx.seasonNum || null;
    const episode = ctx.episodeNum || null;
    const pad = (n) => String(n).padStart(2, "0");
    const seasonEpisode = season && episode ? [`S${pad(season)}`, `E${pad(episode)}`] : null;
    return {
      config: { addonName: ADDON_NAME },
      stream: {
        // PenguPlay
        specs: specsOf(m),
        streamType: m.streamType || null,
        // CloudStream Bridge extras
        linkName: m.linkName || null,
        source: m.source || null,
        // AIOStreams (no link-check data in the bridge: speed fields stay null)
        filename: m.filename || null, folderName: null, size: m.size || null, bitrate: null, folderSize: null,
        library: false, quality: m.quality || null, resolution: m.resolution || null,
        subbed: false, dubbed: /dub/i.test(m.filename || ""),
        mediaInfoQuality: "addon",
        languages: langs.names, uLanguages: langs.names, subtitles: subs.names, uSubtitles: subs.names,
        languageEmojis: langs.emojis, uLanguageEmojis: langs.emojis, subtitleEmojis: subs.emojis, uSubtitleEmojis: subs.emojis,
        languageCodes: langs.codes, uLanguageCodes: langs.codes, subtitleCodes: subs.codes, uSubtitleCodes: subs.codes,
        smallLanguageCodes: langs.small, uSmallLanguageCodes: langs.small, smallSubtitleCodes: subs.small, uSmallSubtitleCodes: subs.small,
        wedontknowwhatakilometeris: langs.emojis, uWedontknowwhatakilometeris: langs.emojis,
        visualTags: m.visualTags || null, audioTags: m.audioTags || null, audioTracks: [], subtitleTracks: [],
        releaseGroup: m.releaseGroup || null, regexMatched: null, rankedRegexMatched: [], regexScore: null, nRegexScore: null,
        encode: m.encode || null, audioChannels: m.audioChannels || null, edition: null, editions: null, remastered: null,
        regraded: false, repack: /\brepack\b/i.test(m.filename || ""), proper: /\bproper\b/i.test(m.filename || ""),
        uncensored: /uncensored/i.test(m.filename || ""), unrated: /unrated/i.test(m.filename || ""), upscaled: /upscaled/i.test(m.filename || ""),
        hasChapters: false, network: null, site: null,
        container: m.streamType && /^(MKV|MP4|AVI|WEBM|MOV|TS)$/.test(m.streamType) ? m.streamType.toLowerCase() : null,
        extension: m.streamType && /^(MKV|MP4|AVI|WEBM|MOV|TS)$/.test(m.streamType) ? `.${m.streamType.toLowerCase()}` : null,
        indexer: m.provider || null, year: m.year || null, title: m.title || null, country: null, episodeTitle: null, date: null,
        folderSeasons: null, formattedFolderSeasons: null,
        seasons: season ? [season] : null, season, formattedSeasons: season ? `S${pad(season)}` : null,
        episodes: episode ? [episode] : null, episode, formattedEpisodes: episode ? `E${pad(episode)}` : null,
        folderEpisodes: null, formattedFolderEpisodes: null, seasonEpisode, seasonPack: false,
        seeders: null, private: false, freeleech: null, age: null, ageHours: null, duration: null, infoHash: null,
        type: "http",
        message: null,
        proxied: false, seadex: false, seadexBest: false, seScore: null, nSeScore: null, seMatched: null,
        rseMatched: [], preloading: false, idMatched: true,
        // speed fields defined but unknown in the bridge
        speed: null, fast: null, speedLabel: null, speedMbps: null, startMs: null, startTime: null,
        provider: m.provider || null, server: m.server || null,
      },
      metadata: {
        queryType: ctx.mediaType === "series" ? "series" : "movie", type: ctx.mediaType === "series" ? "series" : "movie",
        isAnime: false, title: ctx.title || null, titles: ctx.title ? [ctx.title] : null,
        year: ctx.year ? Number(ctx.year) : null, yearEnd: null, runtime: null, episodeRuntime: null, genres: null,
        originalLanguage: null, country: null, season, episode, absoluteEpisode: null, relativeAbsoluteEpisode: null,
        episodeTitle: null, episodeTitles: null, latestSeason: null, daysSinceRelease: null, daysSinceFirstAired: null,
        daysSinceLastAired: null, hasNextEpisode: false, daysUntilNextEpisode: null, anilistId: null, malId: null, hasSeaDex: false,
      },
      user: {
        languages: ["Hindi", "English"], subtitles: ["English"],
        resolutions: ["2160p", "1440p", "1080p", "Unknown"],
        qualities: [], visualTags: [], audioTags: [], audioChannels: [], encodes: [], streamTypes: ["http"], releaseGroups: [], keywords: [],
      },
      service: { id: null, shortName: null, name: null, cached: null },
      addon: { name: ADDON_NAME, presetId: typeof ctx.presetId === "string" ? ctx.presetId : null, manifestUrl: null },
    };
  }

  function render(nameTpl, descTpl, meta, ctx) {
    const built = buildMeta(meta || {});
    let name;
    let description;
    try { name = ENGINE.compile(nameTpl)(buildParseValue(built, ctx)); } catch (e) { name = `⚠️ formatter error: ${e.message}`; }
    try { description = ENGINE.compile(descTpl)(buildParseValue(built, ctx)); } catch (e) { description = `⚠️ formatter error: ${e.message}`; }
    return { name, description };
  }

  // Sample links mirroring what providers actually return, for the configure
  // page preview.
  const SAMPLES = [
    {
      label: "Movie · Hindi 4K · direct file",
      meta: { provider: "VegaMovies", linkName: "4K HDR • Fast Download", source: "VegaMovies",
        quality: 2160, kind: "VIDEO",
        url: "https://dl.vegamovies.example/FILES/Inception.2010.2160p.WEB-DL.DDP5.1.Atmos.HDR10.HEVC.Hindi-ELiTe.mkv" },
      ctx: { mediaType: "movie", title: "Inception", year: 2010 },
    },
    {
      label: "Series · English 1080p · direct file",
      meta: { provider: "HDHub4u", linkName: "BluRay 1080p", source: "Gdflix",
        quality: 1080, kind: "VIDEO",
        url: "https://cdn.example.net/w/Breaking.Bad.S01E01.1080p.BluRay.x264-DEMAND.mkv" },
      ctx: { mediaType: "series", title: "Breaking Bad", year: 2008, seasonNum: 1, episodeNum: 1 },
    },
    {
      label: "Anime · HLS · unknown quality",
      meta: { provider: "Anikoto", linkName: "Subbed • 1080p", source: "Megaplay",
        quality: 0, kind: "M3U8",
        url: "https://megaplay.example/stream/e1/playlist.m3u8" },
      ctx: { mediaType: "series", title: "Attack on Titan", year: 2013, seasonNum: 1, episodeNum: 1 },
    },
  ];

  function previewJson(nameTpl, descTpl) {
    return SAMPLES.map((s) => ({ label: s.label, ...render(nameTpl, descTpl, s.meta, s.ctx) }));
  }

  function validateJson(tpl) {
    try { return ENGINE.validateTemplate(String(tpl || "")); } catch (e) { return [{ message: e.message }]; }
  }

  function fieldsWithExtras() {
    const reg = ENGINE.FIELD_REGISTRY || {};
    const out = {};
    for (const [sec, props] of Object.entries(reg)) {
      out[sec] = Array.isArray(props) ? props.slice() : props;
    }
    if (Array.isArray(out.stream)) {
      for (const extra of ["linkName", "source", "speed", "fast", "speedLabel", "speedMbps", "startMs", "startTime"]) {
        if (!out.stream.includes(extra)) out.stream.push(extra);
      }
    }
    return out;
  }

  // ── Polyglot API (all strings in/out) ───────────────────────────────────
  globalThis.__CSB = {
    // {f,n,d} -> templates or null
    templates(f, n, d) {
      const t = templatesFor(f, n, d);
      return t == null ? null : JSON.stringify(t);
    },
    // templates + meta json + ctx json -> {name, description}
    render(nameTpl, descTpl, metaJson, ctxJson) {
      const meta = metaJson ? JSON.parse(metaJson) : {};
      const ctx = ctxJson ? JSON.parse(ctxJson) : {};
      return JSON.stringify(render(String(nameTpl), String(descTpl), meta, ctx));
    },
    // templates + [[metaJson, ctxJson], ...] -> [rendered-or-null, ...]
    // (one polyglot hop for a whole serve instead of one execute per link)
    renderBatch(nameTpl, descTpl, arrJson) {
      let arr;
      try { arr = JSON.parse(arrJson); } catch (e) { return "[]"; }
      const out = arr.map((it) => {
        try {
          const meta = it && it[0] ? JSON.parse(it[0]) : {};
          const ctx = it && it[1] ? JSON.parse(it[1]) : {};
          // render() already returns the object — JSON.parse-ing it threw
          // "[object Object] is not valid JSON" per entry, so EVERY serve
          // silently fell back to built-in naming while preview looked fine
          return render(String(nameTpl), String(descTpl), meta, ctx);
        } catch (e) { return null; }
      });
      return JSON.stringify(out);
    },
    // templates -> [{label, name, description}]
    preview(nameTpl, descTpl) {
      return JSON.stringify(previewJson(String(nameTpl || ""), String(descTpl || "")));
    },
    validate(nameTpl, descTpl) {
      return JSON.stringify({ name: validateJson(nameTpl), description: validateJson(descTpl) });
    },
    presets() {
      const list = Object.entries(allPresets()).map(([id, p]) => ({
        id, label: p.label || id, family: p.family || "Other", name: p.name, description: p.description,
      }));
      return JSON.stringify({ presets: list, fields: fieldsWithExtras() });
    },
  };
})();
