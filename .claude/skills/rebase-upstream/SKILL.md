---
name: rebase-upstream
description: Rebase the oxc fork's patch stack onto kolmafia/kolmafia upstream/main, resolve conflicts, verify, and force-push. Use when the user asks to rebase, sync with upstream, update the fork, or when the scheduled "Rebase Upstream" GitHub action failed.
---

# Rebase the oxc fork onto upstream

[FORK.md](../../../FORK.md) is the source of truth for the fork's rules (rebase never
merge, patch stack groups, conflict hotspots). Read it first if it is not already in
context. This skill is the step-by-step procedure.

## 1. Preflight

```shell
git status --short | grep -v '^??'        # must be empty: no staged/unstaged tracked changes
git fetch upstream && git fetch origin
git rev-list --left-right --count origin/main...main   # must be "0 0"
git rev-list --count main..upstream/main               # upstream commits to take in
git log --oneline upstream/main..main                  # the stack being replayed
```

- Untracked files (`KoLmafia-*-oxc-*.jar`, `update-kolmafia.sh`, `node_modules/`, …) are
  expected — ignore them, never commit or stash them.
- If `main` and `origin/main` differ, stop and ask: either local commits are unpushed or
  the sync action pushed something. Do not rebase on top of a divergence blindly.
- If `upstream/main` has 0 new commits, there is nothing to do.
- Record the old base for verification later:
  `OLD_BASE=$(git merge-base origin/main upstream/main)`

## 2. Rebase

```shell
git rebase upstream/main
```

Non-interactive. On each conflict:

1. Look at the conflict with `git diff <file>` (it shows the diff3 base section between
   `|||||||` and `=======`), the fork commit's own change with
   `git show <fork-sha> -- <file>`, and what upstream changed with
   `git log --oneline $OLD_BASE..upstream/main -- <file>`.
2. Rule: **take upstream's version of the surrounding code and re-apply only the fork
   commit's intent.** Remember `HEAD`/`--ours` is upstream (plus earlier fork commits),
   `--theirs` is the fork commit being replayed.
3. Before `git add`, check that `git diff upstream/main -- <file>` (for the first fork
   commit touching it) shows *only* the fork's lines, no leftover markers
   (`grep -n '<<<<<<<\|>>>>>>>' <file>`).
4. `git add <file> && GIT_EDITOR=true git rebase --continue`

Patterns seen so far (see also FORK.md "Conflict hotspots"):

| File | Typical cause | Resolution |
| --- | --- | --- |
| `RelayAgentTest.java` | upstream changed expected headers | `git checkout --ours`, then `sed -i 's/Server: KoLmafia r0/Server: KoLmafia oxc r0/g'` |
| `RuntimeLibrary.java` | both sides added functions/imports nearby | keep both |
| import blocks (e.g. `RequestThread.java`) | upstream removed/added an import next to a fork import | upstream's imports + the fork's added import |
| `chat/ChatManager.java` | upstream lint cleanups (`static final` → `static`, `equals("")` → `isEmpty()`) on lines the fork changed (`messageDate` param) | upstream style + fork signature/logic |
| `build.gradle.kts` | upstream reworked version computation | re-apply the `upstream/main`-based `revisionProvider`/`patchesProvider` scheme |
| `.github/workflows/build.yml` | upstream edited jobs | keep `oxc/kolmafia` repo guards and "Add upstream remote" steps |

- A conflict in a file **no** fork commit touches means a stray change in the stack —
  resolve toward upstream and tell the user.
- A fork commit that upstream has merged becomes empty: `git rebase --skip`, and tell the
  user so FORK.md's stack list can be updated.
- If a resolution is genuinely ambiguous (fork feature semantics would change), stop and
  ask instead of guessing. `git rebase --abort` returns to the pre-rebase state.

## 3. Verify

```shell
git range-diff $OLD_BASE..origin/main upstream/main..main | grep -v ' = '
```

Every remaining `!` entry must differ only in context lines adapted to upstream — the
`+`/`-` lines the fork commit adds or removes must be the same. Stack size should be
unchanged unless a commit was dropped on purpose.

```shell
./gradlew spotlessApply          # must leave the tree clean; if not, fold the fixes into the owning commit (FORK.md "Editing the stack")
./gradlew test --console=plain   # slow (several minutes) — run in background
```

The full suite takes ~20 minutes. Known environmental failure: `FindBarTest$Shortcut`
fails with `java.awt.HeadlessException: No X11 DISPLAY variable was set` when run from a
shell without a display (e.g. WSL / Claude Code). That is not caused by the stack — confirm
the exception type in `build/test-results/test/TEST-*.xml` and move on.

If other tests fail, check whether the same test fails on plain `upstream/main` before blaming
the stack (`git stash -u` is not needed — use a worktree:
`git worktree add ../kolmafia-upstream upstream/main`). Fix fork-caused failures as
fixups into the owning commit, not as new commits.

## 4. Push

Force-pushing is outward-facing and triggers the *Build oxc* release workflow. Report the
result (upstream commits taken in, conflicts and how they were resolved, test results)
and **ask the user before pushing**, unless they already said to push. Then:

```shell
git push --force-with-lease origin main
```

Never use plain `--force`: the sync action pushes to the same branch.

## 5. Report

Summarize: upstream range taken in, stack size, each conflict and its resolution, test
outcome, push status. Mention any stack hygiene issues noticed along the way, e.g. a
commit that FORK.md's stack list doesn't mention, or a follow-up commit that should be a
fixup into the commit that owns the change.
