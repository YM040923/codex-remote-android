# Desktop feature parity audit

Audit baseline:

- Codex Desktop `26.721.4979.0` command registrations and remote-host request bridge.
- Open-source Codex app-server schema from `codex-rs/app-server-protocol`.
- Android remains an SSH client only; agent execution stays on the remote host.

## Implemented remote workflow

| Area | Android support |
| --- | --- |
| Host discovery | Imports every active remote thread with cursor pagination and no project filter; groups projects by thread `cwd`. |
| Thread lifecycle | Start, paginated resume, rename, pin, archive, browse archived tasks, unarchive, permanently delete, fork, and compact. If another independent app-server holds the writer lease, show and refresh a read-only history/status snapshot without retrying resume. |
| Turns | Load the latest five full turns first, fetch older pages at the top with `thread/turns/list`, preserve scroll position, start, stream, steer, and interrupt. When attached to the same shared daemon as another Codex window, rejoin its live turn, stop it, and send a new turn in that same conversation. |
| Composer | Desktop-style plus menu, remote file/folder references, Android image input, Goal/Plan actions, remote skill/plugin `$` mentions, slash commands, model, reasoning, service tier, collaboration mode, and permissions. |
| Commands | `/compact`, `/feedback`, `/fork`, `/goal`, `/init`, `/mcp`, `/model`, `/new`, `/plan`, `/reasoning`, `/review-mode`, and `/status`, plus task-management shortcuts. |
| Goals | Desktop-style removable Goal marker for the next composer submission, plus read, create, edit, pause, resume, clear, and continue persisted remote goals. |
| Review and file changes | Start inline reviews and render structured, expandable changed-file summaries in the conversation. There is no separate Changes tab. |
| Long conversations | Open at the latest message, load older turns incrementally while scrolling upward, preserve the visible anchor during prepend, and show a return-to-latest button away from the bottom. |
| MCP | Show server/tool/resource status, start OAuth, observe completion, and reload remote MCP configuration. |
| Authentication | Reuse remote auth and support the ChatGPT device-code flow. |
| Approvals | Command, file-change, permission, and structured user-input requests. |
| Diagnostics | Context and rate-limit status, app-server warnings, feedback upload, and SSH host-key pinning. |

## Remaining gaps

### High priority

- Rich Git review: automatic base-branch discovery, per-file navigation, syntax-highlighted diffs, inline comments, commit/revert actions, and detached review delivery.
- Edit or undo a historical turn. The protocol's `thread/rollback` is deprecated and does not restore repository changes; a correct implementation must restore both conversation history and affected files.
- Background terminal sessions and interactive terminal input. Command events render, but Android has no terminal panel for long-running PTY sessions.
- Control a writer in an entirely separate app-server process. The fallback view is intentionally read-only; stop/continue works when Android and the other Codex window connect to the same shared app-server daemon. Unrelated server processes are not controlled by killing processes or editing lock files.
- MCP extended-form elicitation, MCP App HTML surfaces, resource browsing, and direct MCP tool invocation.
- Plugin management: marketplace browsing, install/uninstall/update, authentication policy, plugin details, and sharing. Android currently loads installed plugins for `$` mentions only. The public protocol marks several plugin management methods as under development.

### Composer and media

- Fuzzy remote file search across the project. The current picker browses remote directories and inserts `@file` references, but it does not yet provide desktop's ranked workspace-wide search.
- Generic file attachments, remote-path images, audio attachments, dictation, realtime voice, and rich generated-media viewers.
- Temporary `/side` chats, Memories controls, personality selection, and auto-review-denial approval shortcuts.
- Historical-turn fork selection through `thread/fork.lastTurnId`; current fork copies the complete task.

### Settings and account

- Enable/disable skills, add extra skill roots, view full skill/plugin details, and manage Hooks.
- Full remote config editor, experimental feature switches, account logout, usage history, earned rate-limit resets, and workspace messages.
- Notification preferences, desktop automations, app connectors, and Remote Control relay pairing.

### SSH and platform integration

- OpenSSH config expansion, ProxyJump, SSH agent or hardware-key authentication, and managed relay pairing.
- Remote file open-in-editor actions, deep links, desktop notifications, and Android share-sheet integration.

When an independent app-server owns a thread, its status/history snapshot
refreshes every five seconds; detailed item deltas may be less immediate than
on a thread resumed directly by Android. The snapshot view does not expose
stop/send controls because those requests cannot be routed to the independent
writer process.
