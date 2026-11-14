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
