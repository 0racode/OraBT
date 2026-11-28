# Importing live test questions (CSV or JSON)

In the admin area, open **New live test** and tap **Import CSV / JSON file**. Pick a file; the app shows how many
questions it found and which rows were skipped, and only adds them when you tap **Add**. You can then edit any
question on screen before you create the test. The in-app **File format** button shows the same help and lets you
save the sample files.

Each question needs: the question text, four options (a, b, c, d) and the correct answer.
A test holds up to 200 questions. Live tests use four options (A-D); a row whose answer is E is skipped.

## CSV

First row is the header. Column names are not case-sensitive. Files saved from Excel or Google Sheets work (UTF-8, with or
without BOM; comma, semicolon or tab separated). Put text that contains commas inside double quotes.

| Column | Required | Notes |
|---|---|---|
| `question` | yes | also accepted: `q`, `text` |
| `a`, `b`, `c`, `d` | yes | also accepted: `option_a` ... `option_d` |
| `answer` | yes | `a`-`d` (any case), `1`-`4`, or the exact text of the right option |
| `subject` | no | shown in results; defaults to Custom |
| `explanation` | no | shown in the review after the test |
| `section` | no | passage or instructions shown above the question |

See [samples/live-test-sample.csv](samples/live-test-sample.csv).

## JSON

Either a plain list of questions, or an object with optional test settings and a `questions` list:

```json
{
  "title": "Mock Test 1",
  "minutes": 40,
  "subject": "Biology",
  "questions": [
    { "question": "...", "a": "...", "b": "...", "c": "...", "d": "...", "answer": "b" },
    { "question": "...", "options": ["...", "...", "...", "..."], "answer": "B", "explanation": "..." }
  ]
}
```

`title` and `minutes` (1 to 120) fill in the title and time boxes. `subject` is the default for questions that do not
have their own. Options can be `a`-`d` or a single `options` list of four. See
[samples/live-test-sample.json](samples/live-test-sample.json).

## Formatting inside text

Question, option, section and explanation text may use `<b>`, `<i>`, `<u>`, `<sub>`, `<sup>` and entities such as `&nbsp;`
and `&amp;`; they are displayed as formatting, not as raw tags.

## Several subjects in one test

A test can mix up to **4 subjects** (bank picks, pasted, typed and imported questions together). They all share the same
duration. Questions stay grouped by subject, in the order the subjects were added, and are shuffled only within a subject.
In the admin screen (app and web) use **Add another subject** to paste a separate block per subject; **Tidy all with AI**
tidies every block and tags each question with its subject. Names that differ only by case or spacing count as one subject.

## What gets skipped

A row is skipped (and listed in the preview) when the question is empty, an option is empty, the answer is missing, or the
answer is not one of A-D. Good rows in the same file are still imported.
