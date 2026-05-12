# Plan: AGP 9.x migration of `pl.droidsonroids.pitest` plugin

## Context

`pl.droidsonroids.pitest` 0.2.27 (`koral--/gradle-pitest-plugin`) is the only PIT Gradle plugin that supports `com.android.library` modules. It is the foundation of the TrailLens mutation-testing initiative documented in `/Users/mark/.claude/plans/import-this-plan-file-smooth-sutton.md` (KT-1…KT-14 in `${HOME}/.claude/standards/CODINGSTANDARDS-KOTLIN.md` section 15).

Upstream 0.2.27 fails at project configuration time on AGP 9.x:

```text
A problem occurred configuring project ':lib'.
> Could not get unknown property 'libraryVariants' for object of type
  com.android.build.gradle.internal.dsl.LibraryExtensionImpl$AgpDecorated.
```

The root cause is in `src/main/groovy/pl/droidsonroids/gradle/pitest/PitestPlugin.groovy:119-132`:

```groovy
project.afterEvaluate {
    if (pitestExtension.mainSourceSets.empty()) {
        pitestExtension.mainSourceSets.set(project.android.sourceSets.main as Set<AndroidSourceSet>)
    }
    if (pitestExtension.testSourceSets.empty()) {
        pitestExtension.testSourceSets.set(project.android.sourceSets.test as Set<AndroidSourceSet>)
    }

    project.plugins.withType(AppPlugin) { createPitestTasks(project.android.applicationVariants) }
    project.plugins.withType(LibraryPlugin) { createPitestTasks(project.android.libraryVariants) }
    project.plugins.withType(DynamicFeaturePlugin) { createPitestTasks(project.android.applicationVariants) }
    project.plugins.withType(TestPlugin) { createPitestTasks(project.android.testVariants) }
    addPitDependencies()
}
```

AGP 9.0 removed `BaseExtension.libraryVariants` / `applicationVariants` / `testVariants` (and the entire `BaseVariant` hierarchy). Code must migrate to the `AndroidComponentsExtension.onVariants {}` callback API introduced in AGP 7.0 and made authoritative in AGP 9.x.

This fork (`TrailLensCo/gradle-pitest-plugin`) exists to land that migration so TrailLens's `androidrestapi/lib` module (and the broader TrailLens mutation-testing pipeline) can run PIT on AGP 9.1.0 + Kotlin 2.3.20 + minSdk 26 + jvmTarget 21.

Arcmutate's `com.arcmutate:pitest-kotlin-plugin:1.5.0` is a separate PIT classpath plugin that improves Kotlin mutation behaviour (kills false survivors on inline functions, intrinsics, sealed `when` exhaustiveness). It is added to the `pitest` configuration of the Gradle plugin AFTER the Gradle plugin works. License is held by TrailLensCo and stored locally at `androidrestapi/arcmutate-licence.txt` (gitignored).

---

## Where things live

| Asset | Location |
|---|---|
| Upstream | https://github.com/koral--/gradle-pitest-plugin |
| TrailLensCo fork | https://github.com/TrailLensCo/gradle-pitest-plugin |
| Local clone (submodule) | `/Users/mark/src/traillensdev/gradle-pitest-plugin-fork` |
| Working branch (fork) | `topic/agp-9-migration` |
| Consumer project | `/Users/mark/src/traillensdev/androidrestapi/` (branch `topic/mutants`) |
| Arcmutate license | `/Users/mark/src/traillensdev/androidrestapi/arcmutate-licence.txt` (gitignored) |
| Parent plan | `/Users/mark/.claude/plans/import-this-plan-file-smooth-sutton.md` |
| Kotlin standards | `${HOME}/.claude/standards/CODINGSTANDARDS-KOTLIN.md` section 15 |

---

## Goal

Land a fork release (`0.2.28-traillens` or equivalent) of `pl.droidsonroids.pitest` that:

