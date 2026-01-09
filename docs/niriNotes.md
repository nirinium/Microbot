# Niriniums's Development Notes

## Git Workflow

### Syncing with Microbot Upstream Repository

Use this single command to sync with the original Microbot repository:



```bash
git sync-upstream
```
 When there is a new client version available us this command.

**This command will:**
1. Fetch from upstream
2. Merge `upstream/main` into your current branch
3. Push to your fork

**Note:** If there are merge conflicts (like with `gradle.properties`), the process will pause to let you resolve them. After resolving conflicts, complete the sync with:
```bash
git push origin main
```

**Alternative:** For a cleaner history using rebase, you can create `git sync-upstream-rebase`.