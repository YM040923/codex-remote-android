# Release index

Every published build, its signing group, and what changed.

## Current

**v0.2.3** - recommended. Pure Compose Markdown rendering with recursive inline
parsing, clickable links, and a redesigned conversation surface.

## Signing groups

| Certificate | Versions | Notes |
| --- | --- | --- |
| `CN=YM040923 Codex Remote` | v0.1.7, v0.1.8, v0.1.9 | Older key. Cannot upgrade into v0.2.x without uninstalling. |
| `CN=Codex Remote Release` | v0.2.0, v0.2.1, v0.2.2, v0.2.3 | Current key. Covers all in-place upgrades from v0.2.0 onward. |

Android enforces signature matching on update. Crossing the group boundary
requires uninstalling the old app, which also clears locally stored SSH
passwords and pinned host fingerprints.

## Per-version summary

### v0.2.3

- Assistant messages render directly on the page background; the previous
  container card is removed.
- Block spacing varies by type: 20dp before headings, 16dp before tables and
  code, 14dp before rules, 12dp before paragraphs.
- Tables drop the header background and alternating row colours. They keep a
  rounded container, a title bar, and 1dp dividers between rows.
- User bubbles move to `surfaceContainerHigh` so the shape contrast against
  unbounded assistant text is explicit.
- The composer control row splits: the left group scrolls, a vertical divider
  separates it from the pinned model and send controls.
- Inline parsing became recursive, so nested emphasis such as
  `**bold *italic***` applies both styles.
- Links are clickable and open through the system browser.

Known gaps: LaTeX renders as plain text; the table action icons are not
implemented; dark-mode divider strength is untuned.

### v0.2.2

- Markwon, AndroidView and TextView rendering were removed in favour of native
  Compose blocks.
- Code blocks, tables, quotes, headings, lists and rules are drawn by Compose.
- Inline bold, italic, code, strikethrough and link parsing added.

Known gaps carried into this build: no LaTeX, links not clickable, nested
emphasis unsupported. All three are resolved in v0.2.3.

### v0.2.1

Maintenance release on top of v0.2.0 with further interface tuning. No change to
the remote control surface.

### v0.2.0

First build on the current signing key. Baseline for the remote workflow:

- Connects to the host's shared app-server daemon through
  `codex app-server proxy`; never starts a second agent on Android.
- Imports all resumable remote conversations and groups projects by thread cwd.
- Paginated history: latest five turns first, older turns fetched upward.
- Rejoins a live turn when attached to the same daemon as Codex Desktop, can
  stop it, and can send a new message in that conversation.
- Shows a five-second read-only snapshot when another app-server process holds
  the writer lease. Never kills processes or deletes lock files.
- Model and reasoning options load from remote `model/list`.
- Composer supports plan mode, service tiers, permission profiles, image input,
  turn steering, goals, compaction, forks, review, MCP status, remote skills and
  installed plugins.

### v0.1.9

Last build on the older signing key. Carries the v0.1.7 and v0.1.8 connection
stability work.

### v0.1.8

Continues the v0.1.7 stability fixes with further SSH transport error reporting.

### v0.1.7

- Connection teardown no longer leaves suspended channel readers.
- Approval requests queue instead of overwriting each other.
- Pasted OpenSSH/PEM private keys stay in memory rather than a temp file.
- Close is idempotent; a second call no longer throws.
- The daemon version probe reads its output stream fully.