1. Builds against AGP 9.1.0 (and forward-compatible with AGP 9.2.x).
2. Produces a working `:lib:pitest` task on `com.android.library` modules using the new Variants API.
3. Continues to support `com.android.application` and `com.android.dynamic-feature` modules unchanged.
4. Continues to honour every existing `PitestPluginExtension` setting (`pitestVersion`, `junit5PluginVersion`, `mutators`, `excludedClasses`, `excludedMethods`, `targetClasses`, `targetTests`, `mutationThreshold`, `coverageThreshold`, `threads`, `timestampedReports`, `outputFormats`, `historyInputLocation`, `historyOutputLocation`, `avoidCallsTo`).
5. Is publishable to a TrailLens artifact repository so `androidrestapi/gradle/libs.versions.toml` can resolve it.

After the fork release, the secondary deliverable is wiring `androidrestapi`:

- Add the TrailLens artifact repo to `settings.gradle.kts` pluginManagement.
- Update `libs.versions.toml` `pitest-plugin` version to the new fork release.
- Uncomment the `alias(libs.plugins.pitest)` line and `pitest { }` config block in `lib/build.gradle.kts`.
- Uncomment `testImplementation(libs.kotest.property)`.
- Add `pitest 'com.arcmutate:pitest-kotlin-plugin:1.5.0'` to the `pitest` configuration so arcmutate's mutation improvements activate.
- Configure arcmutate license via the documented mechanism (env var or system property pointing at `arcmutate-licence.txt`).
- Regenerate `gradle/verification-metadata.xml` for the new dependency tree.
- Uncomment validate_android.sh step 4.5.
- Restore the kotest property smoke test at `lib/src/test/kotlin/com/traillenshq/api/kotest/KotestPropertyExample.kt`.

---

## Scope of the API migration

### Files to touch

| File | Why |
|---|---|
| `src/main/groovy/pl/droidsonroids/gradle/pitest/PitestPlugin.groovy` | Lines 119-132 entry point; entire `createPitestTasks(...)` method needs `BaseVariant` removed |
| `src/main/groovy/pl/droidsonroids/gradle/pitest/PitestTask.groovy` (likely) | Source-set / output references via `BaseVariant` |
| `src/main/groovy/pl/droidsonroids/gradle/pitest/PitestMockableAndroidJarTask.groovy` | `mockableAndroidJar` API moved to `AndroidComponentsExtension` |
| Test fixtures under `src/test/` | Update AGP test versions; add AGP 9.x fixture |
| `gradle.properties` / `build.gradle.kts` | Bump targeted AGP version range |

### API mapping

| Old (`BaseVariant`) | New (AGP 9.x Variants API) | Notes |
|---|---|---|
| `project.android.libraryVariants.all { v -> ... }` | `libraryAndroidComponents.onVariants(...) { v -> ... }` | `libraryAndroidComponents` is `project.extensions.getByType(LibraryAndroidComponentsExtension)` |
| `project.android.applicationVariants` | `applicationAndroidComponents.onVariants(...) { ... }` | Analogous for `ApplicationAndroidComponentsExtension` |
| `project.android.testVariants` | `androidComponents.onVariants` is no longer needed — `Variant` exposes `unitTest` / `androidTest` via `HasUnitTest` / `HasAndroidTest` | Adapt to new API |
| `BaseVariant.name` | `Variant.name` | Same |
| `BaseVariant.sourceSets` | `Variant.sources.kotlin / java / resources` | Replaced by `Sources` API |
| `BaseVariant.outputs` | `Variant.artifacts.get(SingleArtifact.AAR)` | For the runtime AAR |
| `BaseVariant.unitTestVariant` | `Variant as HasUnitTest`, then `.unitTest` | Cast pattern |
| `BaseVariant.javaCompileProvider` | `Variant.runtimeConfiguration` for classpath, `Variant.compileClasspath` deprecated | Different access |
| `android.bootClasspath` | `LibraryAndroidComponentsExtension.sdkComponents.bootClasspath` | Provider |

