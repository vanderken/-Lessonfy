# Contributing to Lessonfy

## Branches
- `main` is stable code. Never push straight to it.
- New feature: `feature/<issue-number>-<short-name>` (example: `feature/1-user-login`)
- Bug fix: `fix/<issue-number>-<short-name>`

## Commit messages
Format: `type(area): what you did`
Types: `feat` (new feature), `fix` (bug fix), `docs` (documentation), `chore` (housekeeping)
Example: `feat(auth): add login form scaffold`

## Pull requests
1. Open a pull request from your branch into `main`.
2. Write `Closes #<issue-number>` in the description.
3. Ask your assigned reviewer to check it.
4. Merge only after at least one approval.

## Secrets
Never commit real passwords, tokens or `.env` files.