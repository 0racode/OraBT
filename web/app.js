'use strict';
/* PastQ CBT, web version. Practice mode, CBT exam mode, results and review. Works offline once opened. */

const ENGLISH = 'English Language';
const TYPE_LABELS = {
  'utme': 'UTME', 'wassce': 'WASSCE', 'post-utme': 'Post-UTME', 'post-utme-aaua': 'Post-UTME (AAUA)',
  'model': 'Model', 'neco': 'NECO',
};
const typeLabel = (t) => TYPE_LABELS[t] || t;

/* ---------- storage (never throws) ---------- */
const store = {
  get(k, d) { try { const v = localStorage.getItem(k); return v ? JSON.parse(v) : d; } catch (e) { return d; } },
  set(k, v) { try { localStorage.setItem(k, JSON.stringify(v)); } catch (e) { /* private mode or full */ } },
  del(k) { try { localStorage.removeItem(k); } catch (e) { /* ignore */ } },
};

/* ---------- question text (HTML-ish, kept safe) ---------- */
const NAMED = {
  nbsp: ' ', amp: '&', lt: '<', gt: '>', quot: '"', apos: "'", ndash: '–', mdash: '—', hellip: '…',
  lsquo: '‘', rsquo: '’', ldquo: '“', rdquo: '”', times: '×', divide: '÷', plusmn: '±', deg: '°',
  frac12: '½', frac14: '¼', frac34: '¾', le: '≤', ge: '≥', ne: '≠', asymp: '≈', infin: '∞', pi: 'π',
  theta: 'θ', alpha: 'α', beta: 'β', gamma: 'γ', delta: 'δ', lambda: 'λ', mu: 'μ', sigma: 'σ',
  omega: 'ω', Omega: 'Ω', micro: 'µ', sup2: '²', sup3: '³', sup1: '¹', middot: '·', bull: '•',
  rarr: '→', larr: '←', copy: '©', cent: '¢', pound: '£', euro: '€', laquo: '«', raquo: '»',
  sect: '§', para: '¶', radic: '√', sum: '∑', minus: '−', prime: '′', Prime: '″',
  eacute: 'é', egrave: 'è', agrave: 'à', ocirc: 'ô', uuml: 'ü', ouml: 'ö',
};
const ENTITY = /&(#[xX][0-9a-fA-F]+|#[0-9]+|[A-Za-z][A-Za-z0-9]*);/g;
const codePoint = (n) => (n === 160 ? ' ' : n >= 1 && n <= 0x10ffff && !(n >= 0xd800 && n <= 0xdfff) ? String.fromCodePoint(n) : '');
function decodeEntities(input) {
  let s = input;
  for (let i = 0; i < 3; i++) { // repeats so double-encoded text also comes out right
    const next = s.replace(ENTITY, (m, b) => {
      if (b[0] === '#') {
        const n = b[1] === 'x' || b[1] === 'X' ? parseInt(b.slice(2), 16) : parseInt(b.slice(1), 10);
        return Number.isFinite(n) ? codePoint(n) : m;
      }
      return NAMED[b] !== undefined ? NAMED[b] : m;
    });
    if (next === s) return s;
    s = next;
  }
  return s;
}
const esc = (s) => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
const KEEP = new Set(['b', 'strong', 'i', 'em', 'u', 'sub', 'sup']);
const DROP = new Set(['span', 'div', 'font', 'a', 'ul', 'ol', 'li', 'table', 'tr', 'td', 'th', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'center', 'o:p', 'img']);
/** Only b/i/u/sub/sup/br are ever output as markup; everything else is escaped text, so bank text can't inject HTML. */
function rich(input) {
  const src = decodeEntities(String(input == null ? '' : input).trim());
  const re = /<\/?\s*([A-Za-z][A-Za-z0-9:]*)\b[^>]*>/g;
  let out = '', pos = 0, m;
  while ((m = re.exec(src))) {
    const name = m[1].toLowerCase();
    const known = KEEP.has(name) || name === 'br' || name === 'p' || DROP.has(name);
    if (!known) continue;
    out += esc(src.slice(pos, m.index));
    pos = re.lastIndex;
    const closing = m[0][1] === '/';
    if (KEEP.has(name)) out += closing ? `</${name}>` : `<${name}>`;
    else if (name === 'br' || (name === 'p' && closing)) out += '<br>';
  }
  out += esc(src.slice(pos));
  return out.replace(/\n/g, '<br>');
}

/* ---------- data ---------- */
let DATA = [];
const BY_ID = new Map();
let SUBJECTS = []; // [{name, count}]

async function loadData() {
  const res = await fetch('questions.json');
  if (!res.ok) throw new Error('Could not load the question bank (HTTP ' + res.status + ')');
  DATA = await res.json();
  const counts = new Map();
  for (const q of DATA) {
    if (q.type === 'posr-utme-aaua') q.type = 'post-utme-aaua'; // typo in the source data
    BY_ID.set(q.id, q);
    counts.set(q.subject, (counts.get(q.subject) || 0) + 1);
  }
  SUBJECTS = [...counts].map(([name, count]) => ({ name, count })).sort((a, b) => a.name.localeCompare(b.name));
}
const subjectQs = (subject, types) => DATA.filter((q) => q.subject === subject && (!types || types.size === 0 || types.has(q.type)));
const typesFor = (subject) => [...new Set(DATA.filter((q) => q.subject === subject && q.type).map((q) => q.type))].sort();
const yearsFor = (subject) => [...new Set(DATA.filter((q) => q.subject === subject && q.year).map((q) => q.year))].sort().reverse();
function shuffle(a) {
  a = a.slice();
  for (let i = a.length - 1; i > 0; i--) { const j = Math.floor(Math.random() * (i + 1)); [a[i], a[j]] = [a[j], a[i]]; }
  return a;
}
const optionsOf = (q) => ['a', 'b', 'c', 'd', 'e'].filter((l) => String(q[l] || '').trim() !== '');
const isRight = (q, picked) => picked && picked === String(q.answer || '').trim().toLowerCase()[0];

/* ---------- state ---------- */
const S = {
  practice: { subject: '', types: new Set(), year: '', list: [], i: 0, picked: {} },
  setup: { utme: true, chosen: new Set(), per: 20, types: new Set(), h: 2, m: 0 },
  exam: store.get('exam', null), // in-progress exam, survives a page refresh
  outcome: null,
  review: [],
  reviewFilter: 'all',
  showGrid: false,
};
let tick = null;
const $app = document.getElementById('app');

/* ---------- helpers ---------- */
const fmtTime = (secs) => {
  const s = Math.max(0, Math.round(secs));
  const h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60), r = s % 60;
  const p = (n) => String(n).padStart(2, '0');
  return h > 0 ? `${h}:${p(m)}:${p(r)}` : `${p(m)}:${p(r)}`;
};
const go = (hash) => { if (location.hash === hash) route(); else location.hash = hash; };
const pct = (a, b) => (b ? Math.round((a / b) * 100) : 0);
function bar(title, back) {
  return `<header class="bar">${back ? `<button class="ghost" data-act="go" data-to="${back}" aria-label="Back">← Back</button>` : ''}<h1>${esc(title)}</h1></header>`;
}
function chips(items, selected, act) {
  return `<div class="chips">${items.map((it) =>
    `<button class="chip" data-act="${act}" data-v="${esc(it.v)}" aria-pressed="${selected(it.v)}"${it.disabled ? ' disabled' : ''}>${esc(it.t)}</button>`).join('')}</div>`;
}
function modal(html) {
  closeModal();
  const bg = document.createElement('div');
  bg.className = 'modal-bg'; bg.id = 'modal';
  bg.innerHTML = `<div class="modal" role="dialog" aria-modal="true">${html}</div>`;
  bg.addEventListener('click', (e) => { if (e.target === bg) closeModal(); });
  document.body.appendChild(bg);
}
const closeModal = () => { const m = document.getElementById('modal'); if (m) m.remove(); };

