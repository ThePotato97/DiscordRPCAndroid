---
description: Commits all current changes to git to save progress.
---

1. Stage all changes
   ```bash
   ssh-add -l
   git add .
   ```

2. Commit changes
   - Generate a concise but descriptive commit message summarizing the changes.
   - **MUST** follow [Conventional Commits](https://www.conventionalcommits.org/) format: `<type>: <description>`
   - Allowed types: `feat`, `fix`, `chore`, `refactor`, `docs`, `style`, `test`, `perf`.
   - Example: `fix: resolve race condition in MainScreen`
   - Example: `feat: add fuzzy search for app list`
   - Example: `chore: update build dependencies`
   ```bash
   git commit -m "[Conventional Commit Message]"
   ```