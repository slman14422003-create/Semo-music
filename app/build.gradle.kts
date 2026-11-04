import java.io.File
import java.util.Properties

// ⚠️ MANUAL RELEASE NUMBER — bump this by +1 before every single manual
// build+upload of a new Release APK, and give the matching GitHub Release
// a tag ending in the SAME number (e.g. 3 → tag "v1.0.3"). See the long
// comment on versionCode/versionName below for why this matters — this is
// the one line in the whole project you now have to remember to touch by
// hand each release, since nothing builds this automatically anymore.
val MANUAL_VERSION_CODE = 1

plugins {
    id("com.android.application")
    // See root build.gradle.kts — required by Kotlin 2.0+ to compile any
    // @Composable code; replaces the old composeOptions{} version pin below.
    id("org.jetbrains.kotlin.plugin.compose")
}

// SIGNING: reads the real Play Store release keystore's credentials from
// keystore/keystore.properties.local — a file that is NEVER committed
// (see .gitignore) and must never leave this machine/CI secret store.
// If that file isn't present (a fresh checkout, a contributor's machine,
// a CI run with no signing secret configured), release builds fall back
// to Gradle's own auto-generated debug key exactly as before, so
// `assembleRelease` never breaks — it just produces a build that isn't
// the one Play will accept until the real keystore is available.
//
// BUILD FIX: this used to reference `java.util.Properties` inline
// (fully-qualified, no import). That fails specifically in an Android
// module's build.gradle.kts: AGP applies the `java-base` plugin
// internally, which makes Gradle's Kotlin DSL auto-generate a top-level
// `java` accessor (for JavaPluginExtension) on the script — and that
// accessor shadows the `java` *package* name, so `java.util.Properties`
// tried to resolve `.util` as a member of the accessor instead of the
// JDK package ("Unresolved reference: util"). Importing `Properties`
// explicitly (the standard pattern used everywhere else for this exact
// keystore-loading snippet) sidesteps the shadowing entirely.
val keystorePropertiesFile = rootProject.file("keystore/keystore.properties.local")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) keystorePropertiesFile.inputStream().use { load(it) }
}

// SIGNING (CI + محلي): مصدران لمفتاح التوقيع بالترتيب:
//  1) متغيرات بيئة يمررها release.yml من GitHub Secrets (SIGNING_STORE_FILE,
//     SIGNING_STORE_PASSWORD, SIGNING_KEY_ALIAS, SIGNING_KEY_PASSWORD) — هيك
//     مفتاح الإصدار ما بيتخزن بالمستودع أبدًا، وكل إصدار CI بيطلع بنفس التوقيع
//     (بدونه كان كل تشغيل يولّد مفتاح debug جديد ويفشل التحديث من داخل التطبيق).
//  2) keystore/keystore.properties.local على جهازك (ما بينرفع للمستودع).
// BUG FIX: الشرط القديم كان يكتفي بوجود ملف properties حتى لو ملف .jks نفسه
// غير موجود، فيفشل assembleRelease بـ "Keystore file not found". الآن لازم
// يوجد الملف الفعلي، وإلا يرجع للتوقيع الافتراضي (debug) بدون ما ينكسر البناء.
class ReleaseSigning(
    val storeFile: File,
    val storePassword: String,
    val keyAlias: String,
    val keyPassword: String
)

fun envOrNull(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }

val releaseSigning: ReleaseSigning? = run {
    val envFile = envOrNull("SIGNING_STORE_FILE")?.let { File(it) }
    val envStorePw = envOrNull("SIGNING_STORE_PASSWORD")
    val envAlias = envOrNull("SIGNING_KEY_ALIAS")
    val envKeyPw = envOrNull("SIGNING_KEY_PASSWORD")
    if (envFile != null && envFile.isFile && envStorePw != null && envAlias != null && envKeyPw != null) {
        return@run ReleaseSigning(envFile, envStorePw, envAlias, envKeyPw)
    }
    val localName = keystoreProperties.getProperty("STORE_FILE")
    val localFile = localName?.let { rootProject.file("keystore/$it") }
    val localStorePw = keystoreProperties.getProperty("STORE_PASSWORD")
    val localAlias = keystoreProperties.getProperty("KEY_ALIAS")
    val localKeyPw = keystoreProperties.getProperty("KEY_PASSWORD")
    if (localFile != null && localFile.isFile && localStorePw != null && localAlias != null && localKeyPw != null) {
        ReleaseSigning(localFile, localStorePw, localAlias, localKeyPw)
    } else {
        null
    }
}
val hasReleaseKeystore = releaseSigning != null