/* ---------- screens ---------- */
function isIosBrowser() {
  const ua = navigator.userAgent || '';
  const ios = /iPhone|iPad|iPod/.test(ua) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
  const standalone = window.navigator.standalone === true || matchMedia('(display-mode: standalone)').matches;
  return ios && !standalone;
}

function home() {
  const results = store.get('results', []);
  const e = S.exam;
  const resume = e ? `<button class="tile resume" data-act="go" data-to="#/exam/run"><b>Resume your exam</b><span>${esc(e.title)} · ${e.qs.length} questions · ${Object.keys(e.answers).length} answered</span></button>` : '';
  const tip = isIosBrowser() && !store.get('hideTip', false)
    ? `<div class="note info small">To keep this app on your iPhone: tap <b>Share</b> in Safari, then <b>Add to Home Screen</b>. <button class="ghost" data-act="hideTip">Got it</button></div>` : '';
  return `<div class="page">
    <h1>PastQ CBT</h1>
    <p class="muted">UTME, WASSCE &amp; Post-UTME past questions. ${DATA.length.toLocaleString()} questions in ${SUBJECTS.length} subjects, available offline.</p>
    ${tip}${resume}
    <button class="tile" data-act="go" data-to="#/practice"><b>Practice</b><span>Pick a subject and get instant feedback</span></button>
    <button class="tile" data-act="go" data-to="#/exam"><b>CBT exam</b><span>Timed exam like the real UTME</span></button>
    <button class="tile" data-act="go" data-to="#/live"><b>Live test</b><span>${typeof L !== 'undefined' && L.cfg ? 'You are in "' + esc(L.cfg.title) + '". Tap to continue' : 'Join your teacher\'s test with a code'}</span></button>
    <button class="tile" data-act="go" data-to="#/results"><b>Past results</b><span>${results.length ? results.length + ' saved on this phone' : 'Nothing yet'}</span></button>
    <p class="muted small" style="margin-top:1.5rem">Questions come from MySchool, Nigerianscholar (Ulearngo), ALOC, ExamWiseNG and NAIJAPREP, used with permission or open licence. Free, no ads.</p>
  </div>`;
}

