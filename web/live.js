'use strict';
/* Live tests for the web version: join with a code, take the test, see results; plus the admin essentials.
   Talks to the same Supabase function (pastq-live) as the Android app. Uses helpers from app.js. */

const LIVE_URL = 'https://wyzswbklytklzbcmihsh.supabase.co/functions/v1/pastq-live';
const LIVE_KEY = 'sb_publishable_E21JIx3tbNTX9K3bhR6Ieg_fZRL1xqj'; // public, read-only key (same as the Android app)

const L = {
  cfg: store.get('live', null),            // {code, token, name, title} once joined
  answers: store.get('liveAnswers', {}),   // position -> letter
  admin: null,                              // admin token, kept in memory only
  join: { code: '', name: store.get('liveName', ''), busy: false, error: '' },
  p: null,                                  // player screen state
  a: { sessions: [], error: '', busy: false, poll: null, key: '', form: null, login: { login: '', password: '' } },
};

/* ---------- server calls ---------- */
async function liveCall(body, admin, keepalive) {
  const headers = { 'Content-Type': 'application/json', apikey: LIVE_KEY, Authorization: 'Bearer ' + LIVE_KEY };
  if (admin && L.admin) headers['x-admin-token'] = L.admin;
  let res;
  try {
    res = await fetch(LIVE_URL, { method: 'POST', headers, body: JSON.stringify(body), keepalive: !!keepalive });
  } catch (e) { throw new Error("Can't reach the server. Check your internet connection."); }
  let data = null;
  try { data = await res.json(); } catch (e) { /* not JSON */ }
  if (!res.ok) {
    if (admin && res.status === 401) L.admin = null;
    throw new Error((data && data.error) || 'Server error ' + res.status + '.');
  }
  return data || {};
}
const saveAnswers = () => store.set('liveAnswers', L.answers);
function deviceId() {
  let id = store.get('liveDevice', '');
  if (!id) {
    id = (crypto.randomUUID && crypto.randomUUID()) || 'd' + Date.now().toString(36) + Math.random().toString(36).slice(2);
    store.set('liveDevice', id);
  }
  return id;
}
function forgetLive() {
  // The device id stays, so forgetting a session cannot be used to join a strict test twice.
  store.del('live'); store.del('liveAnswers');
  L.cfg = null; L.answers = {};
}
const cleanCode = (s) => String(s || '').toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 12);
const shareLink = (code) => location.origin + location.pathname + '#/live?code=' + code;
function countdownLabel(secs) {
  const s = Math.max(0, Math.round(secs));
  const d = Math.floor(s / 86400), h = Math.floor((s % 86400) / 3600), m = Math.floor((s % 3600) / 60);
  if (d > 0) return `${d}d ${h}h ${m}m`;
  return fmtTime(s);
}
function pictures(image) {
  return String(image || '').split(/\s+/).filter((u) => /^https:\/\//i.test(u))
    .map((u) => `<img class="qimg" src="${esc(u)}" alt="Question picture" loading="lazy">`).join('');
}

/* ---------- player state ---------- */
function freshPlayer() {
  return {
    state: null, results: null, questions: [], deadline: 0, startsAt: 0, submitted: false, submitting: false,
    leftApp: false, lockSeen: false, requestSent: false, reqSeen: false, closedOut: false, rulesOk: false,
    requestError: '', error: '', index: 0, poll: null, tick: null, running: false, key: '', resKey: '',
  };
}
L.p = freshPlayer();

function startPlay() {
  stopPlay();
  L.p = freshPlayer();
  L.p.running = true;
  pollOnce(L.p);
  L.p.tick = setInterval(tickPlay, 500);
}
function stopPlay() {
  const p = L.p;
  p.running = false;
  if (p.poll) clearTimeout(p.poll);
  if (p.tick) clearInterval(p.tick);
  p.poll = p.tick = null;
}
const isTesting = (p) => {
  const s = p.state;
  return !!s && s.status === 'running' && !p.submitted && p.questions.length > 0 && !p.leftApp && !s.locked && s.join_status === 'approved';
};
const remainingSecs = (p) => Math.max(0, Math.round((p.deadline - Date.now()) / 1000));

async function pollOnce(p) {
  if (!L.cfg || !p.running || p !== L.p || p.busy) return;
  p.busy = true;
  try {
    const s = await liveCall({ action: 'state', code: L.cfg.code, token: L.cfg.token });
    if (!p.running || p !== L.p) return;
    p.error = ''; p.state = s;
    p.startsAt = s.status === 'waiting' && s.scheduled_start_at ? Date.now() + (s.starts_in_seconds || 0) * 1000 : 0;
    if (s.strict_leave && s.left_app) p.closedOut = true;
    if (s.rejoin_requested) p.reqSeen = true; else if (p.reqSeen) { p.reqSeen = false; p.requestSent = false; }
    if (p.closedOut && s.strict_leave && !s.left_app && !s.submitted && s.status === 'running') {
      // The admin let this player back in.
      Object.assign(p, { closedOut: false, submitted: false, leftApp: false, lockSeen: false, requestSent: false, reqSeen: false });
    }
    if (s.submitted) p.submitted = true;
    if (s.locked) p.lockSeen = true;
    if (p.leftApp && s.status === 'running' && !s.submitted && s.join_status === 'approved' && !s.locked) {
      if (p.lockSeen) { p.leftApp = false; p.lockSeen = false; p.requestSent = false; } else sendLeave();
    }
    if (s.status === 'running' && !p.submitted && !s.locked && s.join_status === 'approved') {
      if (s.questions && s.questions.length) {
        p.questions = s.questions;
        restoreAnswers(s.saved_answers || {});
      }
      p.deadline = Date.now() + (s.seconds_left || 0) * 1000;
    }
    if (s.join_status === 'approved' && (s.status === 'ended' || (s.submitted && s.score_visibility === 'immediate'))) {
      const r = await liveCall({ action: 'results', code: L.cfg.code, token: L.cfg.token });
      if (!p.running || p !== L.p) return;
      p.results = r;
      if (r.ready && r.final) { p.running = false; paintPlay(); return; } // nothing more will change
    }
  } catch (e) {
    p.error = e.message;
  } finally {
    p.busy = false;
  }
  const s = p.state;
  const waitingOnAdmin = (s && s.join_status === 'pending') || (s && s.locked) || p.leftApp || (s && s.left_app);
  const startingSoon = p.startsAt > 0 && p.startsAt - Date.now() < 8000;
  const delay = startingSoon ? 1000 : waitingOnAdmin ? 3000 : p.results && p.results.ready ? 8000
    : s && s.status === 'running' && !p.submitted ? 7000 : 2500;
  paintPlay();
  if (p.running && p === L.p) p.poll = setTimeout(() => pollOnce(p), delay);
}
function restoreAnswers(saved) {
  const back = {};
  for (const [k, v] of Object.entries(saved)) if (k && v) back[k] = String(v)[0];
  if (!Object.keys(back).length) return;
  L.answers = { ...back, ...L.answers };
  saveAnswers();
}
function answersPayload() { return { ...L.answers }; }
function sendLeave() {
  if (!L.cfg) return Promise.resolve();
  const send = () => liveCall({ action: 'leave', code: L.cfg.code, token: L.cfg.token, answers: answersPayload() }, false, true);
  return send().catch(() => new Promise((resolve) => {
    setTimeout(() => { if (L.cfg && L.p.leftApp) send().catch(() => {}).then(resolve); else resolve(); }, 1500);
  }));
}
/** Check in with the server right away instead of waiting for the next scheduled check. */
function pollSoon(p) {
  if (!p.running || p !== L.p) return;
  if (p.poll) clearTimeout(p.poll);
  p.poll = null;
  pollOnce(p);
}
async function submitNow() {
  const p = L.p;
  if (p.submitting || p.submitted || !L.cfg) return;
  closeModal();
  p.submitting = true; paintPlay(true);
  try {
    await liveCall({ action: 'submit', code: L.cfg.code, token: L.cfg.token, answers: answersPayload() });
    p.submitted = true;
    try {
      const r = await liveCall({ action: 'results', code: L.cfg.code, token: L.cfg.token });
      if (r.ready) p.results = r;
    } catch (e) { /* shown on the next refresh */ }
  } catch (e) {
    if (/already|closed/i.test(e.message)) p.submitted = true; else p.error = e.message;
  }
  p.submitting = false;
  paintPlay(true);
}
function tickPlay() {
  const p = L.p;
  if (!p.running || !location.hash.startsWith('#/live/play')) return;
  const s = p.state;
  const t = document.getElementById('ltimer');
  if (t) { const r = remainingSecs(p); t.textContent = fmtTime(r); t.classList.toggle('low', r < 60); }
  const c = document.getElementById('lcount');
  if (c) c.textContent = countdownLabel((p.startsAt - Date.now()) / 1000);
  if (isTesting(p) && p.deadline > 0 && remainingSecs(p) <= 0) submitNow();
  if (s && s.status === 'ended' && !p.submitted && p.questions.length) submitNow();
}

/* leaving the page during a test closes it for this player, like leaving the Android app */
document.addEventListener('visibilitychange', () => {
  const p = L.p;
  if (document.hidden && p.running && isTesting(p) && location.hash.startsWith('#/live/play')) {
    p.leftApp = true; p.lockSeen = false;
    sendLeave().then(() => pollSoon(p));
  } else if (!document.hidden && p.running && p.leftApp) {
    paintPlay(true);
  }
});

/* ---------- player screens ---------- */
function screenOf(p) {
  const s = p.state;
  if (!L.cfg) return 'none';
  if (p.results && p.results.ready) return 'results';
  if (s && s.join_status === 'pending') return 'pending';
  if (s && s.join_status === 'denied') return 'denied';
  if (s && s.status === 'running' && ((!p.submitted && (p.leftApp || s.locked)) || (s.strict_leave && (s.left_app || p.closedOut)))) return 'left';
  if (!s) return 'loading';
  if (isTesting(p) && s.strict_leave && !p.rulesOk) return 'rules';
  if (isTesting(p)) return 'test';
  return 'wait';
}
function viewKey(p, screen) {
  const s = p.state || {};
  switch (screen) {
    case 'results': return 'results|' + JSON.stringify(p.results);
    case 'left': return 'left|' + (p.requestSent || s.rejoin_requested) + '|' + p.requestError;
    case 'wait': return `wait|${s.status}|${p.startsAt > 0}|${s.players}|${p.submitted}|${p.submitting}|${p.error}`;
    case 'loading': return 'loading|' + p.error;
    case 'test': return 'test|' + p.error + '|' + p.submitting;
    default: return screen;
  }
}
function paintPlay(force) {
  if (!location.hash.startsWith('#/live/play')) return;
  const p = L.p, screen = screenOf(p), key = viewKey(p, screen);
  if (!force && key === p.key) return;
  const changedScreen = key.split('|')[0] !== p.key.split('|')[0];
  p.key = key;
  const y = window.scrollY;
  $app.innerHTML = playHtml(p, screen);
  window.scrollTo(0, changedScreen ? 0 : y);
}
const centered = (inner) => `<div class="page center">${inner}</div>`;
const spinner = '<div class="spinner" role="status" aria-label="Waiting"></div>';
const leaveBtn = '<button data-act="lLeave">Leave this session</button>';

function strictRules(returnAllowed) {
  return `<div class="note bad"><b>Strict test: read before you start</b>
    <p>• Stay on this page. Leaving it (another app or tab, locking the phone) ends your test at once.</p>
    <p>• Your answers so far are graded and your result is marked "left app" for the admin to see.</p>
    <p>• Being offline or switched off for about a minute counts as leaving.</p>
    <p>• This phone can join this test only once. Joining again under another name is blocked.</p>
    <p>${returnAllowed ? '• If a network or phone problem cuts you off, you can ask the admin to let you back in. It only works if the admin is online and agrees.' : '• There is no way back in once your test has ended this way.'}</p></div>`;
}

function playHtml(p, screen) {
  const s = p.state || {};
  const title = esc(s.title || (L.cfg && L.cfg.title) || 'Live test');
  const back = bar(s.title || 'Live test', '#/live');
  switch (screen) {
    case 'none':
      return `${bar('Live test', '#/')}<div class="page"><p>You are not in a live session.</p><button class="primary" data-act="go" data-to="#/live">Join a session</button></div>`;
    case 'results': return resultsHtml(p.results);
    case 'pending':
      return back + centered(`${spinner}<h2>Waiting for the admin</h2><p>The test has already started, so your request to join was sent to the admin. Keep this page open. The test opens as soon as they allow you in.</p><button data-act="lLeave">Cancel my request</button>`);
    case 'denied':
      return back + centered(`<h2>Request declined</h2><p>The admin did not let you into this test.</p><button class="primary" data-act="lLeave">Leave</button>`);
    case 'left': {
      const strict = s.strict_leave;
      const canAsk = !(strict && !s.strict_return);
      const sent = p.requestSent || s.rejoin_requested;
      return back + centered(`<h2>${strict ? 'Your test has ended' : 'You left the test'}</h2>
        <p>${strict
          ? (s.strict_return ? 'This test is strict: leaving the page closes it for you and your answers so far are graded. If you were cut off by a network or phone problem, ask the admin to let you back in. It only works while the admin is online and agrees; otherwise your result stands as it is.'
            : 'This test is strict: leaving the page closes it for you, and you cannot come back to it. Your answers so far are graded.')
          : 'Leaving the page during a live test closes it for you. Your answers so far are saved. Ask the admin to let you back in; if they allow it you continue where you stopped, and the clock does not stop meanwhile.'}</p>
        ${!canAsk ? '' : sent ? `${spinner}<p><b>Request sent. Waiting for the admin…</b></p>` : '<button class="primary" data-act="lRejoin">Ask the admin to let me back in</button>'}
        ${p.requestError ? `<p class="note bad">${esc(p.requestError)}</p>` : ''}`);
    }
    case 'loading':
      return `${bar('Live test', '#/live')}` + centered(p.error
        ? `<div class="note bad">${esc(p.error)}</div><p class="muted small">Trying again…</p>${leaveBtn}` : spinner);
    case 'rules':
      return back + centered(`${strictRules(s.strict_return)}<button class="primary" data-act="lRules">I understand. Show me the questions</button>`);
    case 'test': return testHtml(p);
    default: { // wait
      let inner;
      if (s.status === 'waiting' && p.startsAt > 0) {
        inner = `<p>Starts in</p><div class="bigtime" id="lcount">${countdownLabel((p.startsAt - Date.now()) / 1000)}</div>
          ${s.scheduled_start_at ? `<p>Scheduled for ${esc(new Date(s.scheduled_start_at).toLocaleString())}</p>` : ''}
          <p>${s.players} joined · ${s.minutes} minutes · you are ${esc(s.name)}</p>${s.strict_leave ? strictRules(s.strict_return) : ''}
          <p class="muted small">${s.timer_mode === 'personal' ? `You get the full ${s.minutes} minutes from the moment you begin.` : 'The test opens by itself at the start time and the clock runs for everyone. Keep this page open.'}</p>`;
      } else if (s.status === 'waiting') {
        inner = `${spinner}<h2>Waiting for the admin to start</h2><p>${s.players} joined · ${s.minutes} minutes · you are ${esc(s.name)}</p>
          ${s.strict_leave ? strictRules(s.strict_return) : ''}
          <p class="muted small">${s.timer_mode === 'personal' ? `You get the full ${s.minutes} minutes from the moment you start, even if you join late.` : 'The clock starts for everyone when the admin starts the test.'}</p>`;
      } else if (s.status === 'running' && p.submitted) {
        inner = `<h2>Answers submitted</h2><p>Your corrections and position appear when the session ends (<span id="ltimer">${fmtTime(remainingSecs(p))}</span> left, or when the admin ends it).</p>`;
      } else if (s.status === 'ended') {
        inner = `${spinner}<p>Session ended. Getting your results…</p>`;
      } else inner = spinner;
      return back + centered(`${inner}${p.error ? `<p class="note bad">${esc(p.error)}</p>` : ''}<p style="margin-top:1rem">${leaveBtn}</p>`);
    }
  }
}
function testHtml(p) {
  const qs = p.questions;
  p.index = Math.min(Math.max(0, p.index), qs.length - 1);
  const q = qs[p.index];
  const picked = L.answers[q.pos];
  const answered = qs.filter((x) => L.answers[x.pos]).length;
  const opts = ['a', 'b', 'c', 'd', 'e'].filter((l) => String(q[l] || '').trim()).map((l) =>
    `<button class="opt ${picked === l ? 'sel' : ''}" data-act="lPick" data-v="${l}" aria-pressed="${picked === l}"><span class="l">${l.toUpperCase()}</span><span>${rich(q[l])}</span></button>`).join('');
  const last = p.index === qs.length - 1;
  const title = (p.state && p.state.title) || 'Live test';
  return `<header class="bar"><h1>${esc(title)}</h1><span class="timer ${remainingSecs(p) < 60 ? 'low' : ''}" id="ltimer">${fmtTime(remainingSecs(p))}</span></header>
    <div class="page">
      <div class="qmeta"><span>${answered}/${qs.length} answered</span><span>Question ${p.index + 1} of ${qs.length}</span></div>
      <div class="qrow">${qs.map((x, i) => `<button class="${L.answers[x.pos] ? 'done' : ''} ${i === p.index ? 'cur' : ''}" data-act="lJump" data-v="${i}">${i + 1}</button>`).join('')}</div>
      ${q.section && q.section.trim() ? `<div class="section">${rich(q.section)}</div>` : ''}
      ${q.subject ? `<p class="muted small">${esc(q.subject)}</p>` : ''}
      <div class="qtext">${rich(q.question)}</div>${pictures(q.image)}${opts}
      ${p.error ? `<p class="note bad">${esc(p.error)}</p>` : ''}
    </div>
    <div class="footer"><div class="row">
      <button class="grow" data-act="lPrev"${p.index === 0 ? ' disabled' : ''}>Previous</button>
      ${last ? `<button class="primary grow" data-act="lAsk"${p.submitting ? ' disabled' : ''}>${p.submitting ? 'Sending…' : 'Submit'}</button>` : '<button class="primary grow" data-act="lNext">Next</button>'}
    </div>${last ? '' : `<button class="ghost" data-act="lAsk"${p.submitting ? ' disabled' : ''}>Submit now</button>`}</div>`;
}

function reviewCard(item) {
  const correct = String(item.answer || '').trim().toLowerCase()[0];
  const picked = item.picked ? String(item.picked).toLowerCase()[0] : null;
  return `<div class="card"><div class="qmeta"><span>Q${item.pos}${item.subject ? ' · ' + esc(item.subject) : ''}</span></div>
    ${item.section && item.section.trim() ? `<div class="section">${rich(item.section)}</div>` : ''}
    <div class="qtext" style="font-size:1rem">${rich(item.question)}</div>${pictures(item.image)}
    ${['a', 'b', 'c', 'd', 'e'].filter((l) => String(item[l] || '').trim()).map((l) => {
      const tag = l === correct && l === picked ? ' ✓ your answer, correct' : l === correct ? ' ✓ correct answer' : l === picked ? ' ✗ your answer' : '';
      return `<div class="opt ${l === correct ? 'right' : l === picked ? 'wrong' : ''}"><span class="l">${l.toUpperCase()}</span><span>${rich(item[l])}<b>${tag}</b></span></div>`;
    }).join('')}
    ${!picked ? '<p class="muted small">You did not answer this question.</p>' : ''}
    ${item.explanation && item.explanation.trim() ? `<div class="note info"><b>Explanation</b><br>${rich(item.explanation)}</div>` : ''}</div>`;
}
function boardRow(r) {
  return `<div class="card row" style="${r.you ? 'border-color:var(--brand);background:var(--brand-soft)' : ''}">
    <b>#${r.rank}</b><span class="grow">${esc(r.name)}${r.you ? ' (you)' : ''}${r.left ? ' (left app)' : ''}<br><span class="muted small">${fmtTime(r.seconds)}</span></span><b>${r.correct}/${r.total}</b></div>`;
}
function resultsHtml(r) {
  const pc = r.total ? (r.correct * 100 / r.total).toFixed(1) : '0.0';
  return `${bar(r.title || 'Live test results', '#/live')}<div class="page">
    <div class="card score"><p><b>${esc(r.name)}</b></p>${r.submitted
      ? `<div class="big">${r.correct} / ${r.total}</div><p>${pc}%</p>
         <p><b>${r.rank != null ? `Position ${r.rank} of ${r.participants} who finished${r.final ? '' : ' so far'}` : 'Not ranked'}</b></p>
         ${!r.final ? '<p class="muted small">Others are still taking the test, so your position can change. The corrections appear when the test ends; open this page again then.</p>' : ''}
         <p class="muted">Time used: ${fmtTime(r.seconds)}</p>`
      : '<p><b>You did not submit answers before the session ended.</b></p>'}</div>
    ${r.board && r.board.length ? `<h2>Scoreboard</h2>${r.board.map(boardRow).join('')}` : ''}
    ${r.review && r.review.length ? `<h2>Corrections</h2>${r.review.map(reviewCard).join('')}` : ''}
    <button class="primary full" style="margin-top:1rem" data-act="${r.final ? 'lLeaveHome' : 'go'}" data-to="#/">${r.final ? 'Done' : 'Close for now'}</button></div>`;
}

/* ---------- join screen ---------- */
function joinHtml() {
  const q = new URLSearchParams((location.hash.split('?')[1]) || '');
  if (!L.join.code && q.get('code')) L.join.code = cleanCode(q.get('code'));
  const c = L.cfg;
  const ok = !L.join.busy && L.join.code.length >= 4 && L.join.name.trim().length >= 2;
  return `${bar('Live test', '#/')}<div class="page">
    ${c ? `<div class="note info"><b>You are in "${esc(c.title)}" as ${esc(c.name)}</b><div class="row" style="margin-top:.5rem">
      <button class="primary" data-act="go" data-to="#/live/play">Continue</button><button data-act="lLeave">Leave</button></div></div>` : ''}
    <p>Enter the code your teacher gave you, and a name for the scoreboard. Nobody else in the session can use the same name.</p>
    <div class="field"><label for="jcode" class="grow">Session code<br>
      <input id="jcode" class="text" data-lj="code" value="${esc(L.join.code)}" maxlength="12" autocapitalize="characters" autocomplete="off" spellcheck="false"></label></div>
    <div class="field"><label for="jname" class="grow">Your name<br>
      <input id="jname" class="text" data-lj="name" value="${esc(L.join.name)}" maxlength="40" autocomplete="name"></label></div>
    <p class="muted small">If the test has already started, your request goes to the admin and you can begin once they allow you in.</p>
    ${L.join.error ? `<p class="note bad">${esc(L.join.error)}</p>` : ''}
  </div>
  <div class="footer"><button class="primary full" id="jgo" data-act="lJoin"${ok ? '' : ' disabled'}>${L.join.busy ? 'Joining…' : 'Join session'}</button>
    <button class="ghost" data-act="go" data-to="#/live/admin">I'm the admin</button></div>`;
}
async function doJoin() {
  const j = L.join;
  j.busy = true; j.error = ''; route(true);
  try {
    const r = await liveCall({ action: 'join', code: j.code.trim(), name: j.name.trim(), auto_name: false, device_id: deviceId() });
    L.answers = {}; saveAnswers();
    L.cfg = { code: r.code, token: r.token, name: r.name, title: r.title };
    store.set('live', L.cfg); store.set('liveName', j.name.trim());
    j.busy = false; j.code = '';
    go('#/live/play');
  } catch (e) {
    j.busy = false; j.error = e.message; route(true);
  }
}

/* ---------- admin ---------- */
function stopAdminPoll() { if (L.a.poll) { clearInterval(L.a.poll); L.a.poll = null; } }
async function loadSessions(quiet) {
  if (!L.admin) return;
  try {
    const r = await liveCall({ action: 'admin_list' }, true);
    L.a.error = '';
    const key = JSON.stringify(r.sessions || []);
    const changed = key !== L.a.key;
    L.a.sessions = r.sessions || []; L.a.key = key;
    if ((changed || !quiet) && location.hash.split('?')[0] === '#/live/admin' && !document.getElementById('modal')) route(true);
  } catch (e) {
    L.a.error = e.message;
    if (!L.admin) route(); else if (!quiet) route(true);
  }
}
function adminLoginHtml() {
  const f = L.a.login;
  return `${bar('Admin sign-in', '#/live')}<div class="page">
    <p>Sign in to create and manage live tests.</p>
    <div class="field"><label class="grow">Login<br><input class="text" data-la="login" value="${esc(f.login)}" autocomplete="username" autocapitalize="none"></label></div>
    <div class="field"><label class="grow">Password<br><input class="text" type="password" data-la="password" value="${esc(f.password)}" autocomplete="current-password"></label></div>
    ${L.a.error ? `<p class="note bad">${esc(L.a.error)}</p>` : ''}
    <button class="primary full" data-act="aLogin"${L.a.busy ? ' disabled' : ''}>${L.a.busy ? 'Signing in…' : 'Sign in'}</button></div>`;
}
const STATUS = { waiting: 'Waiting', running: 'Running', ended: 'Ended' };
function sessionCard(s) {
  const when = s.status === 'waiting' && s.scheduled_start_at ? `<br><span class="muted small">Starts ${esc(new Date(s.scheduled_start_at).toLocaleString())}</span>` : '';
  return `<div class="card"><div class="row"><b class="grow">${esc(s.title)}</b><span class="chip" style="min-height:32px;padding:.2rem .7rem">${esc(STATUS[s.status] || s.status)}</span></div>
    <p><b style="font-size:1.4rem;letter-spacing:.1em">${esc(s.code)}</b> <button class="ghost" data-act="aCopy" data-v="${esc(s.code)}">Copy link</button></p>
    <p class="muted small">${s.players} joined · ${s.submitted} submitted · ${s.questions} questions · ${s.minutes} min${s.strict_leave ? ' · strict' : ''}${when}</p>
    ${s.requests > 0 ? `<p class="note warn">${s.requests} waiting to be let in</p>` : ''}
    <div class="row wrap">
      ${s.status === 'waiting' ? `<button class="primary" data-act="aStart" data-v="${esc(s.id)}">Start now</button><button data-act="aSchedule" data-v="${esc(s.id)}">Schedule</button>` : ''}
      ${s.status === 'running' ? `<button class="danger" data-act="aEndAsk" data-v="${esc(s.id)}">End test</button>` : ''}
      ${s.requests > 0 ? `<button data-act="aRequests" data-v="${esc(s.id)}">Requests (${s.requests})</button>` : ''}
      <button data-act="aResults" data-v="${esc(s.id)}">Results</button>
      <button class="ghost" data-act="aDeleteAsk" data-v="${esc(s.id)}">Delete</button></div></div>`;
}
function adminListHtml() {
  return `${bar('Live test admin', '#/live')}<div class="page">
    <div class="row"><button class="primary grow" data-act="go" data-to="#/live/admin/new">New live test</button><button data-act="aOut">Sign out</button></div>
    ${L.a.error ? `<p class="note bad">${esc(L.a.error)}</p>` : ''}
    ${L.a.sessions.length ? L.a.sessions.map(sessionCard).join('') : '<p class="muted">No sessions yet.</p>'}</div>`;
}
function newFormInit() {
  return { title: '', minutes: 40, picks: {}, timer_mode: 'shared', join_window_min: 10, score_visibility: 'after_end',
    code: '', auto_end: false, auto_approve_late: false, strict_leave: false, strict_return: false, announce: true, start_at: '', busy: false, error: '', done: null };
}
function adminNewHtml() {
  const f = L.a.form || (L.a.form = newFormInit());
  if (f.done) {
    const link = shareLink(f.done.code);
    return `${bar('Live test created', '#/live/admin')}<div class="page"><div class="card score"><p>Join code</p><div class="big">${esc(f.done.code)}</div>
      <p>${esc(f.done.title)} · ${f.done.questions} questions</p><p class="muted small" style="word-break:break-all">${esc(link)}</p></div>
      <button class="primary full" data-act="aCopy" data-v="${esc(f.done.code)}">Copy student link</button>
      <button class="full" style="margin-top:.5rem" data-act="aFormDone">Back to sessions</button></div>`;
  }
  const total = Object.values(f.picks).reduce((a, b) => a + (Number(b) || 0), 0);
  const n = Object.values(f.picks).filter((v) => Number(v) > 0).length;
  const cb = (k, label) => `<label class="field"><input type="checkbox" data-lc="${k}"${f[k] ? ' checked' : ''}> <span>${label}</span></label>`;
  return `${bar('New live test', '#/live/admin')}<div class="page">
    <div class="field"><label class="grow">Title<br><input class="text" data-lf="title" value="${esc(f.title)}" maxlength="80" placeholder="Live test"></label></div>
    <div class="field"><label>Minutes (1 to 120)<br><input class="text" type="number" inputmode="numeric" data-lf="minutes" value="${esc(f.minutes)}" min="1" max="120" style="width:7rem"></label></div>
    <h2>Questions from the bank <span class="muted small">(${total} chosen, up to 8 subjects, 200 total)</span></h2>
    <div class="subjlist">${SUBJECTS.map((s) => `<label class="card" style="margin:0;padding:.6rem">${esc(s.name)}<br>
      <input class="text" type="number" inputmode="numeric" min="0" max="100" data-lp="${esc(s.name)}" value="${esc(f.picks[s.name] || '')}" placeholder="0" style="width:5rem"></label>`).join('')}</div>
    <p class="muted small">Typed or imported questions are not available in the web version yet. Use the Android app for those.</p>
    <h2>Timer</h2>
    ${chips([{ v: 'shared', t: 'One clock for everyone' }, { v: 'personal', t: 'Each player gets full time' }], (v) => f.timer_mode === v, 'aTimer')}
    ${f.timer_mode === 'personal' ? `<div class="field"><label>Players may join up to (minutes after start)<br><input class="text" type="number" inputmode="numeric" data-lf="join_window_min" value="${esc(f.join_window_min)}" min="0" max="60" style="width:6rem"></label></div>` : ''}
    <h2>Scores</h2>
    ${chips([{ v: 'after_end', t: 'When the test ends' }, { v: 'immediate', t: 'Right after each player finishes' }], (v) => f.score_visibility === v, 'aScore')}
    <h2>Options</h2>
    <div class="field"><label class="grow">Custom join code (optional, 4 to 12 letters or digits)<br><input class="text" data-lf="code" value="${esc(f.code)}" maxlength="12" autocapitalize="characters"></label></div>
    <div class="field"><label class="grow">Scheduled start (optional)<br><input class="text" type="datetime-local" data-lf="start_at" value="${esc(f.start_at)}"></label></div>
    ${cb('auto_end', 'End automatically when everyone has submitted')}
    ${cb('auto_approve_late', 'Let late joiners in without asking me')}
    ${cb('strict_leave', 'Strict: a player who leaves the page is closed out')}
    ${f.strict_leave ? cb('strict_return', 'Closed-out players may ask to come back') : ''}
    ${cb('announce', 'Send a notice to phones with the Android app')}
    ${f.error ? `<p class="note bad">${esc(f.error)}</p>` : ''}
  </div>
  <div class="footer"><p class="muted small">${n} subject${n === 1 ? '' : 's'} · ${total} questions · ${esc(f.minutes)} min</p>
    <button class="primary full" data-act="aCreate"${f.busy ? ' disabled' : ''}>${f.busy ? 'Creating…' : 'Create live test'}</button></div>`;
}
async function createSession() {
  const f = L.a.form;
  f.error = '';
  const picks = Object.entries(f.picks).map(([subject, c]) => ({ subject, count: Math.floor(Number(c)) })).filter((x) => x.count > 0);
  const total = picks.reduce((a, b) => a + b.count, 0);
  const mins = Math.floor(Number(f.minutes));
  if (!picks.length) f.error = 'Pick at least one subject and a number of questions.';
  else if (picks.length > 8) f.error = 'Use 8 subjects at most.';
  else if (picks.some((p) => p.count > 100)) f.error = 'Use 100 questions or fewer per subject.';
  else if (total > 200) f.error = 'A test holds 200 questions at most.';
  else if (!(mins >= 1 && mins <= 120)) f.error = 'Minutes must be 1 to 120.';
  else if (f.start_at && isNaN(new Date(f.start_at).getTime())) f.error = 'That start time is not valid.';
  if (f.error) return route(true);
  f.busy = true; route(true);
  try {
    const body = {
      action: 'admin_create', title: f.title.trim() || 'Live test', minutes: mins, picks, manual: [],
      announce: f.announce, timer_mode: f.timer_mode, join_window_min: Math.floor(Number(f.join_window_min)) || 0,
      score_visibility: f.score_visibility, code: f.code.trim(), auto_end: f.auto_end,
      auto_approve_late: f.auto_approve_late, strict_leave: f.strict_leave, strict_return: f.strict_leave && f.strict_return,
    };
    if (f.start_at) body.start_at = new Date(f.start_at).toISOString();
    const r = await liveCall(body, true);
    f.done = r; f.busy = false; loadSessions(true);
  } catch (e) { f.busy = false; f.error = e.message; if (!L.admin) { L.a.error = e.message; go('#/live/admin'); return; } }
  route(true);
}
async function adminDo(action, extra, okMsg) {
  try { await liveCall({ action, ...extra }, true); await loadSessions(false); }
  catch (e) { L.a.error = e.message; route(true); }
}
function copyText(text) {
  const done = () => { const b = document.querySelector('[data-act=aCopy]'); if (b) { const old = b.textContent; b.textContent = 'Copied'; setTimeout(() => { b.textContent = old; }, 1500); } };
  if (navigator.clipboard && navigator.clipboard.writeText) navigator.clipboard.writeText(text).then(done, () => prompt('Copy this link:', text));
  else prompt('Copy this link:', text);
}

/* ---------- routing hooks used by app.js ---------- */
function liveAway() { stopPlay(); stopAdminPoll(); }
function liveRoute(h) {
  const path = h.split('?')[0];
  if (!path.startsWith('#/live')) { liveAway(); return null; }
  if (path !== '#/live/play') stopPlay();
  if (path !== '#/live/admin') stopAdminPoll();
  switch (path) {
    case '#/live/play':
      if (!L.cfg) return playHtml(L.p, 'none');
      if (!L.p.running) startPlay();
      return playHtml(L.p, screenOf(L.p));
    case '#/live/admin':
      if (!L.admin) return adminLoginHtml();
      if (!L.a.poll) { L.a.poll = setInterval(() => loadSessions(true), 5000); loadSessions(true); }
      return adminListHtml();
    case '#/live/admin/new':
      if (!L.admin) return adminLoginHtml();
      return adminNewHtml();
    default: return joinHtml();
  }
}

const LIVE_ACTIONS = {
  lJoin: doJoin,
  lLeave: () => { stopPlay(); forgetLive(); go('#/live'); },
  lLeaveHome: () => { stopPlay(); forgetLive(); go('#/'); },
  lRules: () => { L.p.rulesOk = true; paintPlay(true); },
  lPick: (el) => { const q = L.p.questions[L.p.index]; L.answers[q.pos] = el.dataset.v; saveAnswers(); paintPlay(true); },
  lJump: (el) => { L.p.index = Number(el.dataset.v); paintPlay(true); window.scrollTo(0, 0); },
  lPrev: () => { L.p.index--; paintPlay(true); window.scrollTo(0, 0); },
  lNext: () => { L.p.index++; paintPlay(true); window.scrollTo(0, 0); },
  lAsk: () => {
    const left = L.p.questions.filter((q) => !L.answers[q.pos]).length;
    modal(`<h2 style="margin-top:0">Submit your answers?</h2><p>${left ? `${left} question${left === 1 ? ' is' : 's are'} not answered yet.` : 'You have answered every question.'}</p>
      <div class="row"><button class="grow" data-act="closeModal">Keep going</button><button class="primary grow" data-act="lSubmit">Submit</button></div>`);
  },
  lSubmit: submitNow,
  lRejoin: async () => {
    const p = L.p; p.requestError = '';
    try { await liveCall({ action: 'rejoin_request', code: L.cfg.code, token: L.cfg.token }); p.requestSent = true; }
    catch (e) { p.requestError = e.message; }
    paintPlay(true);
  },
  /* admin */
  aLogin: async () => {
    const f = L.a.login; L.a.busy = true; L.a.error = ''; route(true);
    try {
      const r = await liveCall({ action: 'admin_login', login: f.login, password: f.password });
      L.admin = r.token; f.password = '';
    } catch (e) { L.a.error = e.message; }
    L.a.busy = false; route();
  },
  aOut: () => { L.admin = null; L.a.sessions = []; L.a.key = ''; stopAdminPoll(); go('#/live'); },
  aCopy: (el) => copyText(shareLink(el.dataset.v)),
  aStart: (el) => adminDo('admin_start', { id: el.dataset.v }),
  aEndAsk: (el) => modal(`<h2 style="margin-top:0">End this test?</h2><p>Everyone's answers are graded now and scores are published.</p>
    <div class="row"><button class="grow" data-act="closeModal">Cancel</button><button class="danger grow" data-act="aEnd" data-v="${esc(el.dataset.v)}">End test</button></div>`),
  aEnd: (el) => { closeModal(); adminDo('admin_end', { id: el.dataset.v }); },
  aDeleteAsk: (el) => modal(`<h2 style="margin-top:0">Delete this test?</h2><p>The session, its questions and all results are removed for good.</p>
    <div class="row"><button class="grow" data-act="closeModal">Cancel</button><button class="danger grow" data-act="aDelete" data-v="${esc(el.dataset.v)}">Delete</button></div>`),
  aDelete: (el) => { closeModal(); adminDo('admin_delete', { id: el.dataset.v }); },
  aSchedule: (el) => modal(`<h2 style="margin-top:0">Start time</h2><p class="muted small">The test starts by itself at this time.</p>
    <input class="text" type="datetime-local" id="sched" style="width:100%">
    <div class="row" style="margin-top:1rem"><button class="grow" data-act="aSchedSave" data-v="${esc(el.dataset.v)}" data-clear="1">Clear</button><button class="primary grow" data-act="aSchedSave" data-v="${esc(el.dataset.v)}">Save</button></div>`),
  aSchedSave: (el) => {
    const val = document.getElementById('sched').value;
    const extra = { id: el.dataset.v };
    if (!el.dataset.clear) {
      if (!val || isNaN(new Date(val).getTime())) return;
      extra.start_at = new Date(val).toISOString();
    }
    closeModal(); adminDo('admin_schedule', extra);
  },
  aResults: async (el) => {
    try {
      const r = await liveCall({ action: 'admin_results', id: el.dataset.v }, true);
      modal(`<h2 style="margin-top:0">${esc(r.title)} <span class="muted small">${esc(r.code)}</span></h2>
        <p class="muted small">${esc(STATUS[r.status] || r.status)} · ${(r.joined || []).length} joined · ${(r.ranked || []).length} finished</p>
        ${(r.ranked || []).map((x) => boardRow({ ...x, you: false })).join('') || '<p class="muted">No scores yet.</p>'}
        ${(r.joined || []).length ? `<p class="muted small">Joined: ${esc(r.joined.join(', '))}</p>` : ''}
        <button class="full" data-act="closeModal">Close</button>`);
    } catch (e) { L.a.error = e.message; route(true); }
  },
  aRequests: async (el) => {
    try {
      const r = await liveCall({ action: 'admin_requests', id: el.dataset.v }, true);
      const id = el.dataset.v;
      modal(`<h2 style="margin-top:0">Waiting to be let in</h2>${(r.requests || []).map((q) =>
        `<div class="card row"><span class="grow"><b>${esc(q.name)}</b><br><span class="muted small">${q.kind === 'rejoin' ? 'Left the test, wants back in' : 'Late joiner'}</span></span>
          <button data-act="aDecide" data-id="${esc(id)}" data-p="${esc(q.player_id)}" data-allow="0">Deny</button>
          <button class="primary" data-act="aDecide" data-id="${esc(id)}" data-p="${esc(q.player_id)}" data-allow="1">Allow</button></div>`).join('') || '<p class="muted">Nobody is waiting.</p>'}
        <button class="full" data-act="closeModal">Close</button>`);
    } catch (e) { L.a.error = e.message; route(true); }
  },
  aDecide: async (el) => {
    const { id, p, allow } = el.dataset;
    try { await liveCall({ action: 'admin_decide', id, player_id: p, allow: allow === '1' }, true); } catch (e) { L.a.error = e.message; }
    closeModal(); loadSessions(false);
  },
  aTimer: (el) => { L.a.form.timer_mode = el.dataset.v; route(true); },
  aScore: (el) => { L.a.form.score_visibility = el.dataset.v; route(true); },
  aCreate: createSession,
  aFormDone: () => { L.a.form = null; go('#/live/admin'); },
};

/* typed fields (separate attributes from app.js so the two never clash) */
document.addEventListener('input', (ev) => {
  const el = ev.target, d = el.dataset || {};
  if (d.lj) { // join form
    L.join[d.lj] = d.lj === 'code' ? cleanCode(el.value) : el.value;
    if (d.lj === 'code' && el.value !== L.join.code) el.value = L.join.code;
    const btn = document.getElementById('jgo');
    if (btn) btn.disabled = !(L.join.code.length >= 4 && L.join.name.trim().length >= 2 && !L.join.busy);
  } else if (d.la) L.a.login[d.la] = el.value;
  else if (d.lf && L.a.form) L.a.form[d.lf] = d.lf === 'code' ? cleanCode(el.value) : el.value;
  else if (d.lp && L.a.form) L.a.form.picks[d.lp] = el.value;
});
document.addEventListener('change', (ev) => {
  const el = ev.target;
  if (el.dataset && el.dataset.lc && L.a.form) { L.a.form[el.dataset.lc] = el.checked; route(true); }
});
