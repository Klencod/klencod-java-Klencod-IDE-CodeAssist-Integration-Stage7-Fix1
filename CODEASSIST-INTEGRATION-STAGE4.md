# CodeAssist engine integration — Stage 4

## Completed in this workspace

- Promoted the staged CodeAssist engine modules into the Klencod root Gradle build as ordinary subprojects.
- Added the upstream module project-directory mapping and restricted Maven repositories required by the Kotlin compiler artifacts.
- Adopted the upstream Gradle 9.5.1 wrapper and Kotlin 2.4.20 / AGP 9.2.1 version catalog so the engine modules and host use one plugin train.
- Added the upstream buildSrc convention scripts and AGP-aware `build-logic` included build required for Kotlin compiler ART instrumentation.
- Applied `dev.ide.kotlinc-art` to the Klencod Android application so the Kotlin compiler runtime receives the upstream ART bytecode compatibility passes.
- Added app runtime dependencies for `:android-support`, `:build-engine`, `:jvm-build`, Java/Kotlin compilers, project model and required in-process D8/R8/apksig/bundletool/Guava/BouncyCastle libraries.
- Added the upstream AAPT2 native libraries for x86, x86_64, arm64-v8a and armeabi-v7a.
- Added `android.jar`, `core-lambda-stubs.jar`, and the upstream debug keystore provisioning assets.
- Added Gradle asset tasks for the stripped platform jar, Kotlin compiler resource bundle and Kotlin stdlib.
- Added `KlencodCodeAssistBuildRuntime`, which provisions the on-device SDK paths and constructs CodeAssist's `AndroidBuildSystem` with the in-process toolchain and Kotlin compiler. It exposes build-graph creation for a supplied CodeAssist `Project` and `BuildRequest`.

## Deliberate boundary / not yet complete

- No project importer is connected to Klencod's project-open flow yet. The upstream `GradleProjectImporter` depends on CodeAssist's IDE-core services and model-applier wiring; the staged model libraries alone do not provide that host.
- No task-graph executor is connected to Klencod's existing build UI or `BuildForegroundService` yet. The adapter creates a graph only; it does not execute it.
- The current runtime adapter does not yet wire CodeAssist's forked R8/D8 or forked Kotlin compiler workers. In-process compilation/dexing can be memory intensive on 4 GB devices; this must be addressed before treating the path as production-ready.
- Kotlin compiler ART bytecode instrumentation and full resource-home compatibility must be validated against the exact app DEX output.
- Attempted `./gradlew --version`; the wrapper could not download Gradle 9.5.1 because this environment cannot resolve `services.gradle.org` (`UnknownHostException`). There is no installed `gradle` command. Consequently no Gradle configuration, module compilation, or APK build was run; source-level/static wiring checks only.

## Compatibility decision

The Klencod host build now uses Gradle 9.5.1, Kotlin 2.4.20 and AGP 9.2.1 to match the source modules. This is a deliberate migration, not proof that all existing app build behavior remains compatible. Run `./gradlew :app:assembleDebug` in an environment with Android SDK 36 and network access before relying on the result.

## Licensing

The upstream GPL-3.0-or-later LICENSE and LICENSE-EXCEPTION remain in `codeassist-engine/`. The exception applies only to the modules identified upstream as exception-licensed; it does not relicense all CodeAssist source. Distribution of an app containing GPL-covered source must be reviewed for GPL compliance.