android {
    namespace = "com.shopmanager.app"
    // BUILD UPDATE (٢٠٢٦-٠٩): bumped to 37 (Android 17) — reached Platform
    // Stability in beta 3 and its stable public rollout was back in June
    // 2026, so this is a shipped stable SDK, not a preview. This is also
    // what unlocks the AndroidX bumps below that gate on it (core-ktx
    // 1.19.x, lifecycle 2.11.x, the current compose-bom line) — those
    // libraries' own AAR metadata requires compileSdk 37+ AGP 9.1+, which
    // this project now has. The app already renders fully edge-to-edge
    // with its own status/nav bar handling (see ui/theme/SystemBars.kt +
    // WindowCompat.setDecorFitsSystemWindows(window, false) in
    // MainActivity) rather than relying on the OS's default chrome, so the
    // targetSdk 35+ "edge-to-edge enforced" behavior change that trips up
    // apps which don't handle their own insets doesn't apply here.
    compileSdk = 37

    defaultConfig {
        applicationId = "com.shopmanager.app"
        minSdk = 24
        targetSdk = 37
        // MANUAL RELEASES: you're now building and uploading Release APKs
        // by hand (not through GitHub Actions/release.yml anymore), so
        // GITHUB_RUN_NUMBER never exists at build time — versionCode was
        // silently falling back to 1 on EVERY manual build, forever. That
        // breaks update-checking completely: the installed app always
        // reports itself as version 1, so it either never sees an update
        // (if the release tag's number isn't higher than 1) or nags with
        // "update available" forever even right after installing the
        // "latest" one (since the freshly-installed app is still, itself,
        // version 1 — it can never catch up).
        //
        // Fix: bump MANUAL_VERSION_CODE by hand, by at least +1, every
        // single time you build a new Release APK — and make sure the
        // GitHub Release's tag you create for it ends in that exact same
        // number (e.g. tag "v1.0.3" for MANUAL_VERSION_CODE = 3). Those
        // two numbers — this one, and the release tag's trailing number —
        // MUST match, because UpdateChecker.kt compares this app's own
        // versionCode against the number it reads back out of the tag.
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: MANUAL_VERSION_CODE
        versionName = "1.0.${System.getenv("GITHUB_RUN_NUMBER") ?: MANUAL_VERSION_CODE.toString()}"

        // GITHUB_REPOSITORY is another env var GitHub Actions sets
        // automatically ("owner/repo") — baked into BuildConfig so
        // UpdateChecker can build the GitHub Releases API URL itself with
        // no manifest URL ever needing to be typed in by hand anywhere
        // (see AdminPanelScreen's "رابط التحديثات" field, which now shows
        // this as a read-only default instead of a blank field to fill
        // in). Blank on a local/non-CI build — see the empty-string
        // handling already in UpdateChecker.
        buildConfigField("String", "GITHUB_REPO", "\"${System.getenv("GITHUB_REPOSITORY") ?: ""}\"")

        // SIZE FIX: Firestore/gRPC ship native .so libraries for four ABIs
        // (armeabi-v7a, arm64-v8a, x86, x86_64). x86/x86_64 exist only for
        // emulators — no real phone (not the Redmi A10, not the Samsung
        // A16, not any real ARM device) uses them, so they were pure dead
        // weight on every install. Restricting to the two ARM ABIs shrinks
        // the APK real users download/install without removing anything
        // that runs on real hardware. (If this app is ever distributed as
        // an .aab through Play, Play already does this splitting
        // automatically per-device and this filter is a no-op there — it
        // only matters for a directly-installed/sideloaded .apk.)
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    signingConfigs {
        releaseSigning?.let { info ->
            create("release") {
                storeFile = info.storeFile
                storePassword = info.storePassword
                keyAlias = info.keyAlias
                keyPassword = info.keyPassword
            }
        }
    }

    buildTypes {
        release {
            // PERF FIX: this was `false`, which meant the release build
            // shipped completely unshrunk — every class from every
            // dependency (Compose, Firestore, and especially
            // material-icons-extended, which alone bundles several
            // thousand icon classes for icons this app never uses) stayed
            // in the APK at full size, unobfuscated, un-dead-code-eliminated.
            // That's a heavier APK to download/install and more classes for
            // the runtime to load at cold start — exactly what shows up as
            // "needs a full-spec device" on an entry-level phone with a
            // slow eMMC and little RAM. The app has no reflection-based
            // Firestore mapping (every read uses doc.getString()/
            // getDouble(), never toObject()), so there's nothing here R8
            // could break by renaming/removing unused classes — safe to
            // shrink. See proguard-rules.pro for the few explicit keep
            // rules Firebase itself still needs.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // SIGNING: uses the real release keystore (see keystore/
            // shopmanager-release.jks + keystore.properties.local) when
            // it's present on this machine, so `assembleRelease`/
            // `bundleRelease` produce a build that's actually acceptable
            // to the Play Store — the same signing identity every future
            // update must keep using. Falls back to the debug key only
            // when the real keystore isn't available locally, so this
            // still never breaks a plain checkout/CI run with no signing
            // secret configured.
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    // JAVA VERSION (٢٠٢٦-٠٩): bumped from 21 to 25 — JDK 25 is now the
    // current LTS (released Sept 2025) and JDK 21's own free-updates
    // window closes this same month (Sept 2026), a year after JDK 25
    // shipped; AGP 9.4.0 / Gradle 9.7.1 / Kotlin 2.4.20 (see the root
    // build.gradle.kts and CI workflow) all officially build with and run
    // on JDK 25. minSdk 24 is unaffected: D8 still desugars whatever the
    // target device's runtime can't run natively, exactly as it did for
    // Java 21 language features.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_25
        targetCompatibility = JavaVersion.VERSION_25
    }

    // Kotlin's own JVM target (matches JAVA VERSION 25 above) now lives in
    // AGP 9 built-in Kotlin takes jvmTarget from compileOptions — see the
    // BUILD FIX comment there. The old android.kotlinOptions{} DSL that
    // used to sit here is a hard compile error as of this Kotlin version.

    // PERF (وقت البناء): assembleRelease كان يشغّل lintVitalRelease تلقائيًا قبل
    // التجميع، وrelease.yml أصلاً يشغّل lintRelease كاملًا بخطوة مستقلة غير
    // مانعة — فالفحص المزدوج كان يضيف دقائق بلا فائدة.
    lint {
        checkReleaseBuilds = false
    }

    // PERF (حجم APK): مكتبات AndroidX/Firebase/Material تشحن ترجمات لعشرات
    // اللغات. الواجهة نفسها عربية/إنجليزية، فبنحتفظ بهدول بس — النصوص
    // الداخلية للمكتبات بلغات ثانية بترجع للإنجليزي (الافتراضي) بدل ما تنحشر
    // بالـ APK.
    androidResources {
        localeFilters += listOf("ar", "en")
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // PERF (general Compose smoothness) — carried over from the old
    // composeOptions{}/freeCompilerArgs "experimentalStrongSkipping=true"
    // flag this replaces: strong skipping mode makes the Compose compiler
    // treat a composable as skippable even when one of its parameters is
    // an *unstable* type (a plain lambda built fresh each recomposition, a
    // List/Map straight from a ViewModel's StateFlow — exactly what every
    // list row's `onEdit`/`onDelete` and every screen's `persons`/
    // `materials` param look like in this app), comparing by
    // reference/equality at runtime instead of refusing to skip the whole
    // composable just because the type itself isn't provably stable. No
    // explicit flag is needed any more: the Kotlin Compose compiler plugin
    // (see plugins{} above and the composeCompiler{} block below this
    // android{} block) has shipped with strong skipping on by default
    // since the version this project now pins.

    packaging {
        resources {
            // PERF (حجم APK): ملفات وصفية للبناء/التصحيح ما لها أي دور وقت
            // التشغيل — DebugProbesKt.bin خاص بأدوات تصحيح الـ coroutines،
            // وبقية الملفات تراخيص/قوائم تبعيات مكررة.
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/INDEX.LIST",
                "/META-INF/*.version",
                "/DebugProbesKt.bin",
                "/kotlin-tooling-metadata.json"
            )
        }
        // BUILD FIX: "Unable to strip the following libraries, packaging
        // them as they are: libandroidx.graphics.path.so" — this is AGP
        // trying to run the NDK's `strip` tool on a prebuilt native lib
        // that ships inside an AndroidX artifact, but the build machine
        // (this project has no NDK/JNI code of its own, so no NDK is
        // installed here or in CI) doesn't have that tool available. AGP
        // then just packages the lib unstripped and prints it as a
        // warning — build output still succeeds either way, but the
        // warning is noise on every build. Telling AGP up front to keep
        // debug symbols for this lib (rather than attempt-then-fall-back)
        // skips the failed strip attempt entirely, so the warning is gone
        // and the packaged .apk is byte-for-byte the same as before.
        jniLibs {
            keepDebugSymbols += "**/libandroidx.graphics.path.so"
        }
    }
}

// AGP 9 built-in Kotlin: الـ jvmTarget لـ Kotlin يُؤخذ تلقائيًا من
// compileOptions.targetCompatibility (VERSION_25 أعلاه)، فلا حاجة لكتلة
// kotlin { compilerOptions { ... } } المنفصلة.

// Config surface for the Kotlin Compose compiler plugin (see plugins{}
// above) — deliberately empty: strong skipping mode is on by default at
// this plugin version already (see the comment on buildFeatures above),
// so there's nothing here that needs to be turned on explicitly.
composeCompiler {
}

dependencies {
    // BUILD UPDATE (٢٠٢٦-٠٩, latest stable, verified individually): every
    // androidx/Compose/Firebase/Kotlin dependency in this file was bumped
    // from what it pinned before to its own current stable release, not
    // just the BOMs. Nothing here is an alpha/beta/RC.
    implementation(project(":blurview"))
    implementation("androidx.core:core-ktx:1.19.1")
    // Standard AndroidX SplashScreen API — shows a static app icon on a
    // flat background immediately at cold start instead of a blank/white
    // starting window, and is kept on screen (see MainActivity) until the
    // first real frame is fully ready. This is what fixes the startup
    // jitter/flash.
    implementation("androidx.core:core-splashscreen:1.2.0")
    // PERF (general smoothness, release builds): ART can run an app off an
    // ahead-of-time "baseline profile" instead of interpreting/JIT-warming
    // every class from scratch on first use — this is what makes cold
    // start and the first few seconds of scrolling/animating noticeably
    // smoother, especially on a slow-flash low-RAM phone. Jetpack Compose's
    // own artifacts (compose-ui, material3, ...) already ship a baseline
    // profile bundled inside their AARs, but ART only ever installs and
    // uses it if this dependency is present in the final APK — without it,
    // that shipped profile just sits unused and this app was always
    // running the slow interpreted/JIT-only path for Compose's own
    // internals, on every single install, not just this app's own code.
    // No source changes needed for this to take effect (Play/adb install
    // both trigger ProfileInstaller automatically on first run) and it
    // only affects release-signed installs — a plain Android Studio "Run"
    // (debug build) never benefits from this, or from R8/minify below;
    // always judge real-world smoothness from an installed *release* APK,
    // never a debug run.
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    implementation("androidx.metrics:metrics-performance:1.0.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    // FEATURE ADDED (فتح القفل بالبصمة/الوجه): BiometricPrompt needs a
    // FragmentActivity (MainActivity now extends it). fragment-ktx is pinned
    // explicitly because biometric 1.1.0 would otherwise pull an old fragment
    // 1.2.x that predates the activity 1.13 line used here.
    implementation("androidx.fragment:fragment-ktx:1.8.9")
    implementation("androidx.biometric:biometric:1.1.0")
    // BUILD UPDATE: previously pinned to 2.10.0 because lifecycle 2.11.0's
    // AAR metadata requires compileSdk 37 + AGP 9.1.0+, which this project
    // didn't have yet. Now that compileSdk is 37 and AGP is 9.4.0 (see
    // android{} above and the root build.gradle.kts), that requirement is
    // met and the pin is no longer needed — bumped straight to the current
    // stable release.
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    // collectAsStateWithLifecycle(): stops collecting UI state (and, through
    // WhileSubscribed, the Firestore listeners behind it) while the app is in
    // the background, instead of keeping every screen's flows hot.
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.navigation:navigation-compose:2.10.2")

    // BUILD UPDATE: latest stable Compose BOM as of this update — brings in
    // Compose UI/Foundation/Material3/Runtime 1.11.x across the board (see
    // the per-artifact versions on developer.android.com/jetpack/androidx/
    // versions/stable-channel). All existing composeOptions/BOM-driven code
    // in this project (PullToRefreshBox etc.) stays source-compatible.
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Firebase — initialized manually via FirebaseOptions (no google-services.json / plugin needed)
    val firebaseBom = platform("com.google.firebase:firebase-bom:34.17.0")
    implementation(firebaseBom)
    // BUILD FIX: "Could not find com.google.firebase:firebase-firestore-ktx:"
    // (empty version, same for firebase-common-ktx) — Firebase stopped
    // releasing the standalone -ktx modules and dropped them from the BoM
    // entirely as of BoM v34.0.0 (this project pins 34.17.0), so there's no
    // version left here for Gradle to resolve against. The KTX extension
    // APIs were merged into these same main modules back in BoM 32.5.0+
    // under the same package names, so no source change is needed — every
    // Firebase import in this project (FirebaseModule.kt etc.) already
    // uses the plain com.google.firebase(.firestore) package, never a
    // .ktx sub-package.
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-common")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.11.0")

    // Free periodic background check for new debts / shortage-list changes
    // (see data/notifications/BackgroundSyncWorker.kt) — no server or paid
    // Firebase plan needed, unlike a real Cloud-Function-triggered push.
    implementation("androidx.work:work-runtime-ktx:2.11.2")

    // In-app WebView (دليل الاستخدام / help screen). androidx.webkit gives
    // access to the WebViewFeature/WebSettingsCompat compat-shims needed to
    // correctly force-dark WebView content and check per-feature support
    // across API levels (the plain android.webkit APIs for this only
    // stabilized piecemeal from API 29 through 33, so a raw SDK check alone
    // is not reliable on Android 11/12 devices — this library picks the
    // right mechanism at runtime).
    implementation("androidx.webkit:webkit:1.16.0")

    testImplementation("junit:junit:4.13.2")
}