/* ----- practice ----- */
function practiceSetup() {
  const p = S.practice;
  const types = p.subject ? typesFor(p.subject) : [];
  const years = p.subject ? yearsFor(p.subject) : [];
  const n = p.subject ? practiceList().length : 0;
  return `${bar('Practice', '#/')}<div class="page">
    <h2 style="margin-top:0">Subject</h2>
    <div class="subjlist">${SUBJECTS.map((s) =>
      `<button class="chip" data-act="pSubject" data-v="${esc(s.name)}" aria-pressed="${p.subject === s.name}">${esc(s.name)}<small>${s.count} questions</small></button>`).join('')}</div>
    ${p.subject ? `<h2>Exam type</h2>
      ${chips([{ v: '', t: 'Any' }, ...types.map((t) => ({ v: t, t: typeLabel(t) }))], (v) => (v === '' ? p.types.size === 0 : p.types.has(v)), 'pType')}
      <h2>Year</h2>
      <select data-change="pYear" aria-label="Year"><option value="">Any year</option>${years.map((y) => `<option value="${esc(y)}"${p.year === y ? ' selected' : ''}>${esc(y)}</option>`).join('')}</select>` : ''}
  </div>
  <div class="footer"><p class="muted small">${p.subject ? `${n} question${n === 1 ? '' : 's'} match` : 'Pick a subject to start'}</p>
    <button class="primary full" data-act="pStart"${n ? '' : ' disabled'}>Start practice</button></div>`;
}
const practiceList = () => {
  const p = S.practice;
  return subjectQs(p.subject, p.types).filter((q) => !p.year || q.year === p.year);
};
function practiceRun() {
  const p = S.practice;
  if (!p.list.length) return `${bar('Practice', '#/practice')}<div class="page"><p>No questions yet. Go back and pick a subject.</p></div>`;
  const q = p.list[p.i];
  const picked = p.picked[p.i];
  const last = p.i === p.list.length - 1;
  const right = String(q.answer || '').trim().toLowerCase()[0];
  const opts = optionsOf(q).map((l) => {
    let cls = '';
    if (picked) cls = l === right ? 'right' : l === picked ? 'wrong' : '';
    return `<button class="opt ${cls}" data-act="pPick" data-v="${l}"${picked ? ' disabled' : ''}><span class="l">${l.toUpperCase()}</span><span>${rich(q[l])}</span></button>`;
  }).join('');
  const feedback = picked
    ? `<div class="note ${picked === right ? 'good' : 'bad'}">${picked === right ? 'Correct!' : `Wrong. The answer is ${right ? right.toUpperCase() : '?'}.`}</div>
       ${q.solution && q.solution.trim() ? `<div class="note info"><b>Explanation</b><br>${rich(q.solution)}</div>` : ''}` : '';
  return `${bar(S.practice.subject, '#/practice')}<div class="page">
    <div class="progress"><i style="width:${pct(p.i + 1, p.list.length)}%"></i></div>
    <div class="qmeta"><span>Question ${p.i + 1} of ${p.list.length}</span><span>${[typeLabel(q.type), q.year].filter(Boolean).join(' · ')}</span></div>
    ${q.section && q.section.trim() ? `<div class="section">${rich(q.section)}</div>` : ''}
    <div class="qtext">${rich(q.question)}</div>${opts}${feedback}
  </div>
  <div class="footer"><div class="row">
    <button class="grow" data-act="pPrev"${p.i === 0 ? ' disabled' : ''}>Previous</button>
    ${last ? `<button class="primary grow" data-act="pFinish">Finish</button>` : `<button class="primary grow" data-act="pNext">Next</button>`}
  </div></div>`;
}
function practiceDone() {
  const p = S.practice;
  const answered = Object.keys(p.picked).length;
  const correct = Object.entries(p.picked).filter(([i, l]) => isRight(p.list[i], l)).length;
  return `${bar('Practice finished', '#/practice')}<div class="page"><div class="card score">
    <div class="big">${correct} / ${answered}</div><p class="muted">correct out of ${answered} answered (${p.list.length} questions in this set)</p>
    <p>${pct(correct, answered)}%</p></div>
    <button class="primary full" data-act="pStart">Practise again</button>
    <button class="full" style="margin-top:.5rem" data-act="go" data-to="#/">Home</button></div>`;
}

