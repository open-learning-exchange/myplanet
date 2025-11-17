# Dead Code Review – myPlanet Android

## Overview
- Ran Android Lint (`UnusedResources`, `UnusedIds`, `UnusedDeclaration`) via `./gradlew :app:lintReportDefaultDebug` — no automatic findings.
- Used custom ripgrep scripts to enumerate every Kotlin class/object/enum, layout, drawable, color, dimension, and string resource. All but one group of resources were referenced either in Kotlin source, other resources, or manifest entries.
- Identified a set of legacy custom theme attributes (`metaButtonBarStyle`, `metaButtonBarButtonStyle`) defined in `res/values/attrs.xml` and only referenced inside the `FullscreenTheme` style. Because no Kotlin/Java code calls `obtainStyledAttributes(R.styleable.ButtonBarContainerTheme, …)` and no Android component queries those attrs, they have no effect and qualify as dead code.

## Confirmed dead code removed
| Type | Location | Notes |
| ---- | -------- | ----- |
| XML attrs & style items | `res/values/attrs.xml`, `res/values/styles.xml`, `res/values-night/styles.xml` | Custom `ButtonBarContainerTheme` + `metaButtonBar*` items were never read. Removing the declare-styleable block and the redundant items has no functional impact.

## Kotlin/Java review
- Automated search showed every Kotlin `class`, `object`, and `enum` is referenced at least once. Hilt modules (`RepositoryModule`, `ServiceModule`, `NetworkModule`, `DatabaseModule`, `SharedPreferencesModule`) only appear by name once because they are discovered via annotation processing. **Do not delete them** even though text search reports a single reference.
- Parcelable companions named `CREATOR` are accessed through the Android runtime; they also appear to be single-use identifiers.

## XML resources review
- Layouts, drawables, colors, dimens, menus, and string resources are all referenced either directly (`R.layout.*`, `@layout/*`) or indirectly via view binding/includes.
- The only unused resources were the custom theme attrs noted above, which have been removed.

## Gradle & dependency review
- Every external dependency declared in `app/build.gradle` has direct usages in source code or layouts (e.g., ChipCloud, MPAndroidChart, MaterialDrawer, WorkManager, OSMDroid, Media3). No unused plugins or libraries were identified.

## Reflection / dynamic usage cautions
- Hilt/DI modules, WorkManager initializers, and `Parcelable.CREATOR` companions are instantiated via generated code or the Android framework. These symbols may appear unused in static searches but must remain.
- View binding references (e.g., `FragmentFooBinding`) hide the underlying layout names, so layouts referenced only via binding still count as used.

## Potential false positives to keep in mind
- Test-only or future-facing code paths (e.g., sync workers, multi-pane chat UI) may only run on tablets or behind feature flags (`beta_function` preference). Manual confirmation is required before removing anything guarded by those flags.
- Any resource referenced from `tools:` attributes is preview-only, but everything inspected here was tied to real runtime references.
