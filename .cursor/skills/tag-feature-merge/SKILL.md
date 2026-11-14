---
name: tag-feature-merge
description: After a milestone/feature PR is merged into main, create and push the annotated Git tag (m0–m10) on the merge commit. Use when the user asks to tag a merge into main, tag a milestone, or cut mN after a PR merge.
disable-model-invocation: true
---

# Tag feature merge into main

After a milestone PR lands on `main`, cut the annotated Git tag `mN`
(`m0`…`m10`). Do not merge the PR, edit docs, or create a GitHub Release
unless the user asks.

## Preconditions

1. `git fetch origin main` (and tags if needed).
2. Identify the milestone `N` from, in order:
   - the user (`tag m5`),
   - the merge commit subject (`…/milestone/mN-…`),
   - or `docs/milestones/MN.md`.
3. Resolve the **merge commit** on `main`:
   `Merge pull request #<n> from …/milestone/mN-…`
   — not a feature-branch tip.
4. Stop if:
   - tag `mN` already exists (never `--force`),
   - the merge SHA is not an ancestor of `main`,
   - or `N` is outside `0`…`10`.

## Annotated message

English, factual, same shape as existing tags (`m4`):

```
MN — <short title>

<2–4 sentences: what shipped + one notable fact>. Merged in PR #<n>.
```

Derive title and body from `docs/milestones/MN.md` and/or the PR title.
End the body with `Merged in PR #<n>.`

## Create and push

If the user did not clearly say to proceed (e.g. only “prepare the tag”),
show the planned tag name, SHA, and message and wait for confirmation.

```bash
git tag -a "mN" <merge-sha> -m "$(cat <<'EOF'
MN — Title

Body. Merged in PR #X.
EOF
)"
git push origin "mN"
```

## Verify and report

1. `git show mN` — tag points at the expected merge commit.
2. Report: tag name, SHA, annotation, and that it was pushed to `origin`.

## Out of scope

- Merging the PR
- Editing `docs/milestones/`, README, or ADRs
- Semver tags, moving tags, or GitHub Releases
