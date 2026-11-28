-- Open-window live tests: no admin Start. The test is open for open_window_min minutes (1 to 2880 = 2 days);
-- each player starts whenever they like inside the window and gets duration_min from the moment they begin,
-- but never past the end of the window (the session ends the test for everyone still going). No late-join requests.
-- Null means a normal (admin-started) test. Open tests keep timer_mode = 'personal', so older apps still work.
alter table public.pastq_live_sessions
  add column if not exists open_window_min integer
  check (open_window_min is null or (open_window_min >= 1 and open_window_min <= 2880));
