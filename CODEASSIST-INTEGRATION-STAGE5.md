# Klencod / CodeAssist integration — Stage 5

## Implemented in this stage

### 1. Static Gradle importer is now present in the Klencod app source

Added the following importer/parser sources under `app/src/main/kotlin/dev/ide/core/`:

- `gradle/GradleProjectImporter.kt`
- `gradle/GradleImport.kt`
- `gradle/GradleScript.kt`
- `gradle/GradleVersionCatalog.kt`
- `gradle/GradleKmp.kt`
- `gradle/KlencodRuntimeDetector.kt`
- `kmp/KmpShapes.kt`
- `sync/ExternalProjectMarker.kt`

The copied parser does not execute Gradle scripts. It reads conventional settings/build scripts, Android blocks, dependency declarations, and version catalogs into a neutral `ExternalProjectModel`. Dynamic DSL, arbitrary plugins, custom task logic, and computed script values remain outside its supported surface. Unsupported/unresolved constructs are reported as importer warnings where the upstream parser recognizes them.

The Gradle KMP platform choice now uses a tiny ART/JVM runtime detector rather than importing the full IDE SDK-download manager and its network/UI dependencies.

### 2. Klencod project-model host

Added `KlencodGradleProjectImporter.kt`:

- Opens a `PlatformCore` and a `ProjectModelStore` for the selected project directory.
- Registers Android module types/codecs through `AndroidSupport.register`.
- Registers Kotlin, Kotlin BuildConfig, and Compose Resources facet codecs.
- Registers a minimal `java-lib` module type for imported plain JVM modules.
- Runs `GradleProjectImporter.resolve` and applies the returned snapshot with `ExternalModelApplier`.
- Saves only Klencod's `.platform` model metadata; it does not rewrite the user's Gradle scripts or project layout.
- Closes the project model and platform resources when the import session ends.

The imported model is an IDE-side projection. The original `settings.gradle[.kts]`, `build.gradle[.kts]`, version catalog and source files remain the source of truth.

### 3. Task graph execution adapter

Extended `KlencodCodeAssistBuildRuntime` with `executeGraph`:

- Uses CodeAssist `BuildCache` for task fingerprints.
- Uses `TaskExecutorImpl` to execute the graph.
- Streams plain logs, structured log entries, diagnostics, and task status callbacks to the host.
- Defaults to one concurrent task to limit peak memory on the 4 GB target device (callers can request at most two).
- Checks a live `shouldCancel` callback at task/progress cancellation checkpoints; the owning coroutine/job should also be cancelled for prompt unwinding.

### 4. Local build orchestration API

Added `KlencodLocalAndroidBuildSession`:

1. Imports a standard Gradle project into the internal model.
2. Finds the first `android-app` module.
3. Creates a debug `ASSEMBLE` request.
4. Creates the Android build graph.
5. Executes the graph with one worker and persistent task cache.
6. Searches the module's `build/outputs/apk` folder for the resulting APK after a successful outcome.

This is a local build path separate from the existing remote GitHub/Flutter build workflow.

## Important boundaries that remain

- The new session is an application-level API, not yet connected to a visible Klencod toolbar/menu action or to `BuildForegroundService`. The existing foreground service and remote-build state/log stores were intentionally left untouched to avoid mixing local Android builds with the remote Flutter workflow.
- The parser is deliberately static and partial. It does not evaluate Gradle Kotlin/Groovy scripts, resolve arbitrary plugin behavior, or guarantee support for KSP, Compose compiler plugin combinations, custom convention plugins, buildSrc logic, or every AGP DSL form.
- The minimal `java-lib` type enables model import. It is not a replacement for a complete IDE Java-support plugin registration/feature set.
- The in-process D8/R8 and Kotlin compiler path remains memory-sensitive. One worker reduces simultaneous pressure but does not isolate compiler heaps or guarantee success on a 4 GB device. Forked worker support and runtime memory-pressure handling are still outstanding.
- No APK build was run in this environment. The Gradle wrapper still requires downloading Gradle 9.5.1, and network DNS is unavailable here. Therefore this stage has not passed Gradle configuration, Kotlin compilation, DEXing, signing, or device validation.

## Static checks performed

- The standalone Kotlin sources `GradleScript.kt`, `GradleVersionCatalog.kt`, `KlencodRuntimeDetector.kt`, and `ExternalProjectMarker.kt` compile together with the installed Kotlin compiler.
- Checked the copied importer references: its only `ExternalProjectMarker` calls are `exists`, `clear`, and `write`, all implemented by the Klencod marker.
- Checked the Gradle KMP source no longer imports `JdkManager`.
- Full Android app/module compilation was not possible without Gradle 9.5.1 and its dependencies.

## Licensing

The imported engine/parser sources remain covered by their upstream GPL-3.0-or-later terms and the included `LICENSE` / `LICENSE-EXCEPTION` files. The exception does not automatically cover every source module. This stage does not change the licensing obligations of distributing an application containing GPL-covered CodeAssist code.
