# Architecture

## Source audit

This client follows the public Codex app-server contract rather than embedding
an agent on Android.

- Codex source inspected at tag `rust-v0.162.1` (`codex-cli 0.162.1`),
  including `codex-rs/app-server-daemon`, app-server RPC handlers and tests.
- Protocol definitions come from `codex-rs/app-server-protocol`.
- Server behavior comes from `codex-rs/app-server`.
- Desktop behavior and command registration were audited from Codex Desktop
  `26.721.4979.0`; Android keeps only actions that can operate on the SSH host.
- `codex-rs/app-server-daemon/README.md` documents the shared daemon and its
  SSH-facing `app-server proxy` path used by remote clients.
- `codex-rs/tui/src/app/session_lifecycle.rs` and
  `codex-rs/tui/src/app_server_session/rollout_history.rs` handle the
  `already has an active writer` case as a read-only snapshot, explicitly not
  an attachment that may route turns. Android follows that boundary.
- `codex-rs/app-server/src/request_processors/thread_processor.rs` distinguishes
  rejoining a live thread on the same app-server from reading a conflicting
  writer's persisted history; `turn_processor.rs` and the protocol schema
  define the turn-ID-scoped interrupt request.
- `codex-rs/app-server-protocol/src/protocol/v2/thread.rs` defines host-wide
  `thread/list` discovery, `cwd` filtering and opaque cursor pagination.
- `codex-rs/app-server/tests/suite/v2/thread_list.rs` verifies `nextCursor`
  pagination and shows that `cwd: None` does not restrict results to one project.
- Desktop sends an empty `sourceKinds` filter for its user-facing thread list;
  Android mirrors that behavior and follows every `nextCursor` page.

## Runtime flow

```text
Android UI
   |
   | SSH handshake (verify/pin SHA-256 host key)
   v
Remote login shell (same account that owns the running Codex daemon)
   |
   | codex app-server daemon version
   v
codex app-server proxy --sock <reported socket>
   |
   | WebSocket frames over SSH stdin/stdout
   v
Shared app-server daemon (existing Codex sessions and active turns)
   |
   +-- app-server RPC (JSON-RPC messages carried in WebSocket text frames)
   +-- initialize / initialized
   +-- thread/list (all cursor pages, no cwd filter)
   +-- thread/start, thread/resume, thread/fork, thread/compact/start
   +-- thread/archive, thread/unarchive, thread/delete
   +-- thread/goal/get, thread/goal/set, thread/goal/clear
   +-- thread/metadata/update (remote pin state)
   +-- turn/start, turn/steer, turn/interrupt
   +-- review/start, collaborationMode/list
   +-- account/read, account/login/start
   +-- model/list (all cursor pages)
   +-- skills/list, plugin/installed, mcpServerStatus/list
   +-- mcpServer/oauth/login, config/mcpServer/reload
   +-- feedback/upload (remote thread id and remote logs)
   +-- item and turn notifications for resumed threads
   +-- command, file-change, permission and user-input approvals
```

Agent execution, repository access, authentication, tools and approvals remain
owned by the remote Codex installation. Android has no local agent runtime.

## Host-wide discovery

A saved connection stores only SSH host, authentication and platform details.
On connection, the client requests every page of non-archived interactive
threads with the same empty `sourceKinds` filter as Desktop. It deliberately
omits the optional `cwd` field from `thread/list`, follows `nextCursor` until it
is null, and deduplicates thread IDs across overlapping pages.

Projects are a UI projection of the returned threads grouped by normalized
`Thread.cwd`; they are not stored in the SSH connection. Resuming a conversation
uses that thread's own `cwd`. Starting a conversation uses the selected imported
project's path.

The model picker is populated exclusively from the remote `model/list` catalog,
including each model's reasoning choices, input modalities, service tiers and
defaults. Plan mode comes from `collaborationMode/list`; permissions come from
`permissionProfile/list`. Text, structured skill/plugin mentions and image data
are sent as official `UserInput` objects. Follow-up messages sent while a turn
is active use `turn/steer` with the active `expectedTurnId`.

When Desktop and Android connect to the same shared daemon, `thread/resume`
rejoins the daemon's in-memory thread and its live turn. Android receives the
active turn ID and can use `turn/interrupt`; after the interrupt completes, a
new `turn/start` continues the same conversation. While the turn is running,
`turn/steer` remains available.

If `thread/resume` instead reports that another app-server process owns the
writer lease, Android does not retry resume or attempt mutations against a
process it cannot reach. It uses `thread/read` and `thread/turns/list` to show a
read-only snapshot refreshed every five seconds; send and stop controls are
hidden in this fallback. To control the original task, both clients must reach
the same shared daemon. The app never kills a remote CLI process or alters lock
files. Forking remains available from the read-only snapshot.

Existing remote API-key and ChatGPT accounts are read through `account/read`.
When the remote host requires ChatGPT authentication, the Android client starts
the official device-code flow through `account/login/start`; credentials remain
owned by the remote Codex installation.

Archived conversations are fetched on demand with the same host-wide,
cursor-paginated `thread/list` query and `archived: true`. Restoring and
permanently deleting them use `thread/unarchive` and `thread/delete`; deletion
is always guarded by a confirmation dialog. Context compaction is available
both as `/compact` and as an explicit current-task menu action.

The MCP status view can reload the remote MCP configuration and start the same
OAuth request used by Desktop. Android opens the returned authorization URL in
the system browser and waits for `mcpServer/oauthLogin/completed`; SSH hosts
whose OAuth provider redirects to loopback must configure a reachable
`mcp_oauth_callback_url` or an SSH tunnel on the remote host.

## Security model

- Passwords, private keys and passphrases are encrypted with an AES-GCM key
  generated inside Android Keystore.
- Android backup and device transfer are disabled for all app data domains.
- A new SSH host is rejected before authentication and its SHA-256 fingerprint
  is shown for explicit confirmation. A changed key is always blocked.
- Android uses SSH stdin/stdout only as a byte tunnel for the daemon proxy's
  WebSocket connection. It does not expose an app-server TCP listener, start a
  second server, or invoke a shell command with user-authored follow-up text.
- The remote thread starts with `workspace-write` sandboxing and `on-request`
  approval by default. Named permission profiles are loaded from the host.
  Explicit full access maps to the app-server `dangerFullAccess` policy.

## Compatibility boundary

The app uses stable app-server methods and tolerant JSON parsing. Unknown item
types are ignored, while unknown server-initiated requests are surfaced rather
than automatically approved. Because schemas are tied to the installed Codex
version, the Android protocol layer should be tested whenever the remote Codex
installation is upgraded across major protocol changes.

Current SSH connection setup supports direct password and private-key hosts.
OpenSSH config expansion, ProxyJump, hardware-backed SSH agents and managed
Remote Control relay pairing are not implemented.

SSHJ's `curve25519` key-exchange factories are excluded on Android because the
platform JCA does not expose the `X25519` key-pair generator expected by SSHJ.
The client retains the interoperable ECDH and DH group14 families rather than
failing before host-key verification.
