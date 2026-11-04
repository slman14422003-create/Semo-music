# التحويل إلى تطبيق أندرويد أصلي (Java)

تم تحويل هذا المشروع بالكامل من تطبيق ويب (PWA) يعمل داخل WebView
إلى **تطبيق أندرويد أصلي حقيقي** مكتوب بلغة Java، بدون أي WebView
وبدون أي ملفات HTML أو CSS.

## ما الذي تغيّر

### حُذف نهائياً
- `index.html`, `compare.html`, `help.html`, `offline.html`, `privacy.html`
- `sw.js` (Service Worker), `manifest.json` (PWA manifest), `version.json`
- مجلد `android/app/main/assets` بالكامل (كان يحتوي نسخة من الملفات أعلاه)
- كل أكواد WebView في `MainActivity.java` (WebViewAssetLoader، WebChromeClient،
  onJsAlert/onJsConfirm/onJsPrompt، إلخ)
- صلاحية الكاميرا و`FileProvider` (كانتا مطلوبتين فقط لرفع الملفات من نموذج ويب)
- `network_security_config.xml` (كان يخص تحميل صفحات https داخل WebView)

### أُضيف
مصدر البيانات لا يزال **نفس مشروع Firestore** (`semoharbs`) الذي كان
تطبيق الويب يقرأ منه، لكن الآن يُقرأ مباشرة بكود Java عبر REST API
(`FirestoreRestClient.java`) بدون حاجة لملف `google-services.json` غير
المتوفر، وبدون أي JavaScript.

| ملف | الوظيفة |
|---|---|
| `model/Herb.java`, `model/Category.java` | نماذج البيانات (نفس أسماء حقول Firestore الأصلية: name, category_id, benefits, warnings, harms, usage, notes, image_url) |
| `data/FirestoreRestClient.java` | جلب المجموعات من Firestore عبر HTTP مباشرة |
| `data/HerbRepository.java` | تحميل البيانات في خيط خلفي + كاش في الذاكرة |
| `adapter/HerbAdapter.java` | عرض قائمة الأعشاب في RecyclerView (مع DiffUtil) |
| `MainActivity.java` | الشاشة الرئيسية: بحث فوري + فلترة حسب التصنيف (Chips) |
| `HerbDetailActivity.java` | تفاصيل عشبة واحدة (صورة + الفوائد/التحذيرات/الأضرار/الاستخدام/ملاحظات) |
| `CompareActivity.java` | مقارنة عشبتين جنباً إلى جنب (بديل compare.html) |
| `AboutActivity.java` | شاشة "حول التطبيق" (بديل help.html/privacy.html) |

تحميل الصور (`image_url`) يتم عبر مكتبة **Glide** (أُضيفت في `build.gradle`)
بدل `<img>` في HTML.

## المشاكل التي كانت موجودة وتم تجاوزها بالتحويل
- الاعتماد الكامل على WebView يعني أن أي خطأ JavaScript أو مشكلة في
  Service Worker (تخزين مؤقت خاطئ، صفحات لا تُحدَّث...) كانت تكسر
  التطبيق كله؛ الآن هذه الطبقة غير موجودة إطلاقاً.
- منطق "عرض غير متصل" كان يعتمد على التقاط أخطاء تحميل صفحة HTML؛
  استُبدل بمعالجة أخطاء شبكة حقيقية (`try/catch` + `IOException`) مع
  رسالة خطأ واضحة وزر إعادة محاولة.
- حوارات JS (`alert/confirm/prompt`) المخصصة لتنسيقها لم تعد ضرورية
  لأن كل الواجهة أصبحت Views أندرويد أصلية من الأساس.

## ما لم يُنقل (نطاق الجلسة)
- ميزات تسجيل الدخول/التعديل على البيانات من `firebase-auth.js` غير
  مُفعّلة في هذه النسخة (التطبيق للقراءة فقط حالياً). يمكن إضافتها
  لاحقاً عبر Firebase Auth REST API بنفس الأسلوب المتّبع في
  `FirestoreRestClient`.
- لم يتم تشغيل Gradle فعلياً لبناء APK داخل بيئة التوليد (لا يوجد
  اتصال إنترنت متاح هنا)، لذا يُنصح ببناء المشروع محلياً أو عبر
  GitHub Actions الموجود أصلاً (`.github/workflows/android-release.yml`)
  للتأكد النهائي، ومراجعة أي أخطاء توافق إصدارات قد تظهر.
- قواعد أمان Firestore (Security Rules) يجب أن تسمح بالقراءة العامة
  لمجموعتي `herbs` و`categories` حتى يعمل `FirestoreRestClient` بدون
  مصادقة، تماماً كما كان الحال مع تطبيق الويب.
