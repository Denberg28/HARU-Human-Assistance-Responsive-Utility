HARU v0.7.2 is a focused chat-input maintenance release.

- Fixes the command example instruction ("task Buy milk • remind me in 30 min to call") remaining visible after a conversation starts.
- Command examples now behave as first-use guidance only: visible before interaction, hidden while typing or using a HARU conversation draft, and kept hidden after a message is sent.
- A true conversation-memory reset restores the first-use guidance.
- Extracts the visibility rule into one small testable helper instead of leaving UI behavior as an unconditional text element.
- Adds regression tests for fresh state, typing/drafts, sent-message state, and blank whitespace.
- No changes to Map, reminders, Home Bubble behavior, AI provider selection, memory limits, or the stable v0.7.1+ Android signing identity.

This release is signed with HARU's stable protected release key and is intended to install normally over v0.7.1.
