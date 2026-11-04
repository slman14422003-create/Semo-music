# تحويل الموسوعة إلى تطبيق APK — دليل الاستخدام

## ما الذي أُضيف للمشروع؟
- مجلد `android/` — مشروع أندرويد كامل (Java) يعرض موقعكم داخل WebView حديث
  (يستخدم WebViewAssetLoader بدل file:// حتى يعمل Service Worker بشكل صحيح).
- أيقونات adaptive icon متطورة بكل الأحجام، مولّدة من `icons/icon-512.png`.
- `.github/workflows/android-release.yml` — يبني APK وينشئ Release تلقائيًا
  عند كل `push` إلى main/master (أو يدويًا عبر "Run workflow").

## خطوات الرفع
1. ارفع محتوى هذا الملف (بما فيه مجلد `android/` و`.github/`) إلى مستودع GitHub الخاص بكم،
   بنفس البنية الحالية (لا تغيّروا مكان أي مجلد).
2. من تبويب **Actions** في GitHub، تأكد أن الصلاحيات تسمح بإنشاء Release:
   Settings → Actions → General → Workflow permissions → اختر
   **"Read and write permissions"**.
3. عند أول `push`، سيبدأ العمل تلقائيًا، وستجدون الـ APK جاهزًا تحت تبويب **Releases**.

## توقيع دائم للتطبيق (مهم لتحديثات لاحقة)
بدون هذه الخطوة، كل إصدار جديد يُوقَّع بمفتاح مختلف تلقائيًا، فيضطر المستخدمون لحذف
النسخة القديمة قبل تثبيت الجديدة. لتفادي هذا، أنشئوا مفتاح توقيع ثابت مرة واحدة:

```bash
keytool -genkeypair -v -keystore release.keystore.jks -alias herbal \
  -keyalg RSA -keysize 2048 -validity 10000
# ثم حوّلوه إلى base64:
base64 -w0 release.keystore.jks > keystore_base64.txt
```

بعدها أضيفوا في **Settings → Secrets and variables → Actions** أربعة أسرار:
| الاسم | القيمة |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | محتوى ملف keystore_base64.txt |
| `ANDROID_KEYSTORE_PASSWORD` | كلمة سر الـ keystore التي اخترتموها |
| `ANDROID_KEY_ALIAS` | `herbal` (أو الاسم الذي اخترتموه) |
| `ANDROID_KEY_PASSWORD` | كلمة سر المفتاح |

احتفظوا بملف `release.keystore.jks` في مكان آمن؛ فقدانه يعني عدم القدرة على نشر
تحديثات لنفس التطبيق مستقبلًا.

## تعديل هوية التطبيق (اختياري)
- اسم الحزمة الحالي: `com.salman.herbalencyclopedia` (في `android/app/build.gradle`
  و`AndroidManifest.xml` وبنية مجلدات `java/`). غيّروه قبل النشر النهائي على المتجر
  إذا رغبتم باسم مختلف.
- اسم التطبيق المعروض: `android/app/src/main/res/values/strings.xml` → `app_name`.

## ملاحظة عن Firebase
ملف `js/firebase-config.js` يحتوي على مفاتيح Firebase الحالية للموقع، وقد تم نسخه
كما هو ضمن أصول التطبيق. هذه المفاتيح عمومًا آمنة للكشف العلني (هذا هو المتعارف عليه
في مشاريع Firebase على الويب)، لكن تأكدوا أن قواعد Firestore/الأمان لديكم مضبوطة بشكل
صحيح لأن أي شخص يمكنه رؤية هذه المفاتيح داخل ملف الـ APK.
