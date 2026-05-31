# Launch drafts

Copy-paste ready content for promoting `spring-saga-kt`. These files are not
part of the library distribution; they're checked in so the launch plan is
reproducible.

## What's here

| File                                | Use for                                                  |
|-------------------------------------|----------------------------------------------------------|
| `RELEASE_NOTES_v0.1.0.md`           | GitHub Releases page body. Paste verbatim.               |
| `launch-post-en.md`                 | dev.to / Medium / personal blog (English).               |
| `launch-post-ko.md`                 | velog / brunch / tistory (한국어).                       |
| `awesome-kotlin-PR.md`              | KotlinBy/awesome-kotlin PR — entry text + commands.      |
| `kotlin-weekly-submission.md`       | kotlinweekly.net submit form.                            |
| `reddit-r-kotlin.md`                | r/Kotlin (and r/SpringBoot cross-post) submission.       |
| `okky-post.md`                      | OKKY "프로젝트" 게시판.                                  |
| `geeknews-post.md`                  | news.hada.io "Show GN" 형식.                             |

## Suggested order

1. **Create the GitHub Release.** Either:
   - Run `gh auth login && gh release create v0.1.0 --title "v0.1.0 — initial public release" --notes-file drafts/RELEASE_NOTES_v0.1.0.md`, or
   - On <https://github.com/BK202503/bk-spring-saga/releases/new>, pick the
     `v0.1.0` tag, paste the file body, publish.
2. **Publish the launch posts.** EN to dev.to, KO to velog. Cross-link them.
3. **Submit to curated lists.** awesome-kotlin PR, Kotlin Weekly form.
4. **Community posts.** r/Kotlin first; OKKY + GeekNews next day in Korean.
5. **Cross-post.** r/SpringBoot 24h later. Tweet/LinkedIn with the dev.to link.

Don't blast all five channels in the same hour — staggering 12–24h apart
catches different audiences and avoids looking spammy.
