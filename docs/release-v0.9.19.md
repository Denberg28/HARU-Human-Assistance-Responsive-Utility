# HARU v0.9.19

HARU now evolves around a quieter daily companion loop instead of adding more chat complexity.

## What changed

- Added **Daily Pulse**, a fully local state engine that reacts to time of day and task progress.
- Added **Today’s Focus** so HARU surfaces the first unfinished task instead of showing a dense task summary.
- Added lightweight morning, focus, evening, sleep, and completion states.
- Preserved overdue reminders as the highest-priority nudge.
- Added a small completion reaction when all entered tasks are done.
- Updated the Today card to show the current focus first, with reminders below it.
- Simplified lock-screen task rows and removed redundant bullets.
- Shortened the Lavender accent label to **Lilac** so the three-button row stays visually balanced.
- Darkened the Sepia background to a warmer paper-like tone based on the supplied reading-theme reference.
- Kept Daily Pulse offline: no Gemini/Groq request is made for these reactions.
- Added unit coverage for Daily Pulse state selection, privacy, completion, and focus behavior.

## Design intent

HARU should quietly watch over the day, show what matters next, and react to progress without becoming another full-screen chatbot. The feature reuses the existing task store and lock-screen service, so it does not introduce a new persistent background worker.

## Verification target

GitHub validation must pass Android unit tests, lint, release assembly, and the existing Python runtime tests before publication.
