### Simplify build.yml branch filter with double-star glob

The single-star `'*'` glob in GitHub Actions does not match branch names containing `/`, so namespaced branches like `claude/fix`, `codex/task`, or `dependabot/gradle/foo` require separate patterns. Replacing `'*'` with `'**'` matches all branch names regardless of path separators, making the five explicit namespace patterns redundant and preventing future omissions when new namespaced branches are introduced.

:codex-file-citation[codex-file-citation]{line_range_start=5 line_range_end=12 path=.github/workflows/build.yml git_url="https://github.com/open-learning-exchange/myplanet/blob/master/.github/workflows/build.yml#L5-L12"}

:::task-stub{title="Replace '*' with '**' in build.yml branch filter"}
1. In `.github/workflows/build.yml`, replace `'*'` on line 6 with `'**'`
2. Remove the now-redundant explicit patterns on lines 8-12 (`claude/**`, `codex/**`, `dependabot/**`, `jules/**`, `*-codex/**`)
3. Keep only `'**'` and `'!master'` in the branches list
:::

### Add concurrency group to release.yml

Without a `concurrency` key, multiple rapid pushes to master can trigger parallel release workflows that race to publish artifacts, create GitHub releases, and upload to the Play Store. Adding a concurrency group with `cancel-in-progress: false` ensures releases are queued rather than duplicated or conflicting.

:codex-file-citation[codex-file-citation]{line_range_start=3 line_range_end=7 path=.github/workflows/release.yml git_url="https://github.com/open-learning-exchange/myplanet/blob/master/.github/workflows/release.yml#L3-L7"}

:::task-stub{title="Add concurrency group to release.yml"}
1. Add a `concurrency` block after the `on:` trigger block (after line 7) in `.github/workflows/release.yml`
2. Set `group: release-${{ github.ref }}` to group runs by branch
3. Set `cancel-in-progress: false` so queued releases complete rather than get cancelled
:::

### Add concurrency group with cancel-in-progress to build.yml

When a developer pushes multiple commits to the same branch in quick succession, each push triggers a separate build workflow run. Earlier in-progress builds become obsolete immediately. Adding `cancel-in-progress: true` automatically cancels stale runs, saving CI runner minutes.

:codex-file-citation[codex-file-citation]{line_range_start=3 line_range_end=12 path=.github/workflows/build.yml git_url="https://github.com/open-learning-exchange/myplanet/blob/master/.github/workflows/build.yml#L3-L12"}

:::task-stub{title="Add concurrency group with cancel-in-progress to build.yml"}
1. Add a `concurrency` block after the `on:` trigger block (after line 12) in `.github/workflows/build.yml`
2. Set `group: build-${{ github.ref }}` to group runs by branch
3. Set `cancel-in-progress: true` so superseded branch builds are cancelled
:::

### Add timeout-minutes to release.yml job

Without an explicit `timeout-minutes`, a stuck Gradle daemon, hung network call, or unresponsive Play Store API will consume runner minutes up to GitHub's default 6-hour limit. The release build currently completes in ~5 minutes, so a 30-minute cap is generous while still catching genuine hangs.

:codex-file-citation[codex-file-citation]{line_range_start=10 line_range_end=16 path=.github/workflows/release.yml git_url="https://github.com/open-learning-exchange/myplanet/blob/master/.github/workflows/release.yml#L10-L16"}

:::task-stub{title="Add timeout-minutes to release.yml job"}
1. Add `timeout-minutes: 30` to the `release` job definition in `.github/workflows/release.yml` (after line 12, alongside `runs-on`)
:::

### Add timeout-minutes to build.yml job

The debug build currently completes in under 5 minutes. Without an explicit timeout, a stuck build would consume runner time up to the 6-hour default. A 20-minute cap catches hangs without interfering with normal builds.

:codex-file-citation[codex-file-citation]{line_range_start=15 line_range_end=21 path=.github/workflows/build.yml git_url="https://github.com/open-learning-exchange/myplanet/blob/master/.github/workflows/build.yml#L15-L21"}

:::task-stub{title="Add timeout-minutes to build.yml job"}
1. Add `timeout-minutes: 20` to the `build` job definition in `.github/workflows/build.yml` (after line 17, alongside `runs-on`)
:::

### Add explicit permissions block to release.yml

Without an explicit `permissions` block the workflow inherits the repository's default token permissions, which are typically broader than needed. The release job requires `contents: write` for creating GitHub releases and tags. Declaring only the minimum required permissions follows the principle of least privilege and limits the blast radius if a third-party action is compromised.