### Variant-iteration pattern

Old (broken):

```groovy
project.plugins.withType(LibraryPlugin) {
    createPitestTasks(project.android.libraryVariants)  // libraryVariants removed
}
```

New (works on AGP 9.x):

```groovy
def libraryAndroidComponents = project.extensions.findByType(LibraryAndroidComponentsExtension)
if (libraryAndroidComponents != null) {
    libraryAndroidComponents.onVariants(libraryAndroidComponents.selector().all()) { variant ->
        // create per-variant PIT task here, using variant.name + variant.sources + variant.artifacts
    }
}
```

The callback registration must happen at plugin-apply time (not inside `project.afterEvaluate`); `onVariants` itself is lazy.

---

## Phased execution

### Phase 1 — Investigation + decision sign-off (READ-ONLY)

**Goal:** Document every `BaseVariant` API usage in the plugin codebase + identify the AGP 9.x replacement for each. Produce a migration map. Send back to the user for sign-off before writing code.

**Steps:**

1. `grep -rnE "BaseVariant|libraryVariants|applicationVariants|testVariants|unitTestVariant|sourceSets|mockableAndroidJar|bootClasspath" src/`
2. For each match, record file:line + old API + new API in a `MIGRATION_MAP.md` (uncommitted working artifact).
3. Identify any usage that has no direct replacement and would need a redesign (e.g., classpath assembly).
4. Sign-off gate: present the map to the user before writing any Groovy.

**Output:** `MIGRATION_MAP.md` (working artifact, ~100 lines).

### Phase 2 — Migration implementation

**Goal:** Update the Groovy source to use the new Variants API.

**Steps:**

1. Add AGP 9.x as the targeted compile-against version in `gradle.properties` / `build.gradle.kts`. Verify the plugin still compiles against AGP 8.x as well (cross-compatibility).
2. Update `PitestPlugin.groovy` `apply()` method to use `AndroidComponentsExtension.onVariants {}` instead of `project.android.<variants>`.
3. Update `createPitestTasks(...)` to accept the new `Variant` type. Rename if helpful.
4. Update `PitestTask.groovy` and `PitestMockableAndroidJarTask.groovy` to read source sets via `Variant.sources` and artifacts via `Variant.artifacts.get(...)`.
5. Verify the `unitTestVariant` lookup still works — `(variant as HasUnitTest).unitTest` for libraries.

### Phase 3 — Fork testing against androidrestapi

**Goal:** Confirm the fork works against the actual TrailLens consumer.

**Steps:**

1. Publish the fork to `mavenLocal()`: `./gradlew publishToMavenLocal`.
2. In `androidrestapi/settings.gradle.kts`, add `mavenLocal()` to `pluginManagement.repositories` (temporary).
3. In `androidrestapi/gradle/libs.versions.toml`, point `pitest-plugin` at the local snapshot version.
4. Run `./gradlew :lib:pitest --no-build-cache` from `androidrestapi`.
5. Expect: PIT runs, produces `lib/build/reports/pitest/index.html`, mutation score reported.
6. If anything fails, iterate on Phases 1+2.

### Phase 4 — Arcmutate integration

**Goal:** Add `com.arcmutate:pitest-kotlin-plugin:1.5.0` to the PIT classpath via `dependencies { pitest '...' }` in `androidrestapi/lib/build.gradle.kts`.

**Steps:**

