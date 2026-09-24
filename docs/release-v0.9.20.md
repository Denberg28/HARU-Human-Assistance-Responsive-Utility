# HARU v0.9.20

HARU chat can now perform bounded local actions for tasks and reminders.

## What changed

- Natural chat requests can add items to HARU's local **Today** task list.
- Natural reminder requests can schedule Android reminders without requiring command-like phrasing.
- Supported reminder timing includes relative times such as "in 30 minutes" and simple clock times such as "tomorrow at 8 AM".
- If a reminder request does not include a usable future time, HARU asks for the missing time instead of pretending the reminder was created.
- Task and reminder actions are interpreted locally before any online AI request.
- The online AI never receives direct write access to the task/reminder store.
- Task changes refresh the lock-screen Today notification immediately.
- Upcoming reminders appear as compact rows in the expanded lock-screen Today notification.
- Reminder alarms remain Android-native and lock-screen-visible when due.
- Added parser tests covering natural task requests, relative reminders, clock-time reminders, timezone handling, ambiguous timing, past-time handling, and ordinary non-action chat.

## Safety and reliability

HARU only executes explicit user requests. Normal conversation does not create tasks or reminders. The AI system prompt also forbids claiming an action occurred unless the local app actually completed it.
