# Supported Git commands

Run `git help` inside the app for the same categorized list.

## Repository creation

`init` and `clone` are available as native actions on the Repositories screen.

## Workspace and commits

- `status`
- `add`
- `apply`
- `rm`
- `mv`
- `restore`
- `clean`
- `commit`
- `reset`

## History and inspection

- `log`
- `shortlog`
- `show`
- `diff`
- `blame`
- `annotate`
- `reflog`
- `rev-list`
- `rev-parse`
- `describe`
- `name-rev`
- `merge-base`

## Branches and integration

- `branch`
- `checkout`
- `switch`
- `merge`
- `rebase`
- `cherry-pick`
- `revert`
- `tag`
- `stash`

## Remotes and submodules

- `remote`
- `fetch`
- `pull`
- `push`
- `ls-remote`
- `submodule`

## Objects, refs, and archives

- `archive`
- `cat-file`
- `hash-object`
- `ls-files`
- `ls-tree`
- `show-ref`
- `for-each-ref`
- `symbolic-ref`
- `update-ref`
- `notes`
- `pack-refs`
- `count-objects`

## Search and maintenance

- `grep`
- `check-ignore`
- `gc`
- `repack`
- `config`
- `help`
- `version`

## Scope

The app exposes commands that can be implemented reliably with the embedded JGit engine and Android file access. It does not pretend to support commands that require a separately installed native Git executable, desktop external tools, Git LFS, credential-helper programs, remote-helper executables, multiple worktrees, or functionality absent from JGit.

Some complex command-line flags have a focused subset matching the operations supported by the embedded API. Entering an unsupported flag returns an error or usage message rather than silently executing a different operation.