1. Configure license file path via documented env var or system property (consult arcmutate's docs).
2. Add the classpath plugin dependency.
3. Re-run `:lib:pitest` and confirm survivor count drops on Kotlin-idiom mutators (inline functions, sealed `when`, intrinsics).
4. Capture the per-package mutation score delta in a measurement note.

### Phase 5 — Publish the fork to TrailLens artifact repo

**Goal:** Make the fork consumable by other TrailLens projects (not just via `mavenLocal()`).

**Steps:**

1. Identify the TrailLens artifact repo (likely GitHub Packages under `TrailLensCo`, or an internal Sonatype/Artifactory if one exists).
2. Configure the fork's `build.gradle.kts` publishing block to target that repo.
3. Cut a release version (`0.2.28-traillens` or `0.2.27.1` — coordinate with upstream version numbering).
4. Publish.
5. Update `androidrestapi/settings.gradle.kts` `pluginManagement.repositories` to include the TrailLens repo with credentials sourced from `~/.gradle/gradle.properties`.

### Phase 6 — Wire androidrestapi (re-activate Phase 4-7 of the parent plan)

**Goal:** Land the work that was blocked in the parent plan.

**Steps:**

1. Uncomment `alias(libs.plugins.pitest)` + the `pitest { }` block in `androidrestapi/lib/build.gradle.kts`.
2. Uncomment `testImplementation(libs.kotest.property)`.
3. Restore `KotestPropertyExample.kt`.
4. Add arcmutate dependency line in `androidrestapi/lib/build.gradle.kts` `dependencies { }`:
   ```kotlin
   pitest("com.arcmutate:pitest-kotlin-plugin:1.5.0")
   ```
5. Regenerate `gradle/verification-metadata.xml` via `./gradlew :lib:test :lib:pitest --write-verification-metadata sha256 --no-configuration-cache`.
6. Uncomment validate_android.sh step 4.5.
7. Verify `./gradlew :lib:pitest --no-build-cache` produces a mutation report with score ≥ 75% global, all KT-rules satisfied.
8. Commit on `topic/mutants` (the parent plan's branch).

### Phase 7 — Parent-plan Phase 5 + 6 + 7

Once Phase 6 above completes, the parent plan's Phase 5 (bulk test rewrite, ~70-100 files), Phase 6 (MUTATION_REPORT.md), and Phase 7 (CI cache + artifact upload) become executable. Pick up from `/Users/mark/.claude/plans/import-this-plan-file-smooth-sutton.md`.

---

## Critical files to read first

| File | Why |
|---|---|
| `src/main/groovy/pl/droidsonroids/gradle/pitest/PitestPlugin.groovy` | Entry point; lines 119-132 are the immediate failure site |
| `src/main/groovy/pl/droidsonroids/gradle/pitest/PitestTask.groovy` | Per-variant task config; uses BaseVariant heavily |
| `src/main/groovy/pl/droidsonroids/gradle/pitest/PitestMockableAndroidJarTask.groovy` | Mockable Android jar generation; AGP-version-dependent code path |
| Upstream issue tracker | `https://github.com/koral--/gradle-pitest-plugin/issues` |
| AGP 9.0 release notes | `https://developer.android.com/build/releases/agp-9-0-0-release-notes` (variants API removal) |
| `AndroidComponentsExtension` API | `https://developer.android.com/reference/tools/gradle-api/9.1/com/android/build/api/variant/AndroidComponentsExtension` |

---

## Verification

### Per-phase verification

- **Phase 1:** `MIGRATION_MAP.md` lists every BaseVariant API usage with replacement.
- **Phase 2:** The fork builds (`./gradlew build`) and unit tests pass against AGP 9.1.0 + 8.7.x fixtures.
- **Phase 3:** `./gradlew :lib:pitest --no-build-cache` from `androidrestapi` succeeds with the fork on `mavenLocal()`.
- **Phase 4:** Mutation score with arcmutate is measurably better than without (compare two reports).
- **Phase 5:** A non-developer machine can pull and run `:lib:pitest` using only the TrailLens artifact repo (no `mavenLocal()`).
- **Phase 6:** parent plan's Verification Checklist items 1, 2, 3 pass on `androidrestapi/topic/mutants`.

### End-to-end gate

`./gradlew :lib:pitest --no-build-cache` from `androidrestapi` produces `lib/build/reports/pitest/index.html` with a global mutation score ≥ 75% (per KT-1), zero validator failures from `kotlin-validate` skill.

---

## Rollback / escape hatch

- **Per-phase:** Each phase is a separate commit on `topic/agp-9-migration` in this fork. Revert any single commit cleanly.
- **Whole initiative:** If the migration proves intractable (e.g., AGP 9.x removes an API with no equivalent), the parent plan has a documented fallback: drop the mutation gate from KT-1, keep KT-2…KT-14 + Kover. The fork can stay parked at its current state.
- **Upstream merge:** If `koral--/gradle-pitest-plugin` lands its own AGP 9.x fix before this fork ships, abandon the fork and consume the upstream release directly. Submit any improvements made here as upstream PRs.

---

## Open risks

1. **AGP 9.x APIs may not cover every BaseVariant feature the plugin uses.** For example, the plugin's mockable Android jar path may rely on internal AGP plumbing that the new Variants API doesn't expose cleanly. Phase 1's investigation is the gate for this risk.
2. **Groovy + AGP Kotlin DSL friction.** The plugin source is Groovy; the new AGP APIs are Kotlin-first. Groovy can call them but type inference is weaker — expect to write more `as Type` casts.
3. **Test infrastructure.** The plugin's existing test fixtures (under `src/test/`) are AGP-version-pinned. We may need to add a parallel AGP 9.x test path without breaking the 8.x path.
4. **Cross-AGP-version compatibility.** If we tighten to AGP 9.x only, downstream consumers stuck on AGP 8.x (rare but possible) lose support. Cross-compatibility via `if (agpVersion >= 9) {...} else {...}` branches is feasible but adds complexity.
5. **License-file handling for arcmutate.** Arcmutate's docs reference license activation but don't fully document the env-var / property name. Phase 4 may need a support-ticket round-trip with arcmutate.
6. **Artifact-repo availability.** Phase 5 assumes a TrailLens artifact repo exists. If not, we use GitHub Packages (free for public + paid for private). Need to confirm what's actually wired into other TrailLens projects.
7. **Upstream evolution.** If `koral--` releases 0.2.28 with the AGP 9.x fix while this fork is in flight, we throw away in-progress work. Mitigate by checking the upstream tracker at the start of each Phase 1+2 session.

---

## Confidence: 75%

**Justification:**

- AGP's `AndroidComponentsExtension.onVariants` API is well-documented (developer.android.com), stable since AGP 7.0, and explicitly the replacement for `BaseVariant`. The migration path is known.
- The failure mode is precisely identified: `PitestPlugin.groovy:128` reads `project.android.libraryVariants`, a removed API.
- The upstream plugin source is small (estimated ~500 lines of Groovy across the 3 implicated files), single-developer maintainable.
- The fork's history shows the maintainer has handled major AGP/Gradle upgrades before (v0.2.2.6 "adapt to breaking changes in Gradle 9") — the path is well-trodden.
- Arcmutate adoption is well-isolated (it's a PIT classpath plugin, not a Gradle plugin) and its addition to a working Gradle plugin is mechanical.
- The local arcmutate license is already in hand (`androidrestapi/arcmutate-licence.txt`).

**Remaining 25% covers:**

- **The unknown size of the API surface.** Phase 1's investigation is needed before a more confident estimate. The 200-line estimate in the parent plan's escalation message may be low — `BaseVariant` touches source sets, outputs, classpath, mockable Android jar, unit test variants. Realistically the entire `PitestPlugin.groovy` + `PitestTask.groovy` may need adjustment.
- **Artifact-repo logistics.** Phase 5 might hit organizational friction (who owns the TrailLens artifact repo, who provisions credentials).
- **Arcmutate license activation.** Phase 4's exact mechanism is undocumented in the public arcmutate docs; may need support contact.
- **Cross-AGP-version testing.** If we want to keep AGP 8.x support, that doubles the test matrix.

If Phase 1's investigation shows the migration is constrained to <150 lines of Groovy changes (as opposed to a wholesale rewrite), confidence goes to ≥ 90%. If Phase 1 surfaces a missing API equivalent, confidence drops and we re-prompt the user.
