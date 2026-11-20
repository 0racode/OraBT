-- Scheduled start for live tests (applied to the Supabase project on 2026-11-20).
-- A waiting session with scheduled_start_at starts by itself at that time; the pastq-live function does it when
-- anyone reads the session, and the cron job below does it every minute even when nobody is online.

alter table public.pastq_live_sessions
  add column if not exists scheduled_start_at timestamptz,
  add column if not exists auto_approve_late boolean not null default false;

create index if not exists pastq_live_sessions_due_idx
  on public.pastq_live_sessions (scheduled_start_at)
  where status = 'waiting' and scheduled_start_at is not null;

-- Secret that lets the cron job call the "tick" action (same pattern as the verify-bank job).
insert into public.pastq_jobs (name, token, state, note)
select 'live-tick',
       replace(gen_random_uuid()::text || gen_random_uuid()::text, '-', ''),
       'idle',
       'Starts and ends scheduled live tests'
where not exists (select 1 from public.pastq_jobs where name = 'live-tick');

select cron.schedule(
  'pastq-live-tick',
  '* * * * *',
  $cron$
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
       or (status = 'running' and ends_at <= now())
  );
  $cron$
);