/* ----- exam setup ----- */
function examQuotas() {
  const s = S.setup;
  return s.utme ? { [ENGLISH]: 60, ...Object.fromEntries([...s.chosen].map((n) => [n, 40])) } : Object.fromEntries([...s.chosen].map((n) => [n, s.per]));
}
function examSetup() {
  const s = S.setup;
  const minutes = (Number(s.h) || 0) * 60 + (Number(s.m) || 0);
  const timeOk = minutes >= 1 && minutes <= 120;
  const max = s.utme ? 3 : 4;
  const subjectsOk = s.utme ? s.chosen.size === 3 : s.chosen.size >= 1;
  const quotas = examQuotas();
  const total = Object.values(quotas).reduce((a, b) => a + b, 0);
  const pool = SUBJECTS.filter((x) => !(s.utme && x.name === ENGLISH));
  const summary = !subjectsOk ? (s.utme ? `Pick ${3 - s.chosen.size} more subject(s) with English` : 'Pick at least one subject')
    : !timeOk ? 'Time must be 1 minute to 2 hours'
    : `${Object.keys(quotas).length} subjects · ${total} questions · ${fmtTime(minutes * 60)}`;
  const allTypes = [...new Set(DATA.map((q) => q.type).filter(Boolean))].sort();
  return `${bar('CBT exam', '#/')}<div class="page">
    ${chips([{ v: 'utme', t: 'UTME (4 subjects)' }, { v: 'custom', t: 'Custom' }], (v) => (v === 'utme') === s.utme, 'xMode')}
    <h2 style="margin-top:0">Subjects ${s.utme ? '<span class="muted small">(English is included. Pick 3 more.)</span>' : `<span class="muted small">(up to ${max})</span>`}</h2>
    <div class="subjlist">${pool.map((x) => {
      const on = s.chosen.has(x.name);
      const off = !on && s.chosen.size >= max;
      return `<button class="chip" data-act="xSubject" data-v="${esc(x.name)}" aria-pressed="${on}"${off ? ' disabled' : ''}>${esc(x.name)}<small>${x.count} questions</small></button>`;
    }).join('')}</div>
    ${s.utme ? '' : `<h2>Questions per subject</h2>${chips([10, 20, 30, 40, 50, 60].map((n) => ({ v: String(n), t: String(n) })), (v) => Number(v) === s.per, 'xPer')}`}
    <h2>Time</h2>
    <div class="field"><input type="number" min="0" max="2" inputmode="numeric" value="${esc(s.h)}" data-input="h" aria-label="Hours"> hours
      <input type="number" min="0" max="59" inputmode="numeric" value="${esc(s.m)}" data-input="m" aria-label="Minutes"> minutes</div>
    <h2>Exam types <span class="muted small">(optional)</span></h2>
    ${chips([{ v: '', t: 'Any' }, ...allTypes.map((t) => ({ v: t, t: typeLabel(t) }))], (v) => (v === '' ? s.types.size === 0 : s.types.has(v)), 'xType')}
  </div>
  <div class="footer"><p class="muted small" id="xSummary">${esc(summary)}</p>
    <button class="primary full" id="xStart" data-act="xStart"${subjectsOk && timeOk ? '' : ' disabled'}>Start exam</button></div>`;
}
function startExam() {
  const s = S.setup;
  const quotas = examQuotas();
  const typeSet = s.types;
  const ids = [];
  const subjects = s.utme ? [ENGLISH, ...s.chosen] : [...s.chosen];
  for (const subject of subjects) {
    const want = quotas[subject];
    let pick = shuffle(subjectQs(subject, typeSet)).slice(0, want);
    if (pick.length < want && typeSet.size) { // not enough of this type: top up from the rest
      const have = new Set(pick.map((q) => q.id));
      pick = pick.concat(shuffle(subjectQs(subject)).filter((q) => !have.has(q.id)).slice(0, want - pick.length));
    }
    ids.push(...pick.map((q) => q.id));
  }
  const minutes = (Number(s.h) || 0) * 60 + (Number(s.m) || 0);
  S.exam = {
    title: s.utme ? 'UTME exam' : 'Custom exam', mode: s.utme ? 'UTME' : 'Custom', subjects, qs: ids,
    answers: {}, i: 0, total: minutes * 60, endAt: Date.now() + minutes * 60000,
  };
  S.showGrid = false;
  store.set('exam', S.exam);
  go('#/exam/run');
}

