-- Strict tests: the admin may allow a closed-out player back (admin online, per test).
alter table pastq_live_sessions add column if not exists strict_return boolean not null default false;
