-- Strict (anti-malpractice) live tests: leaving the app ends the test for good, with or without the admin online.
alter table pastq_live_sessions add column if not exists strict_leave boolean not null default false;
alter table pastq_live_players
  add column if not exists last_seen timestamptz,
  add column if not exists entered_at timestamptz,
  add column if not exists device_hash text,
  add column if not exists left_app boolean not null default false;
create unique index if not exists pastq_live_players_device_idx
  on pastq_live_players (session_id, device_hash) where device_hash is not null;
-- The minute job also runs for strict sessions, to close out players who vanished.
select cron.schedule('pastq-live-tick', '* * * * *', $job$
  select net.http_post(
    url := 'https://wyzswbklytklzbcmihsh.supabase.co/functions/v1/pastq-live',
    headers := jsonb_build_object(
      'Content-Type', 'application/json',
      'x-job-token', (select token from public.pastq_jobs where name = 'live-tick')),
    body := '{"action":"tick"}'::jsonb,
    timeout_milliseconds := 30000
  )
  where exists (
    select 1 from public.pastq_live_sessions
    where (status = 'waiting' and scheduled_start_at is not null and scheduled_start_at <= now())
       or (status = 'running' and (ends_at <= now() or strict_leave))
  );
$job$);
