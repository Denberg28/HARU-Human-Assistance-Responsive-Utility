# HARU dropdown review

Scope: Android appearance and AI selectors, based on main commit
`87078210d8be0bc8a671da1f826e69597fbb7cce` (v0.9.35).

## Findings and changes

| Finding | Change |
| --- | --- |
| Five accent buttons and three background buttons compete for space in the settings dialog. | Two full-width, labeled selectors display the current accent and background. |
| AI providers occupy three full-width button rows. | One provider selector retains all three existing choices. |
| The Gemini model menu uses a separate button style, a text arrow, and inline checkmarks. | The shared selector uses Material's anchored menu, standard arrow, trailing selection mark, and selected accessibility semantics. |
| Model refresh is a separate kind of operation from selecting a model. | Keep refresh below a divider, labeled “Update models”; it stays disabled until a Gemini key is saved. |
| Re-selecting the current value repeats its callback. | Close the menu without repeating the save callback. |

The four selectors share the same typography and shape. Each option has a
minimum 48 dp height; menus match the field width and are capped at 280 dp,
with scrolling managed by the standard exposed menu. Read-only menu anchors
avoid opening the keyboard. Popup state is transient and selection closes the
menu before calling the existing handler. Theme preferences, AI routing,
credentials, task drag handling, map controls, and launcher artwork retain
their existing implementations.

## Verification

- Local: all 12 existing offline Python runtime tests pass; `git diff --check` passes.
- Android: the pull request invokes the existing unit-test, release-lint, and
  APK-build workflow. Its result must be checked before merge.
- Physical phone checks remain required; no emulator or device is available
  in this workspace. A source review does not establish visual acceptance.

## Phone acceptance

1. On the POCO M6 Pro, open Settings and change every accent and background.
   Confirm immediate rendering, selected marks, and persistence after restart.
2. Open and dismiss each selector with an outside tap and Back. Verify a
   reopened dialog has no popup left open. A read-only selector must not open
   the keyboard.
3. In AI settings, select each provider and Gemini model. Confirm refresh is
   disabled without a saved key and remains an explicit action after a key
   is saved; opening a menu must not trigger AI or model refresh requests.
4. Check Light, Sepia, and Dark with the phone's enlarged text setting,
   portrait and landscape, and TalkBack. Labels and options must remain
   readable and reachable; only the current choice is announced as selected.
5. Recheck task drag order and the map/GPS tab after closing settings.

## Follow-up outside this UI patch

README companion descriptions are inconsistent with the current lock-screen
implementation and include older version references. Update that documentation
separately so this dropdown change remains easy to review.