/* ----- exam run ----- */
function examRun() {
  const e = S.exam;
  if (!e) return `${bar('CBT exam', '#/')}<div class="page"><p>No exam in progress.</p><button class="primary" data-act="go" data-to="#/exam">Set up an exam</button></div>`;
  const q = BY_ID.get(e.qs[e.i]);
  const picked = e.answers[e.i];
  const opts = optionsOf(q).map((l) =>
    `<button class="opt ${picked === l ? 'sel' : ''}" data-act="xPick" data-v="${l}" aria-pressed="${picked === l}"><span class="l">${l.toUpperCase()}</span><span>${rich(q[l])}</span></button>`).join('');
  const answered = Object.keys(e.answers).length;
  const last = e.i === e.qs.length - 1;
  return `<header class="bar"><h1>${esc(q.subject)}</h1><span class="timer" id="timer">${fmtTime((e.endAt - Date.now()) / 1000)}</span>
      <button class="ghost" data-act="xGrid">Questions</button></header>
    <div class="page">
      <div class="progress"><i style="width:${pct(answered, e.qs.length)}%"></i></div>
      <div class="qmeta"><span>Question ${e.i + 1} of ${e.qs.length}</span><span>${answered} answered</span></div>
      ${q.section && q.section.trim() ? `<div class="section">${rich(q.section)}</div>` : ''}
      <div class="qtext">${rich(q.question)}</div>${opts}
    </div>
    <div class="footer"><div class="row">
      <button class="grow" data-act="xPrev"${e.i === 0 ? ' disabled' : ''}>Previous</button>
      ${last ? `<button class="primary grow" data-act="xSubmit">Submit</button>` : `<button class="primary grow" data-act="xNext">Next</button>`}
    </div></div>`;
}
function gridModal() {
  const e = S.exam;
  let html = `<h2 style="margin-top:0">Jump to a question</h2>`;
  for (const subject of e.subjects) {
    const idxs = e.qs.map((id, i) => [id, i]).filter(([id]) => BY_ID.get(id).subject === subject).map(([, i]) => i);
    if (!idxs.length) continue;
    html += `<p class="muted small" style="margin:.8rem 0 .3rem">${esc(subject)}</p><div class="grid">${idxs.map((i) =>
      `<button class="${e.answers[i] ? 'done' : ''} ${i === e.i ? 'cur' : ''}" data-act="xJump" data-v="${i}">${i + 1}</button>`).join('')}</div>`;
    }
  html += `<div class="row" style="margin-top:1rem"><button class="grow" data-act="closeModal">Close</button><button class="primary grow" data-act="xSubmit">Submit exam</button></div>`;
  modal(html);
}
function confirmSubmit() {
  const e = S.exam;
  const left = e.qs.length - Object.keys(e.answers).length;
  modal(`<h2 style="margin-top:0">Submit exam?</h2>
    <p>${left ? `You have <b>${left}</b> unanswered question${left === 1 ? '' : 's'}.` : 'You have answered every question.'} Time left: <b>${fmtTime((e.endAt - Date.now()) / 1000)}</b>.</p>
    <div class="row"><button class="grow" data-act="closeModal">Keep going</button><button class="danger grow" data-act="xFinal">Submit</button></div>`);
}
function startTimer() {
  stopTimer();
  tick = setInterval(() => {
    const e = S.exam;
    if (!e) return stopTimer();
    const left = (e.endAt - Date.now()) / 1000;
    const el = document.getElementById('timer');
    if (el) { el.textContent = fmtTime(left); el.classList.toggle('low', left <= 300); }
    if (left <= 0) { stopTimer(); submitExam(); }
  }, 1000);
}
function stopTimer() { if (tick) { clearInterval(tick); tick = null; } }

