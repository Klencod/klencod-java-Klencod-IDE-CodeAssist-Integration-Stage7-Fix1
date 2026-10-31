# Klencod IDE — CodeAssist engine integration analysis (Stage 2)

Status: analysis completed; engine source has NOT yet been integrated or compiled.

## 1. Correct integration boundary

The Android build pipeline is not a standalone file. CodeAssist composes:

- `:android-support` — Android build graph and Android-specific tasks.
- `:build-api` + `:build-engine` — BuildSystem SPI, task graph, execution, cache, diagnostics and cancellation.
- `:jvm-build` — Java/Kotlin module composition and shared module tasks used by Android builds.
- `:lang-jdt` — ECJ-based Java compilation.
- `:lang-kotlin` — K2 Kotlin compilation, which itself requires `:kotlin-compiler-deps` and `:intellij-psi-host`.
- `:lang-xml`, `:language-api`, `:index-api` — Android resource/XML parsing and resource declaration integration.
- `:project-model-api`, `:project-model-impl`, `:vfs-api`, `:platform-core`, `:model-api` — project model, filesystem abstractions, extension points and message bus.
- `:jvm-interp` — transitively exposed by `:jvm-build`'s API in this CodeAssist revision.

The `:ide-core` host then supplies the project-scoped `BuildService`, Kotlin compiler, boot classpaths, signing resolver, dependency state, SDK table, caches, and project model. Copying only `android-support` would compile neither independently nor provide a usable host.

## 2. Android runtime requirements confirmed in source

`AndroidBuildSystem.inProcess(...)` performs D8/R8 and APK signing in-process. Native AAPT2 is still launched as a subprocess. CodeAssist's Android launcher provisions:

- `android.jar` and `core-lambda-stubs.jar` into app-private storage;
- `libaapt2.so` for each ABI under `app/src/main/jniLibs`, then resolves it from `ApplicationInfo.nativeLibraryDir`;
- R8/apksig/bundletool/Guava/BouncyCastle as host runtime dependencies/assets as appropriate;
- a debug keystore asset;
- the Kotlin compiler and its resources/plugin jars.

`AndroidSdk.forDevice(androidJar, nativeLibDir)` points AAPT2 to `nativeLibraryDir/libaapt2.so`. The source comments explicitly rely on Android's executable-binary policy: the native executable is packaged as a native library and run from the installed native library directory. This is not equivalent to copying an arbitrary executable into writable app storage.

The old Klencod local-build assets and `jniLibs` were removed in Stage 1. No CodeAssist native tools or Android SDK assets have been added to Klencod yet.

## 3. Gradle project import is a separate integration problem

CodeAssist does not simply hand an arbitrary Gradle project directory to `AndroidBuildSystem`. It first creates its own `Workspace/Project/Module` model. Its host registers importers (`GradleProjectImporter` and `GradleModelImporter`) and uses a model-export/sync path. The build graph consumes that model, resolved dependency metadata, source roots, module facets, variants, SDK selection and classpaths.

Klencod currently has its own project/file representation and does not contain that model/import/sync infrastructure. A safe adapter must be designed before wiring the build button; otherwise the new engine could receive incomplete module/resource/dependency data and produce misleading failures.

## 4. Gradle/Kotlin compatibility

Klencod currently uses Gradle 8.13, AGP 8.13.0 and Kotlin 2.4.0. This CodeAssist checkout uses Gradle 9.5.1 and Kotlin 2.4.20 (and its own included `build-logic`, version catalog and custom conventions). The engine modules also use Kotlin Multiplatform source sets and project conventions. These versions and conventions cannot be mixed by blindly copying module `build.gradle.kts` files into Klencod.

No Gradle distribution is installed/cached in the analysis environment and no `gradle` executable is available. Therefore no Gradle configuration or Kotlin compilation test could be run in this stage.

## 5. Klencod components deliberately preserved

The following are not the removed local APK engine and remain untouched:

- `BuildForegroundService` — GitHub Actions remote-build foreground service, notification and progress broadcasts.
- `FlutterBuildApi` — remote GitHub Actions build/poll/download workflow.
- `BuildStateStore` — persistence for remote build state.
- `BuildLogStore` — remote build log persistence.

Their references remain in `MainActivity` and the manifest. They must not be removed as part of local-engine migration.

## 6. Licensing gate

The supplied CodeAssist source tree contains a root `LICENSE` identifying it as GNU GPL version 3. Copying/embedding and distributing its engine source as part of Klencod is not compatible with treating that combined distributed build as closed-source without a separate licensing arrangement or a compliant distribution plan. Temporary local study and distributing a binary containing copied/modified GPL code are materially different situations. The project must keep the license and applicable notices for any temporary copied source, and a future closed-source release needs a legally sound separation/replacement/licensing decision before distribution.

## 7. Next implementation sequence

1. Create a narrow Klencod host adapter for CodeAssist's project model and `BuildSystem` contracts; do not import `IdeServices`, Compose UI, agent, VCS or the full IDE host.
2. Port the minimal compile/runtime module closure with Gradle plugin versions and source-set conventions made explicit. Keep original notices and provenance for every copied module.
3. Add a Gradle project importer/sync adapter that maps Klencod project metadata into CodeAssist model objects and resolves external libraries before graph creation.
4. Provision only the Android runtime assets actually needed by the selected pipeline, beginning with `android.jar`, AAPT2 per ABI, R8, apksig and signing support. Verify the binary execution path on Android 13/API 33+.
5. Connect a new local-build entry point without disturbing the remote GitHub Actions service, then add unit/fixture builds before device tests.

## 8. Stage 2 result

This stage establishes the actual integration boundary and runtime requirements from the supplied CodeAssist source. No CodeAssist source files have been copied into Klencod yet; no engine is connected; no APK build has been claimed. The project remains a Stage-1 transition snapshot plus this analysis document.
