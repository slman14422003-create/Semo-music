# Added together with enabling isMinifyEnabled/isShrinkResources on the
# release build (see the comment in app/build.gradle.kts). This app maps
# every Firestore document manually (doc.getString()/getDouble()/etc. — see
# DebtsRepository.kt and MaterialsRepository.kt), never toObject(), so no
# app data class needs a keep rule. What still needs protecting is
# Firestore's own gRPC/protobuf transport, which does its own reflection
# internally regardless of how the app reads documents.

# PERF/SIZE: أُزيلت قواعد keep الشاملة لـ firestore وgrpc وprotobuf — مكتبات Firebase تأتي بقواعدها
# الخاصة (consumer rules) فيحتفظ R8 بما يلزم فقط ويقلّص الباقي. التطبيق لا يستعمل toObject().
# ⚠ اختبر نسخة release (قراءة/كتابة Firestore) قبل النشر؛ إن ظهر انهيار أعد القاعدة المعنية فقط.
-dontwarn com.google.protobuf.**
-dontwarn io.grpc.**

# Firebase — keep the annotation classes so no false "onCall" style
# reflection targets get stripped.
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod

# Kotlin coroutines — the play-services bridge (Task.await()) and Firestore
# callback flows use reflection to inspect suspend function continuations.
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# ── أمان + أداء (إضافة) ──────────────────────────────────────────────────────
# إزالة استدعاءات Log.v/d/i من نسخة release بالكامل: (1) أي نص تصحيح بيطبع
# أسماء زبائن أو مبالغ أو أخطاء داخلية ما بيوصل لـ logcat جهاز المستخدم، و(2)
# أقل عمل وقت التشغيل بالمسارات الساخنة (قوائم/مزامنة). Log.w/e بتضل لأنها
# بتفيد بتشخيص الأعطال الحقيقية.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# تتبّع الأعطال: اسم الملف + رقم السطر بالـ stack trace (مع mapping.txt اللي
# بيرفعه release.yml كـ artifact بتقدر تفك التشفير بالكامل).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