function submitExam() {
  const e = S.exam;
  if (!e) return;
  stopTimer(); closeModal();
  const review = e.qs.map((id, i) => ({ q: BY_ID.get(id), picked: e.answers[i] || null }));
  const correct = review.filter((r) => isRight(r.q, r.picked)).length;
  const used = Math.min(e.total, Math.max(0, Math.round(e.total - (e.endAt - Date.now()) / 1000)));
  const bySubject = new Map();
  for (const r of review) {
    const c = bySubject.get(r.q.subject) || { right: 0, n: 0 };
    c.n++; if (isRight(r.q, r.picked)) c.right++;
    bySubject.set(r.q.subject, c);
  }
  const breakdown = [...bySubject].map(([s, c]) => ({ subject: s, right: c.right, n: c.n }));
  S.outcome = { title: e.title, mode: e.mode, total: review.length, correct, seconds: used, timestamp: Date.now(), breakdown };
  S.review = review; S.reviewFilter = 'all';
  const all = store.get('results', []);
  all.unshift(S.outcome);
  store.set('results', all.slice(0, 100));
  S.exam = null; store.del('exam');
  go('#/result');
}

/* ----- results ----- */
function resultView() {
  const o = S.outcome;
  if (!o) return `${bar('Result', '#/')}<div class="page"><p>No result to show.</p></div>`;
  return `${bar('Your result', '#/')}<div class="page">
    <div class="card score"><div class="big">${pct(o.correct, o.total)}%</div>
      <p><b>${o.correct}</b> out of <b>${o.total}</b> correct</p><p class="muted">Time used: ${fmtTime(o.seconds)}</p></div>
    <div class="card">${o.breakdown.map((b) => `<div class="row"><span class="grow">${esc(b.subject)}</span><b>${b.right}/${b.n}</b></div>`).join('')}</div>
    ${S.review.length ? `<button class="primary full" data-act="go" data-to="#/review">Review answers</button>` : ''}
    <button class="full" style="margin-top:.5rem" data-act="go" data-to="#/">Home</button></div>`;
}
function reviewView() {
  if (!S.review.length) return `${bar('Review', '#/')}<div class="page"><p>Nothing to review.</p></div>`;
  const f = S.reviewFilter;
  const rows = S.review.map((r, i) => ({ ...r, n: i + 1 })).filter((r) =>
    f === 'all' ? true : f === 'wrong' ? r.picked && !isRight(r.q, r.picked) : !r.picked);
  return `${bar('Review answers', '#/result')}<div class="page">
    ${chips([{ v: 'all', t: 'All' }, { v: 'wrong', t: 'Wrong' }, { v: 'none', t: 'Unanswered' }], (v) => v === f, 'rFilter')}
    ${rows.length ? rows.map((r) => {
      const right = String(r.q.answer || '').trim().toLowerCase()[0];
      return `<div class="card"><div class="qmeta"><span>Q${r.n} · ${esc(r.q.subject)}</span><span>${!r.picked ? 'Not answered' : isRight(r.q, r.picked) ? '✓ Correct' : '✗ Wrong'}</span></div>
      ${r.q.section && r.q.section.trim() ? `<div class="section">${rich(r.q.section)}</div>` : ''}
      <div class="qtext" style="font-size:1rem">${rich(r.q.question)}</div>
      ${optionsOf(r.q).map((l) => `<div class="opt ${l === right ? 'right' : l === r.picked ? 'wrong' : ''}"><span class="l">${l.toUpperCase()}</span><span>${rich(r.q[l])}</span></div>`).join('')}
      ${r.q.solution && r.q.solution.trim() ? `<div class="note info"><b>Explanation</b><br>${rich(r.q.solution)}</div>` : ''}</div>`;
    }).join('') : '<p class="muted">No questions in this filter.</p>'}</div>`;
}
function pastResults() {
  const list = store.get('results', []);
  return `${bar('Past results', '#/')}<div class="page">${list.length ? list.map((o, i) =>
    `<button class="tile" data-act="openResult" data-v="${i}"><b>${esc(o.title)} · ${pct(o.correct, o.total)}%</b><span>${o.correct}/${o.total} correct · ${fmtTime(o.seconds)} · ${esc(new Date(o.timestamp).toLocaleString())}</span></button>`).join('')
    : '<p class="muted">No exams taken yet.</p>'}
    ${list.length ? `<button class="ghost" data-act="clearResults">Clear all results</button>` : ''}</div>`;
}

