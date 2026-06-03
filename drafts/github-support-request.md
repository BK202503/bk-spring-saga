# GitHub Support request — purge dangling commit objects (PII)

**Submit at:** <https://support.github.com/contact?subject=Privacy+concern>
**Category:** Privacy / Account & Personal Data → "Remove sensitive data"

## Subject

```
Personal email leaked in dangling commit objects — please purge after force-push
```

## Body (copy-paste, fill the bracketed parts)

```
Hi GitHub Support,

I accidentally committed under my personal Gmail address (billkimjh@gmail.com)
to several repositories last week, then rewrote history to use my GitHub
noreply email (199436087+BK202503@users.noreply.github.com). All branch
tips and tag refs have been force-updated and the commits no longer appear
in any branch / tag / search-result. However, the original commit objects
are still reachable by direct SHA URL because they are referenced by PR
refs (refs/pull/*/head) or are otherwise un-garbage-collected.

Could you please purge the dangling commit objects in the locations below?
The email address is personally identifiable and I'd like it removed from
GitHub's storage as documented here:
https://docs.github.com/en/repositories/working-with-files/managing-files/removing-sensitive-data-from-a-repository

GitHub user:    BK202503
Personal email to remove:  billkimjh@gmail.com
Replacement email (already in current refs):
   199436087+BK202503@users.noreply.github.com

## Affected commit SHAs

### 1. spring-projects/spring-modulith  (upstream, referenced via PR #1714)
- 4647796e6d88692ec5d5a90a0b77763c744d0692
- 81b19236... (the earlier intermediate; please verify in your tooling)

URL examples that still return HTTP 200:
- https://github.com/spring-projects/spring-modulith/commit/4647796e6d88692ec5d5a90a0b77763c744d0692
- https://github.com/BK202503/spring-modulith/commit/4647796e6d88692ec5d5a90a0b77763c744d0692

### 2. Heapy/awesome-kotlin  (upstream, referenced via PR #1125)
- b8c209ad... (the original commit on the PR branch)

URL examples:
- https://github.com/Heapy/awesome-kotlin/commit/b8c209ad...
- https://github.com/BK202503/awesome-kotlin/commit/b8c209ad...

### 3. BK202503/bk-spring-saga  (my own repo — also being deleted+recreated)
- 07ef844f7dfab2a200064554f9bfe4c879e4879b
- ffb5d8c4c9e33be6c6c7e64dc67a962add5fb4c0
- bd57035b342d4932abaf3a2fa99b5020a7fe683f
- 4b01819e5a5f247fb1d78f01e132ac732ed101f9
- bfea37a255f57b94f84ce12bc0a5a791f1618aa3
- 2c81284b3e985870f4250695bff2780eb0568cd8

I am separately deleting and recreating BK202503/bk-spring-saga, so those
SHAs should disappear from my own repository on my end — but please
confirm whether the deletion also purges the underlying objects or if
they survive in any internal mirror / cache.

## What I'd like

1. The above commit objects purged from GitHub's storage so direct SHA
   URL lookups return 404 across all affected repositories.
2. If any of the above commits were captured into archives that GitHub
   maintains (commit search index, REST API caches), please ensure those
   are invalidated as well.
3. Confirmation when complete.

The open PRs themselves (PR #1714 on spring-projects/spring-modulith and
PR #1125 on Heapy/awesome-kotlin) should remain open — only the older
SHAs that were superseded by force-push need to be purged. The current
head SHAs (639eabd2... on #1714 and 09961820... on #1125) use the
noreply email and should be left as-is.

Thanks very much for the help.

Best,
BK202503
```

## After submitting

GitHub Support usually responds in 1–3 business days for privacy requests.
Once complete, run this to confirm:

```bash
for sha in 4647796e 81b19236 b8c209ad 07ef844 ffb5d8c bd57035 4b01819 bfea37a 2c81284; do
  for repo in spring-projects/spring-modulith Heapy/awesome-kotlin BK202503/bk-spring-saga; do
    code=$(curl -sS -o /dev/null -w "%{http_code}" "https://github.com/$repo/commit/$sha")
    [ "$code" = "404" ] || echo "$repo/$sha still HTTP $code"
  done
done
echo "All clean (no output above = every SHA returns 404)"
```
