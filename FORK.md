# Maintaining the oxc fork

This is a **fork of [kolmafia/kolmafia](https://github.com/kolmafia/kolmafia)** that
carries a small stack of patches on top of upstream. It exists to build the jar used by
the **buffoonery buffbot** system, plus a handful of general improvements that are
either waiting to be upstreamed or were rejected there.

Everything in this file is fork-owned. [AGENTS.md](AGENTS.md) is upstream's and
describes KoLmafia itself — follow it for anything touching KoLmafia code.

## Golden rule: rebase, never merge

`main` is always **`upstream/main` plus an ordered stack of fork commits**. The stack is
*replayed* onto upstream, never merged into it. There are no merge commits in this
fork's history and there must never be one.

```
origin    git@github.com:oxc/kolmafia.git       # this fork
upstream  https://github.com/kolmafia/kolmafia
```

Regenerate the current stack any time with:

```shell
git fetch upstream
git log --oneline upstream/main..main
```

## The patch stack

Three groups of commits, by intent. The stack order is historical rather than grouped,
so the groups are interleaved; the group determines what you do with a commit, not where
it sits.

### a) Candidates for upstreaming (and rejects)

Fixes and improvements to KoLmafia proper that are useful outside this fork. They live
here first so they can be exercised in production before a PR — or because upstream
turned them down.

- `fix: release the Rhino scope retained by JavascriptRuntime.currentStdLib`
- `perf: share the JavaScript module script provider across executions`
- `chore: support NativeStrings being passed to library functions`
- `perf: short-circuit parameter match if arg count is wrong`
- `chore: print Stack on ScriptExceptions`
- `feat: include function name in Exception message`
- `feat: add search_mall library function`
- `feat: add library commands to start/stop chat`
- `feat: add thread and message passing support for scripts`
- `feat: pass messageDate to chatbotScript`

These use upstream's conventional-commit style (`fix:`, `feat:`, `perf:`, `chore:`) —
keep it that way, so a commit is ready to cherry-pick as-is. To upstream one:

```shell
git checkout -b some-fix upstream/main
git cherry-pick <sha>
```

then open the PR against `kolmafia/kolmafia` and, per AGENTS.md, state in the PR that
the work came from an automated LLM agent. Once upstream merges it, the commit
disappears from the stack on the next rebase (git usually drops it as already-applied;
if it conflicts as an empty patch, `git rebase --skip`).

### b) Build-system changes

Everything needed to make the fork build and publish *its* artifact. Never upstreamable.

- `build: build oxc version`
- `oxc: add github action to sync upstream`
- `oxc: temporary fix for download-artifact@v7 not creating subdirectories`

See [Build and release](#build-and-release) below.

### c) Buffoonery-only additions

Features that exist purely for the buffbot and will **never** be upstreamed. Prefixed
`oxc:` (plus one `debug:`) to mark them as fork-local.

- `oxc: add buffoonery client` — `src/net/sourceforge/kolmafia/oxc/` (`BuffooneryHttpClient`,
  `BuffooneryResponse`) and the ASH/JS library functions that expose it
- `oxc: add cast buff function` — `CastBuffRequest` + library function
- `oxc: add poll_chat function` — `ChatRequest` + library function
- `oxc: add support for connecting to websockets` — websocket support in the buffoonery client
- `debug: debug messages without time` — session-log line for chat messages with no timestamp

New buffoonery work belongs in a new `oxc:` commit at the top of the stack, or amended
into the matching existing one — see [Editing the stack](#editing-the-stack).

## Build and release

The fork builds **only the fat jar**. Upstream's OS-specific packaging (deb/exe/dmg via
`jpackage`) and the homebrew notification are not deleted from
`.github/workflows/build.yml` — they are left in place and gated on
`if: github.repository == 'kolmafia/kolmafia'`, so they simply never run here, while the
`jar` and `release` jobs are gated on `oxc/kolmafia`. Keeping upstream's jobs untouched
is deliberate: it keeps the fork's diff to `build.yml` tiny and rebasable.

### Versioning

Fork jars are versioned `<upstream-revision>-oxc-<patch-count>` (e.g.
`KoLmafia-28978-oxc-16.jar`):

- **revision** — commit count of `upstream/main`
- **patch count** — commit count of `upstream/main..HEAD`, i.e. the size of the stack

This is why both the `jar` and `release` jobs add the upstream remote and fetch it
before building: `build.gradle.kts` (`revisionProvider` / `patchesProvider`) and the
release job's tag computation both need `upstream/main` to exist locally. A build
without the upstream remote will produce a wrong version.

`PRODUCT_NAME` in `StaticEntity.java` is `"KoLmafia oxc"`, which is why
`RelayAgentTest` is patched in the same commit — the test asserts on the product name.

### Workflows

- `.github/workflows/sync.yml` (**Rebase Upstream**, fork-owned) — runs on a schedule
  (and `workflow_dispatch`) and rebases `main` onto `upstream/main` via
  `imba-tjd/rebase-upstream-action`. It only pushes when the rebase is clean; a
  conflicting rebase fails the run and is left for a human. Note its
  `fetch-depth: 50` — it must stay **greater than the number of fork commits**, so bump
  it again if the stack ever approaches that.
- `.github/workflows/build.yml` (**Build oxc**, upstream's file, fork-patched) — runs on
  push to `main`, on `workflow_dispatch`, and on `workflow_run` completion of *Rebase
  Upstream*. Builds `shadowJar`, force-tags `r<version>`, and publishes a GitHub release
  with the jar.
- `.github/workflows/check.yml`, `.github/workflows/npm.yml` — upstream's, unmodified.
  Leave them alone.

Because the fork's own workflow files are the only ones it modifies and it never deletes
upstream's, an upstream change under `.github/workflows/` only conflicts if it touches
the same lines in `build.yml`.

## Automated sync, and what to do when it fails

When the scheduled rebase applies cleanly there is nothing to do: it pushes `main` and
the *Build oxc* run that follows publishes a new release. When it fails, rebase by hand:

```shell
git fetch upstream
git checkout main
git rebase upstream/main
# resolve, git add, git rebase --continue
./gradlew spotlessApply
./gradlew test
git push --force-with-lease origin main
```

During a rebase the sides are inverted — `--ours` is upstream, `--theirs` is the fork
commit being replayed. Sanity-check with `git diff` before trusting either.

### Conflict hotspots

- **`src/net/sourceforge/kolmafia/textui/RuntimeLibrary.java`** — by far the most
  likely. Six fork commits add library functions to it, and upstream edits it
  constantly. Conflicts are almost always "both added a function nearby": keep both
  sides.
- **`build.gradle.kts`** — the `revisionProvider` / `patchesProvider` versioning block
  and the buffoonery client's dependencies. If upstream reworks version computation,
  re-apply the `upstream/main`-based scheme rather than taking either side wholesale.
- **`.github/workflows/build.yml`** — three fork commits touch it. Keep the
  `oxc/kolmafia` repository guards and the "Add upstream remote" steps.
- **`src/net/sourceforge/kolmafia/chat/`** (`ChatPoller`, `ChatManager`, `ChatMessage`)
  and **`textui/javascript/`** — smaller, but touched by several fork commits.
- **`test/net/sourceforge/kolmafia/webui/RelayAgentTest.java`** — only conflicts if
  upstream changes those assertions; keep the `KoLmafia oxc` product name.

If a conflict shows up in a file *no* fork commit touches, something is wrong — a stray
change crept into the stack. Resolve toward upstream and investigate.

## Editing the stack

**Rewriting an existing commit is normal here, and is the preferred way to change
something the stack already owns.** The whole stack is replayed on every sync, so there
is no stable history to protect: a fix to a fork commit should go *into* that commit, not
on top of it. This keeps the stack one commit per coherent feature — readable, and
cherry-pickable when a group (a) commit goes upstream.

Fix up the commit that owns the change:

```shell
git add <files>
git commit --fixup <sha>              # or --amend, if it is already HEAD
git rebase -i --autosquash upstream/main
git push --force-with-lease origin main
```

Rule of thumb for which commit owns a change:

- **Fix up** when the change corrects, completes, or tunes something an existing fork
  commit introduced — a bug in a fork feature, a knob it added, a follow-up to its build
  change. Example: raising `fetch-depth` in `sync.yml` belongs in
  `oxc: add github action to sync upstream`, which added that line, not in a separate
  commit after it.
- **New commit** when the change is a distinct feature or concern, even if it touches
  the same files. A new buffoonery library function is its own `oxc:` commit rather than
  a fixup to `oxc: add buffoonery client`.
- **Never fix up an upstream commit.** Only commits in `upstream/main..main` are yours.
  If `git rebase -i upstream/main` does not list it, leave it alone.

One caveat for group (a): if a commit already has an open PR upstream, rewriting it here
means the PR and the fork have diverged — either push the updated commit to the PR
branch too, or wait until it is merged.

Force-pushing `main` is normal here. Always use `--force-with-lease`, since the sync
action pushes to the same branch.

## Untracked local files

The repo root accumulates downloaded `KoLmafia-*-oxc-*.jar` release artifacts and
`update-kolmafia.sh` (fetches the latest fork release and copies it into the KoLmafia
directory). These are deliberately untracked local files — do not commit them, and do
not add them to `.gitignore` either, since that would be a fork change to an upstream
file for no benefit.
