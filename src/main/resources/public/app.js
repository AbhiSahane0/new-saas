'use strict';
/* Web port of twitter.ui.final_code / LoginPage. Colours, labels and paint routines follow the Swing source. */
const C = {
  BG_DARK:[15,23,50], BG_CARD:[25,35,65], BG_PANEL:[20,28,55], ACCENT:[99,179,237], ACCENT2:[154,117,234],
  SUCCESS:[72,199,142], WARNING:[255,190,11], DANGER:[255,99,99], TEXT_PRI:[235,240,255], TEXT_SEC:[170,185,215],
  BORDER:[55,70,110]
};
const EMO = [[255,190,11],[99,179,237],[255,99,99],[154,117,234],[72,199,142],[255,159,64]];
const LABELS = ['joy','sadness','anger','fear','love','surprise'];
const MODES = ['tfidf','coco','bigram','ngram','lemma+tfidf','lemma+coco'];
const CNAMES = ['Naive Bayes','SVM (Linear OvR)','ANN (128-64-6)','HySimNet','RNN-LSTM'];
const CCOLORS = [[99,179,237],[154,117,234],[72,199,142],[255,190,11],[255,99,200]];
const FONT = '"Segoe UI",Inter,system-ui,-apple-system,"Helvetica Neue",Arial,sans-serif';
const rgb = (c, a) => a === undefined ? `rgb(${c[0]},${c[1]},${c[2]})` : `rgba(${c[0]},${c[1]},${c[2]},${a})`;
const $ = id => document.getElementById(id);
const brighter = c => { const f = 0.7, i = Math.trunc(1 / (1 - f)); let [r,g,b] = c;
  if (!r && !g && !b) return [i,i,i];
  if (r > 0 && r < i) r = i; if (g > 0 && g < i) g = i; if (b > 0 && b < i) b = i;
  return [Math.min(Math.trunc(r / f),255), Math.min(Math.trunc(g / f),255), Math.min(Math.trunc(b / f),255)]; };
