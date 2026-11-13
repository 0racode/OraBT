# PastQ CBT

Offline-first Android app for Nigerian past questions (UTME, WASSCE, Post-UTME) with a timed CBT exam mode.

## Features
- **Practice mode**: pick a subject, optionally filter by exam type and year, get instant correct/wrong feedback and solutions.
- **CBT exam mode**: choose up to 4 subjects, questions per subject, and a time limit. Countdown timer, question jump grid, submit confirmation, auto-submit at zero.
- **Results and review**: score, time used, full answer review. Past results saved on the device.
- **Offline by default**: 6,391 questions in 16 subjects are bundled (`app/src/main/assets/questions.json`) and imported into a local Room database on first launch.
- **Live top-up**: "Fetch more questions online" pulls extra questions from the ALOC API.

## Setup
1. Install Android Studio (Koala or newer) and open this folder. Let it sync; it will create the Gradle wrapper.
2. Get a free token at https://questions.aloc.com.ng
3. Create `local.properties` in the project root (Android Studio creates it for the SDK path) and add:
   ```
   ALOC_TOKEN=your_token_here
   ```
   The token is read at build time and `local.properties` is git-ignored, so it never reaches GitHub.
4. Run on a device or emulator (Android 7.0+).

The app works without a token; only "Fetch more questions online" needs it.

## Structure
- `data/`: Room entities and DAOs, ALOC Retrofit client, repository (first-run import, API top-up).
- `ui/`: Compose screens, view models, shared `QuestionCard`.
- `MainActivity.kt`: navigation and theme.

## Data notes
- The bundled questions come from the public ALOC repo's 2020 database backup. Image-based questions were left out because their images are hosted by ALOC.
- Maths (74) and Physics (128) are thin in the backup; top up from the API for more.
- ALOC API response parsing is written from their docs and is lenient, but check one real response if "Fetch more" returns 0.

## Ideas for next versions
Leaderboards or accounts, per-subject progress charts, bookmarks, a UTME preset (English 60 + 3 subjects x 40, 2 hours), dark-mode polish, Play Store release signing.