:codex-file-citation[codex-file-citation]{line_range_start=9 line_range_end=12 path=.github/workflows/release.yml git_url="https://github.com/open-learning-exchange/myplanet/blob/master/.github/workflows/release.yml#L9-L12"}

:::task-stub{title="Add explicit permissions block to release.yml"}
1. Add a top-level `permissions` block before the `jobs:` key in `.github/workflows/release.yml`
2. Set `contents: write` (required for creating releases and uploading assets)
3. Leave all other permissions at their implicit default of `none`
:::

### Add explicit permissions block to build.yml

The build workflow only checks out code and runs Gradle. It does not create releases, write packages, or interact with issues or pull requests. Declaring `contents: read` as the sole permission reduces the attack surface.

:codex-file-citation[codex-file-citation]{line_range_start=14 line_range_end=17 path=.github/workflows/build.yml git_url="https://github.com/open-learning-exchange/myplanet/blob/master/.github/workflows/build.yml#L14-L17"}

:::task-stub{title="Add explicit permissions block to build.yml"}
1. Add a top-level `permissions` block before the `jobs:` key in `.github/workflows/build.yml`
2. Set `contents: read` (required only for checkout)
3. Leave all other permissions at their implicit default of `none`
:::

### Add explicit JDK setup with actions/setup-java in both workflows

Both workflows rely on the JDK pre-installed on the `ubuntu-24.04` runner image. If the runner image updates its default JDK version, builds may break silently or produce subtly different bytecode. Adding `actions/setup-java` with an explicit version pins the JDK and makes the dependency visible and reproducible.

:codex-file-citation[codex-file-citation]{line_range_start=25 line_range_end=28 path=.github/workflows/release.yml git_url="https://github.com/open-learning-exchange/myplanet/blob/master/.github/workflows/release.yml#L25-L28"}
:codex-file-citation[codex-file-citation]{line_range_start=22 line_range_end=26 path=.github/workflows/build.yml git_url="https://github.com/open-learning-exchange/myplanet/blob/master/.github/workflows/build.yml#L22-L26"}

:::task-stub{title="Add actions/setup-java step to both workflows"}
1. Add a `Set up JDK 17` step before the `setup gradle` step in `.github/workflows/release.yml` (before line 39)
2. Add the same step in `.github/workflows/build.yml` (before line 37)
3. Use `actions/setup-java@v4` with `distribution: 'temurin'` and `java-version: '17'`
:::

### Set Gradle cache to read-only in build.yml

Branch builds currently write to the shared Gradle remote cache via the `gradle/actions/setup-gradle` action. This can cause cache pollution where entries from short-lived feature branches evict or conflict with entries from master. Setting `cache-read-only: true` lets branch builds benefit from the cache populated by release builds without polluting it. The redundant `cache-disabled` and `cache-write-only` parameters (both already `false` by default) can be removed.

:codex-file-citation[codex-file-citation]{line_range_start=37 line_range_end=43 path=.github/workflows/build.yml git_url="https://github.com/open-learning-exchange/myplanet/blob/master/.github/workflows/build.yml#L37-L43"}

:::task-stub{title="Set Gradle cache read-only in build.yml"}
1. In `.github/workflows/build.yml`, change `cache-read-only: false` to `cache-read-only: true`
2. Remove the `cache-disabled: false` and `cache-write-only: false` lines as they are redundant defaults
3. Optionally remove `gradle-version: wrapper` as it is also the default
:::

### Cache the treehouses CLI installation in the Discord notification step

The Discord notification step in release.yml runs `sudo npm install -g @treehouses/cli` on every release, adding network latency and a potential failure point from npm registry availability. Caching the global npm directory or replacing the step with a direct Discord webhook `curl` call eliminates this repeated installation overhead.

:codex-file-citation[codex-file-citation]{line_range_start=144 line_range_end=151 path=.github/workflows/release.yml git_url="https://github.com/open-learning-exchange/myplanet/blob/master/.github/workflows/release.yml#L144-L151"}

:::task-stub{title="Cache or replace npm install in Discord notification step"}
1. Option A: Add an `actions/cache` step before the Discord step that caches the npm global prefix directory (e.g., `/usr/local/lib/node_modules`) with a static key like `treehouses-cli-v1`
2. Option A continued: Wrap the `npm install` in a conditional that checks if the binary already exists (`which treehouses || sudo npm install -g @treehouses/cli`)
3. Option B (simpler): Replace the entire step with a direct `curl` POST to the Discord webhook API, removing the npm dependency entirely
:::