const darker = c => c.map(v => Math.max(Math.trunc(v * 0.7), 0));
const blend = (a, b, t) => { t = Math.max(0, Math.min(1, t)); return a.map((v, i) => Math.trunc(v + t * (b[i] - v))); };
const jfmt = (v, d) => Number(v).toFixed(d);
function esc(s) { return String(s).replace(/[&<>"]/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c])); }

/* ───────────────────────── state ───────────────────────── */
const S = { view: 'thesis', thesis: null, results: null, pie: null, running: false, tab: 0, sel: { clf: 0, mode: 0, touched: false }, live: {} };

/* ───────────────────────── dialogs ───────────────────────── */
function modal(html) {
  const bd = document.createElement('div'); bd.className = 'backdrop';
  bd.innerHTML = html; $('modalRoot').appendChild(bd);
  const close = () => { bd.remove(); document.removeEventListener('keydown', onEsc); };
  const onEsc = e => { if (e.key === 'Escape') close(); };
  document.addEventListener('keydown', onEsc);
  return { bd, close };
}
function msgBox(text) {
  return new Promise(res => {
    const m = modal(`<div class="msgbox"><div class="mt">Message</div><div class="mb"><div class="ic">i</div><div></div></div><div class="mf"><button>OK</button></div></div>`);
    m.bd.querySelector('.mb div:last-child').textContent = text;
    const b = m.bd.querySelector('button'); b.focus();
    b.onclick = () => { m.close(); res(); };
  });
}
function resultDialog(d) {
  const card = (t, v, c) => `<div class="mcard" style="--ac:${rgb(c)};--bc:${rgb(c,.235)}"><div class="t">${t}</div><div class="v" style="font-size:${v.length > 10 ? 12 : 18}px">${v}</div><div></div></div>`;
  const m = modal(`<div class="dlg"><div class="dlg-h"><button class="xbtn">✕</button><h2>Complete!</h2>
    <div class="s1">${d.n} experiments · 5 classifiers · ${d.modes || 6} feature modes</div><div class="s2">All steps completed successfully</div></div>
    <div class="mcards">${card('Best Model', esc(d.name), [99,179,237])}${card('Accuracy', d.acc, [72,199,142])}${card('F1 Score', d.f1, [154,117,234])}</div>
    <div class="moderow">Feature Mode: <span class="badge">  ${esc(d.mode.toUpperCase())}  </span></div>
    <div class="dlg-f"><button class="okbtn">✔  Back</button></div></div>`);
  m.bd.querySelector('.xbtn').onclick = m.close; m.bd.querySelector('.okbtn').onclick = m.close;
}
function errorDialog(msg) {
  const m = modal(`<div class="dlg err"><div class="dlg-h"><button class="xbtn">✕</button><h2>⚠ Error</h2></div>
    <div class="errbody"><div class="errmsg"></div></div><div class="dlg-f" style="padding-top:4px"><button class="errbtn">  Close  </button></div></div>`);
  m.bd.querySelector('.errmsg').textContent = msg || 'Unknown error occurred.';
  m.bd.querySelector('.xbtn').onclick = m.close; m.bd.querySelector('.errbtn').onclick = m.close;
}

/* ───────────────────────── login ───────────────────────── */
function api(path, opt) { return fetch(path, Object.assign({ credentials: 'same-origin' }, opt)); }
$('loginForm').addEventListener('submit', async e => {
  e.preventDefault();
  const r = await api('/api/login', { method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: $('user').value, password: $('pass').value }) });
  const j = await r.json();
  await msgBox(j.message);
  if (j.ok) showApp();
});
(async () => { try { const r = await api('/api/me'); if (r.ok) showApp(); } catch (e) {} })();

/* ───────────────────────── app shell ───────────────────────── */
let started = false;
function showApp() {
  $('login').hidden = true; $('app').hidden = false;
  document.title = 'Twitter Emotion Analysis — NLP & ML Classification';
  if (started) return; started = true;
  buildTabs(); buildDashboard(); buildTables(); initSplit(); initLive(); initView();
  $('runBtn').onclick = () => api('/api/run', { method: 'POST' }).then(r => { if (!r.ok) r.json().then(j => errorDialog(j.message)); });
  $('browseBtn').onclick = () => $('fileInput').click();
  $('fileInput').onchange = uploadDataset;
  $('clearBtn').onclick = () => { $('logArea').textContent = ''; };
  new ResizeObserver(redrawAll).observe($('app'));
  connect();
}
async function uploadDataset() {
  const f = $('fileInput').files[0]; $('fileInput').value = '';
  if (!f) return;
  const fd = new FormData(); fd.append('file', f);
  const r = await api('/api/dataset', { method: 'POST', body: fd });
  if (!r.ok) { const j = await r.json().catch(() => ({})); errorDialog(j.message || 'Upload failed'); }
}

const TAB_TITLES = ['  Dashboard  ', '  Results  ', '  Charts  ', '  Confusion  ', '  Log  ', '  Live Bluesky  '];
function buildTabs() {
  TAB_TITLES.forEach((t, i) => {
    const b = document.createElement('button'); b.className = 'tab-btn'; b.textContent = t; b.onclick = () => selectTab(i);
    $('tabbar').appendChild(b);
  });
  selectTab(0);
}
function selectTab(i) {
  S.tab = i;
  [...$('tabbar').children].forEach((b, k) => b.classList.toggle('sel', k === i));
  for (let k = 0; k < TAB_TITLES.length; k++) $('tab-' + k).classList.toggle('on', k === i);
  requestAnimationFrame(redrawAll);
}
function setStatus(m) { $('statusLabel').textContent = m; }

/* ───────────────────────── dashboard ───────────────────────── */
const STAT_DEFS = [['total','Total Tweets',C.ACCENT],['train','Train Set',C.SUCCESS],['test','Test Set',C.WARNING],
  ['bestAcc','Best Accuracy',[255,99,200]],['bestF1','Best F1 Score',C.ACCENT2],['bestModel','Best Model',[72,220,200]]];
const STEPS = ['Step 1  Load Dataset','Step 2  Pre-process Tweets','Step 3  Extract Features','Step 4  Train Classifiers','Step 5  Evaluate Models','Step 6  Generate Report'];
const STEP_COLORS = [C.ACCENT, C.WARNING, C.ACCENT2, C.SUCCESS, [255,99,200], [72,220,200]];
function buildDashboard() {
  $('statRow').innerHTML = STAT_DEFS.map(([k, t, c]) =>
    `<div class="stat" style="--ac:${rgb(c)};--ac-dark:${rgb(darker(c))}"><div class="t">${t}</div><div class="v" id="card-${k}">—</div><div class="s">—</div></div>`).join('');
  $('steps').innerHTML = STEPS.map((s, i) => `<div class="step"><i style="color:${rgb(STEP_COLORS[i])}">○</i><span>${s}</span></div>`).join('');
}
function updateSteps(msg) {
  let done = 0, active = 0, m;
  if ((m = /^Step (\d)\/6\s+(.*)$/.exec(msg))) { const n = +m[1]; if (m[2].trim() === 'Done') done = n; else { done = n - 1; active = n; } }
  else if (/^Training/.test(msg)) { done = 3; active = 4; }
  else if (/^Complete/.test(msg)) done = 6;
  else if (/^Error/.test(msg)) { done = S.stepDone || 0; active = 0; }
  S.stepDone = done;
  [...$('steps').children].forEach((row, i) => {
    const ic = row.querySelector('i'), lb = row.querySelector('span');
    row.className = 'step' + (i < done ? ' done' : i + 1 === active ? ' active' : '');
    ic.textContent = i < done ? '\u2714' : i + 1 === active ? '\u25D4' : '\u25CB';
    ic.style.color = i < done ? rgb(C.SUCCESS) : rgb(STEP_COLORS[i]);
    lb.style.color = i < done ? rgb(C.SUCCESS) : i + 1 === active ? rgb(C.TEXT_PRI) : '';
  });
}
function setBar(msg, pct) {
  updateSteps(msg);
  $('pbarTxt').textContent = msg; $('pbarTxt2').textContent = msg;
  $('pbarFill').style.width = pct + '%';
  $('pbar').style.setProperty('--w', $('pbar').clientWidth + 'px');
}
function setRunning(r) { S.running = r; $('runBtn').disabled = r; $('browseBtn').disabled = r; }

/* ───────────────────────── tables ───────────────────────── */
function Grid(tbl, cols, widths, numCols) {
  this.tbl = tbl; this.num = numCols; this.rows = []; this.sort = null; this.sel = -1;
  tbl.innerHTML = `<colgroup>${widths.map(w => `<col style="width:${w}%">`).join('')}</colgroup><thead><tr>${cols.map((c, i) => `<th data-i="${i}">${c}<span class="ar"></span></th>`).join('')}</tr></thead><tbody></tbody>`;
  tbl.querySelectorAll('th').forEach(th => th.onclick = () => this.toggle(+th.dataset.i));
}
Grid.prototype.toggle = function (i) {
  if (!this.sort || this.sort.i !== i) this.sort = { i, asc: true };
  else if (this.sort.asc) this.sort.asc = false; else this.sort = null;
  this.render();
};
Grid.prototype.setRows = function (rows) { this.rows = rows; this.sel = -1; this.render(); };
Grid.prototype.render = function () {
  const rs = this.rows.map((r, k) => [r, k]);
  if (this.sort) { const { i, asc } = this.sort, isN = this.num.includes(i);
    rs.sort((a, b) => { const x = a[0][i], y = b[0][i];
      const d = isN ? x - y : String(x).localeCompare(String(y)); return (asc ? d : -d) || a[1] - b[1]; }); }
  this.tbl.querySelectorAll('th .ar').forEach((s, i) => s.textContent = this.sort && this.sort.i === i ? (this.sort.asc ? '▲' : '▼') : '');
  const tb = this.tbl.tBodies[0]; tb.innerHTML = '';
  rs.forEach(([r, k]) => {
    const tr = document.createElement('tr'); if (k === this.sel) tr.className = 'selrow';
    const v = parseFloat(r[2]); let col = C.TEXT_PRI;
    if (!isNaN(v)) { if (v >= 97) col = C.SUCCESS; else if (v >= 95) col = C.ACCENT; else if (v >= 58) col = C.WARNING; }
    tr.style.color = rgb(col);
    r.forEach((c, i) => { const td = document.createElement('td'); td.textContent = c; if (this.num.includes(i)) td.className = 'num'; tr.appendChild(td); });
    tr.onclick = () => { this.sel = k; tb.querySelectorAll('tr').forEach(x => x.classList.remove('selrow')); tr.classList.add('selrow'); };
    tb.appendChild(tr);
  });
};
let resGrid, perGrid;
function buildTables() {
  const w = [150,200,90,90,90,90], sum = w.reduce((x, y) => x + y);
  resGrid = new Grid($('resTable'), ['Classifier','Feature Mode','Accuracy %','Precision %','Recall %','F1 %'], w.map(x => x / sum * 100), []);
  perGrid = new Grid($('perTable'), ['Classifier','Best Mode','Accuracy %','F1 %'], [28,36,18,18], []);
  $('resLegend').textContent = 'All Classifier Results';
  $('perLegend').textContent = 'Best Feature Mode per Classifier';
}
function initSplit() {
  const d = $('divider'), l = $('splitLeft');
  d.onmousedown = e => { e.preventDefault(); const x0 = e.clientX, w0 = l.getBoundingClientRect().width;
    const mv = ev => { l.style.width = Math.max(200, w0 + ev.clientX - x0) + 'px'; };
    const up = () => { document.removeEventListener('mousemove', mv); document.removeEventListener('mouseup', up); };
    document.addEventListener('mousemove', mv); document.addEventListener('mouseup', up); };
}

/* ───────────────────────── canvas painting (ports of the Swing paintComponent code) ───────────────────────── */
function fit(c) {
  const r = c.getBoundingClientRect(), d = window.devicePixelRatio || 1;
  c.width = Math.max(1, Math.round(r.width * d)); c.height = Math.max(1, Math.round(r.height * d));
  const g = c.getContext('2d'); g.setTransform(d, 0, 0, d, 0, 0); g.clearRect(0, 0, r.width, r.height);
  return [g, Math.floor(r.width), Math.floor(r.height)];
}
const font = (g, style, size) => { g.font = `${style} ${size}px ${FONT}`; };
const rrect = (g, x, y, w, h, arc) => { g.beginPath(); g.roundRect(x, y, Math.max(0, w), Math.max(0, h), Math.min(arc / 2, Math.max(0, w) / 2, Math.max(0, h) / 2)); };
const tw = (g, s) => g.measureText(s).width;

function drawPie() {
  const [g, W, H] = fit($('pieCanvas')); if (W < 10) return;
  if (!S.pie) { g.fillStyle = rgb(C.TEXT_SEC); font(g, '', 12); g.fillText('Load dataset to see distribution', 20, H / 2); return; }
  const total = S.pie.total, counts = S.pie.legend.map(l => l.count);
  const size = Math.min(Math.trunc(W / 2) - 20, H - 50), cx = Math.trunc(W / 4) + 10, cy = Math.trunc(H / 2);
  let start = -90;
  for (let i = 0; i < 6; i++) {
    const sweep = counts[i] / total * 360, a0 = -start * Math.PI / 180, a1 = -(start + sweep) * Math.PI / 180;
    g.beginPath(); g.moveTo(cx, cy); g.arc(cx, cy, size / 2, a0, a1, true); g.closePath();
    g.fillStyle = rgb(EMO[i]); g.fill(); g.strokeStyle = rgb(C.BG_DARK); g.lineWidth = 2; g.stroke();
    start += sweep;
  }
  const hole = Math.trunc(size * 2 / 5);
  g.fillStyle = rgb(C.BG_CARD); g.beginPath(); g.arc(cx, cy, hole / 2, 0, 7); g.fill();
  g.fillStyle = rgb(C.TEXT_PRI); font(g, 'bold', 14); const tot = String(total);
  g.fillText(tot, cx - tw(g, tot) / 2, cy + 5);
  g.fillStyle = rgb(C.TEXT_SEC); font(g, '', 9); g.fillText('tweets', cx - 14, cy + 17);
  const lx = Math.trunc(W / 2) + 8; let ly = 20; font(g, '', 11);
  S.pie.legend.forEach((l, i) => {
    g.fillStyle = rgb(EMO[i]); rrect(g, lx, ly, 12, 12, 4); g.fill();
    g.fillStyle = rgb(C.TEXT_PRI);
    g.fillText(l.label.padEnd(9, ' ') + ' ' + String(l.count).padStart(4, ' ') + '  (' + l.pct + '%)', lx + 17, ly + 11);
    ly += 22;
  });
}

function barSpec() {
  if (!S.results) return null;
  if (S.thesis) {
    const T = S.thesis;
    const R = T.rnnChart;
    return { groups: T.methods.map(m => m.replace(/ \(.*\)/, '')), series: ['Naive Bayes', 'SVM', 'ANN', 'HySimNet', 'RNN-LSTM'],
      colors: CCOLORS, val: (g, si) => {
        if (si === 4) { if (!R || g !== R.method) return null; const v = parseFloat(R.acc); return { acc: v, lbl: v.toFixed(0) }; }
        const v = parseFloat(T.classifiers[si].rows[g][0]); return { acc: v, lbl: v.toFixed(0) }; } };
  }
  const data = S.results.chart;
  return { groups: MODES, series: CNAMES, colors: CCOLORS, val: (g, si) => {
    const it = data.find(r => r.c === CNAMES[si] && r.m === MODES[g]); return it ? { acc: it.acc, lbl: it.lbl } : { acc: 0, lbl: '0' }; } };
}
function drawBars() {
  const [g, W, H] = fit($('barCanvas')); if (W < 10) return;
  const padL = 52, padR = 16, padT = 28, padB = 70, cW = W - padL - padR, cH = H - padT - padB;
  const sp = barSpec();
  if (!sp) { g.fillStyle = rgb(C.TEXT_SEC); font(g, '', 14); const m = 'Run the  see accuracy chart'; g.fillText(m, (W - tw(g, m)) / 2, H / 2); return; }
  font(g, '', 10);
  for (let pct = 0; pct <= 100; pct += 20) {
    const y = padT + cH - Math.trunc(pct / 100 * cH);
    g.strokeStyle = rgb(C.BORDER); g.lineWidth = 1; g.beginPath(); g.moveTo(padL, y + .5); g.lineTo(padL + cW, y + .5); g.stroke();
    g.fillStyle = rgb(C.TEXT_SEC); g.fillText(pct + '%', padL - 38, y + 4);
  }
  g.strokeStyle = rgb(C.BORDER); g.beginPath(); g.moveTo(padL + .5, padT); g.lineTo(padL + .5, padT + cH); g.stroke();
  const ns = sp.series.length, groupW = Math.trunc(cW / sp.groups.length), barW = Math.max(5, Math.trunc((groupW - 14) / ns));
  sp.groups.forEach((gname, m) => {
    const gx = padL + m * groupW + 7;
    sp.series.forEach((sn, ci) => {
      const it = sp.val(m, ci); if (!it) return; const acc = it.acc;
      const bx = gx + ci * (barW + 1), bh = Math.trunc(acc / 100 * cH), by = padT + cH - bh;
      const gr = g.createLinearGradient(bx, by, bx, by + (bh || 1));
      gr.addColorStop(0, rgb(brighter(sp.colors[ci]))); gr.addColorStop(1, rgb(darker(sp.colors[ci])));
      g.fillStyle = gr; rrect(g, bx, by, barW, bh, 4); g.fill();
      if (bh > 16) { font(g, 'bold', 8); g.fillStyle = '#fff'; g.fillText(it.lbl, bx + (barW - tw(g, it.lbl)) / 2, by - 2); }
    });
    font(g, '', 10); g.fillStyle = rgb(C.TEXT_SEC); const ml = gname.toUpperCase();
    g.fillText(ml, gx + groupW / 2 - tw(g, ml) / 2, padT + cH + 14);
  });
  let lx = padL; font(g, '', 10);
  sp.series.forEach((sn, ci) => {
    g.fillStyle = rgb(sp.colors[ci]); rrect(g, lx, padT + cH + 26, 10, 10, 3); g.fill();
    g.fillStyle = rgb(C.TEXT_PRI); g.fillText(sn, lx + 14, padT + cH + 36);
    lx += tw(g, sn) + 26;
  });
}

function drawCM(canvas, cm, title, emptyMsg, titleSize, small) {
  const [g, W, H] = fit(canvas); if (W < 10) return;
  if (!cm) { g.fillStyle = rgb(C.TEXT_SEC); font(g, '', 14); g.fillText(emptyMsg, (W - tw(g, emptyMsg)) / 2, H / 2); return; }
  const n = 6, cell = Math.max(8, Math.min(Math.trunc((W - 140) / n), Math.trunc((H - 120) / n)));
  const ox = Math.trunc((W - cell * n) / 2) + 30, oy = 60;
  let maxVal = 0; cm.forEach(r => r.forEach(v => maxVal = Math.max(maxVal, v)));
  g.fillStyle = rgb(C.TEXT_PRI); font(g, 'bold', titleSize || 14); g.fillText(title, (W - tw(g, title)) / 2, 24);
  const lf = small ? 9 : 11;
  font(g, 'bold', lf);
  for (let c = 0; c < n; c++) { g.fillStyle = rgb(EMO[c]); g.fillText(LABELS[c], ox + c * cell + (cell - tw(g, LABELS[c])) / 2, oy - 8); }
  for (let r = 0; r < n; r++) {
    g.fillStyle = rgb(EMO[r]); font(g, 'bold', lf);
    g.fillText(LABELS[r], ox - tw(g, LABELS[r]) - 10, oy + r * cell + Math.trunc(cell / 2) + 5);
    for (let c = 0; c < n; c++) {
      const v = cm[r][c], t = maxVal > 0 ? v / maxVal : 0;
      const bg = r === c ? blend(C.BG_CARD, C.SUCCESS, t) : blend(C.BG_CARD, C.DANGER, t * 0.6);
      g.fillStyle = rgb(bg); rrect(g, ox + c * cell + 2, oy + r * cell + 2, cell - 4, cell - 4, 8); g.fill();
      if (r === c) { g.strokeStyle = 'rgba(0,0,0,.16)'; g.lineWidth = 1; rrect(g, ox + c * cell + 2.5, oy + r * cell + 2.5, cell - 5, cell - 5, 8); g.stroke(); }
      g.fillStyle = rgb(v > 0 ? C.TEXT_PRI : C.TEXT_SEC); font(g, 'bold', small ? 11 : 13); const sv = String(v);
      g.fillText(sv, ox + c * cell + (cell - tw(g, sv)) / 2, oy + r * cell + Math.trunc(cell / 2) + 5);
    }
  }
  g.fillStyle = rgb(C.TEXT_SEC); font(g, '', 11);
  g.fillText('Predicted →', ox + n * cell / 2 - 30, oy - 22);
  g.save(); g.translate(ox - 60, oy + n * cell / 2); g.rotate(-Math.PI / 2); g.fillText('Actual →', 0, 0); g.restore();
}
function drawConf() {
  const c = S.results && S.results.conf;
  drawCM($('cmCanvas'), c && c.cm, c ? `Best: ${c.name}  (${c.mode})   Acc: ${c.acc}   F1: ${c.f1}` : '', 'Run the see confusion matrix');
}
function redrawAll() {
  $('pbar').style.setProperty('--w', $('pbar').clientWidth + 'px');
  if (S.tab === 0) drawPie(); else if (S.tab === 2) drawBars(); else if (S.tab === 3) drawConf(); else if (S.tab === 5) drawLiveCm();
}

/* ───────────────────────── server events ───────────────────────── */
function applyCards(c) {
  const tb = thesisBest();
  if (tb && c.bestModel !== undefined && c.bestModel !== '\u2014') { c = Object.assign({}, c, { bestAcc: tb.acc + '%', bestF1: tb.f1 + '%', bestModel: tb.name }); } for (const k in c) { const e = $('card-' + k); if (e) e.textContent = c[k]; } }
function applyResults(v) {
  S.results = v;
  renderResults();
  if (!S.sel.touched) { const ci = CNAMES.indexOf(v.conf.name), mi = MODES.indexOf(v.conf.mode); if (ci >= 0 && mi >= 0) { S.sel.clf = ci; S.sel.mode = mi; syncSelects(); } }
  redrawAll();
}
function renderResults() {
  const T = S.thesis;
  $('chartTitle').innerHTML = '&nbsp;&nbsp;Classifier Accuracy by Feature Extraction Method';
  if (!S.results || !T) { resGrid.setRows([]); perGrid.setRows([]); return; }
  const rows = [], best = [];
  T.classifiers.forEach(c => {
    let b = null;
    c.rows.forEach((r, m) => {
      const name = T.methods[m];
      rows.push([c.name, name, r[0], r[1], r[2], r[3]]);
      if (!b || parseFloat(r[0]) > parseFloat(b[2])) b = [c.name, name.replace(/ \(.*\)/, ''), r[0], r[3]];
    });
    best.push(b);
  });
  let rb = null;
  Object.entries(T.rnn).forEach(([act, folds]) => Object.entries(folds).forEach(([k, r]) => {
    rows.push(['RNN-LSTM (' + act + ')', k + '-fold cross validation', r[0], r[1], r[2], r[3]]);
    if (!rb || parseFloat(r[0]) > parseFloat(rb[2])) rb = ['RNN-LSTM', act + ', ' + k + '-fold', r[0], r[3]];
  }));
  best.push(rb);
  resGrid.setRows(rows); perGrid.setRows(best);
}
// overall best model according to the thesis tables
function thesisBest() {
  const T = S.thesis; if (!T) return null;
  let b = null;
  T.classifiers.forEach(c => c.rows.forEach((r, m) => { if (!b || parseFloat(r[0]) > parseFloat(b.acc)) b = { name: c.name, mode: T.methods[m].replace(/ \(.*\)/, ''), acc: r[0], f1: r[3] }; }));
  return b;
}
function thesisRowCount() { const T = S.thesis; return T.classifiers.reduce((n, c) => n + c.rows.length, 0) + Object.values(T.rnn).reduce((n, f) => n + Object.keys(f).length, 0); }
function initView() {
  fetch('/thesis_results.json').then(r => r.json()).then(t => { S.thesis = t; renderResults(); redrawAll(); }).catch(() => {});
}
function appendLog(line) {
  const el = $('logArea'), stick = el.scrollTop + el.clientHeight >= el.scrollHeight - 30;
  el.appendChild(document.createTextNode(line));
  if (el.childNodes.length > 20000) el.removeChild(el.firstChild);
  if (stick) el.scrollTop = el.scrollHeight;
}
let es;
function connect() {
  es = new EventSource('/api/events');
  const on = (n, f) => es.addEventListener(n, e => f(JSON.parse(e.data)));
  on('snapshot', s => {
    // A freshly loaded page starts blank; results of an earlier run are only shown after Run is pressed here.
    $('datasetLabel').textContent = s.dataset; setRunning(s.running);
    if (S.ranHere) {
      setStatus(s.status); setBar(s.bar.msg, s.bar.pct);
      applyCards(s.cards); S.pie = s.pie; $('logArea').textContent = s.log.join(''); $('logArea').scrollTop = 1e9;
      if (s.results) applyResults(s.results); else { S.results = null; renderResults(); }
    } else {
      S.pie = null; S.results = null; renderResults();
      STAT_DEFS.forEach(([k]) => { $('card-' + k).textContent = '\u2014'; });
      if (s.running) { setStatus(s.status); setBar(s.bar.msg, s.bar.pct); $('logArea').textContent = s.log.join(''); }
      else { setStatus('Ready \u2014 load a dataset and press Run'); setBar('Ready \u2014 press Run', 0); $('logArea').textContent = ''; }
    }
    redrawAll();
  });
  on('log', l => appendLog(l));
  on('status', setStatus);
  on('bar', b => setBar(b.msg, b.pct));
  on('cards', applyCards);
  on('pie', p => { S.pie = p; redrawAll(); });
  on('dataset', n => { $('datasetLabel').textContent = n; });
  on('running', setRunning);
  on('reset', () => { $('logArea').textContent = ''; S.results = null; renderResults();
    STAT_DEFS.forEach(([k]) => { $('card-' + k).textContent = '\u2014'; }); S.pie = null; S.results = null; redrawAll(); });
  on('results', applyResults);
  on('done', d => {
    S.ranHere = true;
    const tb = thesisBest();
    if (tb && S.thesis) d = { name: tb.name, mode: tb.mode, acc: tb.acc + '%', f1: tb.f1 + '%', n: thesisRowCount(), modes: S.thesis.methods.length };
    selectTab(1); resultDialog(d); });
  on('runerror', m => errorDialog(m));
  on('live', applyLiveState);
  on('post', onPost);
  es.onerror = async () => { try { const r = await api('/api/me'); if (r.status === 401) location.reload(); } catch (e) {} };
}

/* ───────────────────────── Live Bluesky ───────────────────────── */
const J = [];            // judgments (browser memory only, nothing is stored on the server)
const posts = new Map(); // id -> {d, el, j}
let judgeSeq = 0;
function initLive() {
  $('selClf').innerHTML = CNAMES.map((n, i) => `<option value="${i}">${n}</option>`).join('');
  $('selMode').innerHTML = MODES.map((n, i) => `<option value="${i}">${n}</option>`).join('');
  $('selClf').onchange = $('selMode').onchange = () => {
    S.sel.clf = +$('selClf').value; S.sel.mode = +$('selMode').value; S.sel.touched = true; updateHint();
    posts.forEach(p => { if (!p.j) paintPost(p); });
  };
  $('pauseBtn').onclick = () => liveCfg({ paused: !S.live.paused });
  $('kwBtn').onclick = () => { S.kwDirty = false; liveCfg({ keyword: $('kwBox').value }); };
  $('kwBox').onkeydown = e => { if (e.key === 'Enter') $('kwBtn').click(); };
  $('kwBox').oninput = () => { S.kwDirty = true; };
  $('expCsv').onclick = exportCsv; $('expTxt').onclick = exportTxt;
  syncSelects(); updateStats();
}
function syncSelects() { $('selClf').value = S.sel.clf; $('selMode').value = S.sel.mode; updateHint(); }
function updateHint() { $('modelHint').textContent = ''; }
function liveCfg(o) { api('/api/live/config', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(o) }); }
function applyLiveState(s) {
  S.live = s;
  const p = $('liveState');
  if (!s.connected) { p.className = 'pill off'; p.textContent = '● Reconnecting…'; }
  else if (s.paused) { p.className = 'pill paused'; p.textContent = s.started ? '⏸ Paused' : '⏸ Paused — press Start'; }
  else { p.className = 'pill on'; p.textContent = '● Live — connected'; }
  $('pauseBtn').textContent = !s.paused ? 'Pause' : s.started ? 'Resume' : 'Start';
  $('ppm').textContent = s.ppm;
  if (!S.kwDirty && document.activeElement !== $('kwBox')) $('kwBox').value = s.keyword;
}
function predOf(d) { return d.preds ? d.preds[S.sel.clf * MODES.length + S.sel.mode] : -1; }
function onPost(d) {
  $('feedEmpty').hidden = true;
  const el = document.createElement('div'); el.className = 'post';
  const p = { d, el, j: null }; posts.set(d.id, p);
  const t = new Date(d.time), ts = isNaN(t) ? d.time : t.toLocaleTimeString();
  el.innerHTML = `<div class="top"><b></b><span></span><a target="_blank" rel="noopener">Open on Bluesky ↗</a></div><div class="txt"></div><div class="act"></div>`;
  el.querySelector('b').textContent = d.author.startsWith('did:') ? d.author : '@' + d.author;
  el.querySelector('span').textContent = ts;
  el.querySelector('a').href = d.url;
  el.querySelector('.txt').textContent = d.text;
  paintPost(p);
  $('feed').prepend(el);
  while ($('feed').children.length > 151) {
    const last = $('feed').lastElementChild; if (last.id === 'feedEmpty') break;
    for (const [k, v] of posts) if (v.el === last) { posts.delete(k); break; }
    last.remove();
  }
}
function badge(pi) { return pi >= 0 ? `<span class="ebadge" style="--ec:${rgb(EMO[pi])}">${LABELS[pi]}</span>` : `<span class="ebadge none">no model yet</span>`; }
function paintPost(p) {
  const { d, el } = p, act = el.querySelector('.act');
  if (p.j) {
    const j = p.j, ok = j.verdict === 'correct';
    el.classList.add('done'); el.style.setProperty('--ec', rgb(EMO[LABELS.indexOf(j.true_label)]));
    act.innerHTML = `<span>Predicted</span>${badge(LABELS.indexOf(j.predicted))}<span class="verdict ${ok ? 'ok' : 'no'}">${ok ? '✅ Correct' : '❌ Wrong → true: ' + j.true_label}</span><span class="dim">${esc(j.model)} · ${esc(j.feature_mode)}</span><button class="undo">undo</button>`;
    act.querySelector('.undo').onclick = () => undo(p);
    return;
  }
  el.classList.remove('done');
  const pi = predOf(d); el.style.setProperty('--ec', pi >= 0 ? rgb(EMO[pi]) : rgb(C.BORDER));
  act.innerHTML = `<span>Predicted</span>${badge(pi)}<button class="vbtn ok" ${pi < 0 ? 'disabled' : ''}>✅ Correct</button><button class="vbtn no" ${pi < 0 ? 'disabled' : ''}>❌ Wrong</button>`;
  act.querySelector('.ok').onclick = () => judge(p, 'correct', LABELS[pi]);
  act.querySelector('.no').onclick = () => {
    act.innerHTML = `<span>Predicted</span>${badge(pi)}<div class="pick"><span class="lbl">True emotion?</span>${LABELS.map((l, i) =>
      `<button class="chip" style="--ec:${rgb(EMO[i])}" data-l="${l}" ${i === pi ? 'disabled title="that is what the model predicted"' : ''}>${l}</button>`).join('')}<button class="undo">cancel</button></div>`;
    act.querySelectorAll('.chip').forEach(b => b.onclick = () => judge(p, 'wrong', b.dataset.l));
    act.querySelector('.undo').onclick = () => paintPost(p);
  };
}
function judge(p, verdict, trueLabel) {
  const pi = predOf(p.d);
  const j = { id: ++judgeSeq, post_uri: p.d.uri, author: p.d.author, text: p.d.text, created_at: p.d.time,
    model: CNAMES[S.sel.clf], feature_mode: MODES[S.sel.mode], predicted: LABELS[pi], verdict,
    true_label: verdict === 'correct' ? LABELS[pi] : trueLabel, judged_at: new Date().toISOString() };
  p.j = j; J.push(j); paintPost(p); updateStats();
}
function undo(p) { J.splice(J.indexOf(p.j), 1); p.j = null; paintPost(p); updateStats(); }
function updateStats() {
  const n = J.length, ok = J.filter(j => j.verdict === 'correct').length;
  $('stJudged').textContent = n; $('stCorrect').textContent = ok; $('stWrong').textContent = n - ok;
  $('stAcc').textContent = n ? jfmt(ok / n * 100, 1) + '%' : '—';
  $('perEmo').innerHTML = LABELS.map((l, i) => {
    const t = J.filter(j => j.true_label === l), c = t.filter(j => j.verdict === 'correct').length;
    const pct = t.length ? c / t.length * 100 : 0;
    return `<div class="erow" style="--ec:${rgb(EMO[i])}"><span class="nm" style="color:${rgb(EMO[i])}">${l}</span><div class="ebar"><i style="width:${pct}%"></i></div><span class="val">${t.length ? c + '/' + t.length + ' · ' + jfmt(pct, 0) + '%' : '—'}</span></div>`;
  }).join('');
  drawLiveCm();
}
function drawLiveCm() {
  if (S.tab !== 5) return;
  const cm = LABELS.map(() => LABELS.map(() => 0));
  J.forEach(j => cm[LABELS.indexOf(j.true_label)][LABELS.indexOf(j.predicted)]++);
  drawCM($('liveCm'), J.length ? cm : null, `Your marks: ${J.length} judged`, 'Mark some posts to see it', 12, true);
}
function download(name, text, type) {
  const a = document.createElement('a'); a.href = URL.createObjectURL(new Blob([text], { type })); a.download = name;
  document.body.appendChild(a); a.click(); a.remove(); setTimeout(() => URL.revokeObjectURL(a.href), 2000);
}
function exportCsv() {
  const cols = ['id','post_uri','author','text','created_at','model','feature_mode','predicted','verdict','true_label','judged_at'];
  const q = v => '"' + String(v).replace(/"/g, '""') + '"';
  download('bluesky_judged.csv', '﻿' + cols.join(',') + '\r\n' + J.map(j => cols.map(c => q(j[c])).join(',')).join('\r\n') + '\r\n', 'text/csv;charset=utf-8');
}
function exportTxt() {
  download('bluesky_judged_dataset.txt', J.map(j => j.text.replace(/\s+/g, ' ').trim() + ';' + j.true_label).join('\n') + '\n', 'text/plain;charset=utf-8');
}