/* ---------- routing and events ---------- */
function route(keep) {
  const h = location.hash || '#/';
  const y = window.scrollY;
  stopTimer();
  closeModal();
  let html;
  const live = typeof liveRoute === 'function' ? liveRoute(h) : null;
  if (live !== null) { html = live; } else switch (h) {
    case '#/practice': html = practiceSetup(); break;
    case '#/practice/run': html = practiceRun(); break;
    case '#/practice/done': html = practiceDone(); break;
    case '#/exam': html = examSetup(); break;
    case '#/exam/run':
      html = examRun();
      if (S.exam) {
        if (S.exam.endAt <= Date.now()) { setTimeout(submitExam, 0); } else startTimer();
      }
      break;
    case '#/result': html = resultView(); break;
    case '#/review': html = reviewView(); break;
    case '#/results': html = pastResults(); break;
    default: html = home();
  }
  $app.innerHTML = html;
  window.scrollTo(0, keep === true ? y : 0);
}
function refreshExamSetupFooter() { // update summary/button without losing focus in the number inputs
  const tmp = document.createElement('div');
  tmp.innerHTML = examSetup();
  const sum = tmp.querySelector('#xSummary'), btn = tmp.querySelector('#xStart');
  if (sum) document.getElementById('xSummary').textContent = sum.textContent;
  if (btn) document.getElementById('xStart').disabled = btn.disabled;
}
function toggle(set, v) { set.has(v) ? set.delete(v) : set.add(v); }

