plugins {
    // AGP 9.x: Kotlin مدمج داخل AGP (built-in Kotlin) والـ DSL الجديد مفعّلان
    // افتراضيًا، فلا حاجة لإضافة org.jetbrains.kotlin.android إطلاقًا. نسخة
    // Kotlin (2.4.20) تُحدَّد عبر إضافة الـ Compose أدناه (تجلب
    // kotlin-gradle-plugin بنفس النسخة).
    id("com.android.application") version "9.4.0" apply false
    id("com.android.library") version "9.4.0" apply false
    // Since Kotlin 2.0, the Compose compiler is no longer a separate
    // artifact pinned via composeOptions{kotlinCompilerExtensionVersion}
    // (see the old comment removed from app/build.gradle.kts) — Kotlin
    // ships and versions it itself as this plugin instead, matching the
    // Kotlin version above exactly.
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
}
