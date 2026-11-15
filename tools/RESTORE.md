# PastQ CBT backups

A GitHub job (`.github/workflows/backup.yml`) copies the PastQ tables out of Supabase every night at about 03:30
(Lagos time), encrypts them and stores the last 7 nights on the `backups` branch
(`pastq-mon.tgz.enc` ... `pastq-sun.tgz.enc`). The branch is public because the repository is, so the files are
**encrypted**: they are useless without the passphrase. **Keep the passphrase safe; without it no backup can be opened.**

Tables saved: questions (`pastq_questions`), AI answer checks (`pastq_ai_checks`), accounts (`pastq_accounts`),
scores (`pastq_scores`), admin login (`pastq_admin`). Short-lived data (live sessions, sign-in sessions) is not saved.

## One-time setup (GitHub: repository Settings > Secrets and variables > Actions > New repository secret)
- `BACKUP_TOKEN` : the token the owner was given (it matches the one kept in Supabase Vault).
- `BACKUP_PASSPHRASE` : a long passphrase you choose and write down somewhere safe.
Then run the workflow once by hand (Actions > Nightly backup > Run workflow) to check it works.

## Opening a backup
```
git fetch origin backups && git checkout origin/backups -- pastq-mon.tgz.enc
openssl enc -d -aes-256-cbc -pbkdf2 -iter 200000 -pass pass:'YOUR PASSPHRASE' -in pastq-mon.tgz.enc -out dump.tgz
tar xzf dump.tgz        # gives dump/<table>.jsonl, one row per line
```
Restore by inserting the rows back with `INSERT ... ON CONFLICT DO NOTHING` (or ask Claude to do it).
