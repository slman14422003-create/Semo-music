# قائمة إصدار احترافي — موسوعة الأعشاب

## 1. التوقيع (الخطوة الأهم — نفّذها مرة واحدة فقط قبل أي إصدار أول)
- [ ] أنشئ مفتاح توقيع دائم واحد **ولن تُعيد إنشاءه أبداً بعدها**:
  ```
  keytool -genkeypair -v -keystore herbal-release.jks -alias herbal \
    -keyalg RSA -keysize 2048 -validity 10000
  ```
- [ ] احفظ ملف `.jks` وكلمتي السر في مكان آمن (مدير كلمات سر) — فقدانه يعني
      عدم القدرة على نشر أي تحديث مستقبلي لنفس التطبيق على Play أبداً.
- [ ] **للبناء المحلي (Android Studio):** انسخ `app/keystore.properties.example`
      إلى `app/keystore.properties` وعبّئ القيم الحقيقية. لن تحتاج لمس أي
      ملف gradle بعدها لأي إصدار قادم.
- [ ] **لـ GitHub Actions:** أضف 4 أسرار في Settings → Secrets → Actions:
      `ANDROID_KEYSTORE_BASE64` (ناتج `base64 -w0 herbal-release.jks`)،
      `ANDROID_KEYSTORE_PASSWORD`، `ANDROID_KEY_ALIAS`، `ANDROID_KEY_PASSWORD`.
      بدونها سيستمر الـ workflow بالعمل (نسخة احتياطية مؤقتة عبر cache) لكن
      بلا ضمان دائم — راجع التعليقات داخل `.github/workflows/android-release.yml`.

## 2. رفع رقم الإصدار
- [ ] `versionName` في `app/build.gradle.kts` — رقم يظهر للمستخدم (مثال: 2.1.0).
- [ ] `versionCode` — رقم صحيح يجب أن يزيد عن كل إصدار سابق دائماً (يُدار
      تلقائياً في CI عبر `github.run_number`، أو يدوياً محلياً).

## 3. قبل البناء
- [ ] نفّذ Build → Make Project في Android Studio وتأكد من عدم وجود أخطاء.
- [ ] شغّل التطبيق على جهاز/محاكي حقيقي وجرّب المسارات الأساسية (تصفح،
      بحث، مفضلة، تسجيل دخول لو موجود).
- [ ] بدّل بين وضعي الأداء (عالٍ/اقتصادي) من الإعدادات وتأكد من الفرق
      البصري الفعلي وسلاسة الحركة في كليهما.
- [ ] راجع `proguard-rules.pro` إن أضفت مكتبة جديدة تحتاج قواعد keep خاصة
      (minify مُفعَّل في الإصدار، فأي انعكاس/reflection غير محمي قد يُكسَر).

## 4. البناء
- [ ] **AAB (لِرفع Play Store):** `gradle :app:bundleRelease` — الملف
      الناتج في `app/build/outputs/bundle/release/*.aab`. هذه الصيغة **إلزامية**
      لأي نشر جديد على Play، وليس APK.
- [ ] **APK (للتوزيع المباشر/الاختبار خارج المتجر):** `gradle :app:assembleRelease`.
- [ ] تحقّق من التوقيع فعلياً قبل الرفع:
  ```
  apksigner verify --print-certs app/build/outputs/apk/release/*.apk
  ```
  تأكد أن البصمة (fingerprint) الظاهرة مطابقة لبصمة مفتاحك الدائم، وأنها
  **نفسها** في كل إصدار قادم.

## 5. بعد كل إصدار ناجح
- [ ] احتفظ بنسخة من ملف `mapping.txt` (الناتج مع R8/ProGuard في
      `app/build/outputs/mapping/release/`) لكل إصدار تنشره — بدونه لا يمكن
      فك رموز تقارير الأعطال (crash reports) القادمة من Play Console.
- [ ] لو أضفت `google-services.json` لاحقاً (Play Integrity/FCM حقيقي)،
      تأكد أنه مُستثنى في `.gitignore` (موجود بالفعل) ولا يُرفع للمستودع.

## ملاحظة حول أرقام الإصدارات في ملفات gradle
حدّثت AGP/Kotlin/Compose BOM ومكتبات المشروع لأحدث إصدارات مستقرة أعرفها
بثقة عالية من بيانات تدريبي. لكن هذه الأدوات تصدر إصدارات جديدة باستمرار،
وبيئتي هنا بلا اتصال إنترنت فلم أستطع التحقق من "الأحدث فعلياً اليوم" —
قبل إصدار نهائي مهم، افتح Android Studio → Tools → **Upgrade Assistant** (أو
"Check for updates" على تبعيات gradle) للتأكد من عدم وجود إصدار أحدث صدر
بعد ذلك.
