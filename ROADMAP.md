# PastQ CBT roadmap

Built in batches. Each batch is pushed, built by GitHub Actions and published as a GitHub Release.

## Decisions made
- **Updates:** every phone checks GitHub Releases and updates itself in-app.
- **Retention:** leaderboard scores kept 31 days (so the monthly board works). Live-session results and personal test histories deleted after 7 days.
- **AI (Gemini):** never invents questions. It explains answers for questions already in the bank, checks answers, and for admin-pasted questions it can tidy the text and add plausible wrong options when the admin pastes a question without options. The admin confirms the correct answer.
- **Admin login:** checked on the server only, never stored in the app or this repo.

## Batch A: updater and exam rework (this batch)
- Permanent signing key and versioned GitHub Releases (needs the three signing secrets, see README)
- In-app updater: asks for install permission first, downloads with the system DownloadManager, resumes after the settings screen
- UTME mode: English 60 + 3 subjects x 40, exactly 4 subjects
- Typed exam duration (hours and minutes, 2 hours maximum)
- Start buttons pinned to the bottom so lists never need scrolling first
- Test history shows mode, subjects and per-subject scores

## Batch B: accounts and leaderboards
- Optional profile: first name, last name (optional), email, password (Supabase Auth)
- Scores graded on the server so they cannot be faked
- Daily, weekly and monthly leaderboards of top scorers, updated when a record is beaten

## Batch C: explanations and answer checking (needs a Gemini API key as a Supabase secret)
- Explanation for the correct option, produced only from questions in the bank
- Independent answer check across the whole bank; disagreements are flagged for review and kept out of exams until fixed

## Batch D: live CBT sessions
- Admin login (server-side check), create a session with chosen subjects and a typed duration up to 2 hours
- Questions from the ALOC bank, or pasted by the admin with AI tidy-up
- Users join with a display name (unique within a session), wait for the admin to start
- Ends when time runs out or the admin ends it; users see corrections with explanations and their position
- Results kept 7 days

## Batch E: more question banks
- Search public repositories for additional past-question banks and merge the usable ones (check licences first)


## Batch D (live sessions) - built
- Server: tables pastq_live_sessions/items/players, admin check via pgcrypto, edge function `pastq-live`
  (join, state, submit, results, admin_*). Questions are snapshotted into the session; grading is server side.
- Sessions, players and results are deleted 7 days after a session ends (daily cron).
- Admin sign-in is checked on the server (name Oracle or the admin email); the password is not in the app or repo.
  The admin token lives in memory only and expires after 12 hours; 5 wrong tries lock the IP for 15 minutes.
- Admin can draw questions from the verified bank, or paste/type their own (AI tidies and adds options only to
  pasted questions; the admin confirms every correct answer).
- Admin can review disputed questions and set the correct key (stored as `confirmed`).
- Known gap: a corrected key is fixed in the cloud library (used for scores and live sessions); phones that
  already bundled the old key pick it up only after the next library refresh.

## Batch E (more question banks) - done
Searched GitHub for Nigerian exam question banks. Findings:
- ALIYUAAM10/NAIJAPREP (MIT): 150 original practice questions with explanations -> merged (type "practice";
  adds Agricultural Science and Further Mathematics). Credit in THIRD_PARTY.md.
- Japhethca/past-questions-api (MIT): code only, no question data.
- croniesng/Sdashapi: docs for a hosted paid API; no data in the repo.
- aliuoluyemi7-prog/ExamWiseNG: CSV questions but no licence, so not reusable without the author's permission.
- brandnova/PastQ-CBT and similar: front-ends over the same ALOC data we already have.
The new questions are marked unchecked, so the nightly AI answer check covers them too.

- aliuoluyemi7-prog/ExamWiseNG: 798 questions imported on 2026-11-14 after the author's verbal permission
  (educational use only, no commercial use). Credit and the condition are in THIRD_PARTY.md.
  TODO: get the permission confirmed in writing (WhatsApp/SMS screenshot is enough).

## Next (agreed 2026-11-15)
1. Import the MySchool JAMB 2021-2025 collection (map jamb->utme, dedupe by question text), credit + link in the app.
2. Collect MySchool WAEC and NECO after the JAMB run finishes (sequential, polite).
3. Other sites checked: WAECONLINE (landing page only), Nigerian Scholars/Ulearngo (questions only inside their apps),
   Erudites (news site). No public past-question pages found, so MySchool is the only web source for now.
4. Study mode: pick several exam bodies at once - done (multi-select chips).
5. Question images: collector keeps image URLs, app shows them with Coil and caches on the phone. MySchool-hosted images
   are loaded from their server once and cached; consider mirroring if their bandwidth is a concern.
6. Live-session notifications (last): choose between a Firebase push (instant, needs a Firebase project) and a
   background check every ~15 minutes (no Firebase, delayed).

## Strict live tests (2026-11-21)

Per-test switch "I may let people back in": a closed-out player can ask to return; the admin decides while online, otherwise the result stands. Strict tests show the rules before the questions open.

Optional "Strict mode" per test: leaving the app (or 60s without a check-in) closes the player out for good, graded as submitted, no rejoin and no admin approval, so it works with the admin offline. One phone can join once (device id). Needs the updated app.

## Scheduled live tests (2026-11-20)
- The admin can set a start time when creating a test, or later with "Set a start time" / "Change start time" on a waiting test.
  The test starts by itself at that moment and ends at start + duration (plus the late-join window in personal-timer mode),
  or earlier when every player has submitted ("End when everyone has submitted").
- Server: `scheduled_start_at` and `auto_approve_late` on `pastq_live_sessions`. The `pastq-live` function applies the start
  when anyone reads the session and through a `tick` action that the `pastq-live-tick` cron job calls every minute, so a
  test still starts with nobody online. The waiting -> running change is one conditional update, so the "Live now" notice is sent once.
- Late joiners: with "Let late joiners in automatically" (on by default for scheduled tests) they go straight in; otherwise
  they still wait for the admin's approval.
- Known gaps: a player who leaves the app during the test is still locked until the admin lets them back in, which blocks
  "end when everyone has submitted" until the time is up. Times are chosen on the admin's phone clock.
- Server code now lives in `supabase/functions/pastq-live/index.ts` and the SQL in `supabase/migrations/`.
  Deploy by pasting the file into the Supabase function (or via the Supabase CLI).