const ACTIONS = {
  go: (el) => go(el.dataset.to),
  closeModal,
  hideTip: () => { store.set('hideTip', true); route(true); },
  /* practice */
  pSubject: (el) => { Object.assign(S.practice, { subject: el.dataset.v, types: new Set(), year: '' }); route(true); },
  pType: (el) => { const v = el.dataset.v; if (v === '') S.practice.types.clear(); else toggle(S.practice.types, v); route(true); },
  pStart: () => { const p = S.practice; p.list = shuffle(practiceList()); p.i = 0; p.picked = {}; go('#/practice/run'); },
  pPick: (el) => { const p = S.practice; if (!p.picked[p.i]) p.picked[p.i] = el.dataset.v; route(true); },
  pPrev: () => { S.practice.i = Math.max(0, S.practice.i - 1); route(); },
  pNext: () => { S.practice.i = Math.min(S.practice.list.length - 1, S.practice.i + 1); route(); },
  pFinish: () => go('#/practice/done'),
  /* exam setup */
  xMode: (el) => { const u = el.dataset.v === 'utme'; Object.assign(S.setup, { utme: u, chosen: new Set(), h: u ? 2 : 0, m: u ? 0 : 40 }); route(true); },
  xSubject: (el) => { toggle(S.setup.chosen, el.dataset.v); route(true); },
  xPer: (el) => { S.setup.per = Number(el.dataset.v); route(true); },
  xType: (el) => { const v = el.dataset.v; if (v === '') S.setup.types.clear(); else toggle(S.setup.types, v); route(true); },
  xStart: () => {
    if (S.exam && !confirm('You have an exam in progress. Starting a new one will discard it. Continue?')) return;
    startExam();
  },
  /* exam run */
  xPick: (el) => { const e = S.exam; e.answers[e.i] = el.dataset.v; store.set('exam', e); keepTimerRender(true); },
  xPrev: () => { S.exam.i = Math.max(0, S.exam.i - 1); store.set('exam', S.exam); keepTimerRender(); },
  xNext: () => { S.exam.i = Math.min(S.exam.qs.length - 1, S.exam.i + 1); store.set('exam', S.exam); keepTimerRender(); },
  xGrid: gridModal,
  xJump: (el) => { S.exam.i = Number(el.dataset.v); store.set('exam', S.exam); closeModal(); keepTimerRender(); },
  xSubmit: confirmSubmit,
  xFinal: submitExam,
  /* results */
  rFilter: (el) => { S.reviewFilter = el.dataset.v; route(true); },
  openResult: (el) => {
    const o = store.get('results', [])[Number(el.dataset.v)];
    if (!o) return;
    S.outcome = o; S.review = []; go('#/result');
  },
  clearResults: () => { if (confirm('Delete all saved results from this phone?')) { store.del('results'); route(); } },
};
function keepTimerRender(keep) { route(keep); } // route() restarts the timer for the exam screen

document.addEventListener('click', (ev) => {
  const el = ev.target.closest('[data-act]');
  if (!el || el.disabled) return;
  const fn = ACTIONS[el.dataset.act] || (typeof LIVE_ACTIONS !== 'undefined' ? LIVE_ACTIONS[el.dataset.act] : null);
  if (fn) fn(el);
});
document.addEventListener('change', (ev) => {
  const el = ev.target;
  if (el.dataset && el.dataset.change === 'pYear') { S.practice.year = el.value; route(true); }
});
document.addEventListener('input', (ev) => {
  const el = ev.target;
  if (el.dataset && el.dataset.input) {
    S.setup[el.dataset.input] = el.value;
    refreshExamSetupFooter();
  }
});
window.addEventListener('hashchange', () => route());
document.addEventListener('visibilitychange', () => { // the clock runs on real time, so just refresh when coming back
  if (!document.hidden && S.exam && location.hash === '#/exam/run') route(true);
});

/* ---------- start ---------- */
(async function main() {
  try {
    await loadData();
    route();
  } catch (err) {
    $app.innerHTML = `<div class="page"><h1>PastQ CBT</h1><div class="note bad">${esc(err.message)}</div>
      <p class="muted">Check your internet connection and open the app again. After one successful load it works offline.</p>
      <button class="primary" onclick="location.reload()">Try again</button></div>`;
  }
  if ('serviceWorker' in navigator && location.protocol.startsWith('http')) {
    navigator.serviceWorker.register('sw.js').catch(() => {});
  }
})();
