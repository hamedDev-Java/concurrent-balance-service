<div dir="rtl" align="right">

# سرویس مدیریت موجودی همزمان (Concurrent Balance Service)

سرویس مدیریت موجودی حساب‌های کاربری با تمرکز بر صحت داده‌های مالی، ایمنی همزمانی نخ‌ها (Thread Safety)، توان‌انجام‌پذیری (Idempotency) و انتقال اتمیک و یکپارچه وجه (Atomic Transfers).

---

## ۱. معماری و اجزای اصلی سیستم (Architecture & Components)

این پروژه از معماری لایه‌ای استاندارد (Layered Architecture) بهره می‌برد:

* **لایه دامنه (Domain Layer):**
  * موجودیت حساب کاربری (`Account`): نگهداری شناسه حساب و موجودی، همراه با محاسبات حسابی امن برای جلوگیری از سرریز عددی (`Math.addExact` و `Math.subtractExact`) و قید سطح پایگاه داده (`@Check(constraints = "balance >= 0")`) جهت پیشگیری قطعی از منفی شدن موجودی.
  * موجودیت سابقه تراکنش (`TransactionRecord`): ذخیره اطلاعات هر تراکنش پردازش‌شده شامل شناسه یکتا (`transactionId`)، نوع عملیات (`OperationType`)، حساب مبدأ و مقصد، و مبلغ تراکنش.
* **لایه دسترسی داده (Repository Layer):**
  * مخزن داده حساب‌ها (`AccountRepository`): مجهز به کوئری اختصاصی قفل بدبینانه برای نوشتن (`Pessimistic Write Lock`) با انوتیشن `@Lock(LockModeType.PESSIMISTIC_WRITE)` که دستور `SELECT ... FOR UPDATE` را روی ردیف حساب در پایگاه داده اجرا می‌کند.
  * مخزن داده تراکنش‌ها (`TransactionRecordRepository`): ثبت و استعلام سابقه‌ها با قید یکتایی و پشتیبانی از صفحه‌بندی (`Pagination`).
* **لایه سرویس (Service Layer):**
  * پیاده‌سازی سرویس (`BalanceServiceImpl`): مدیریت تراکنش‌های مالی، کنترل توان‌انجام‌پذیری در درخواست‌های همزمان و اعمال الگوریتم قفل‌گذاری ترتیبی قطعی (Deterministic Lock Ordering) برای ممانعت از بن‌بست.
* **لایه واسط برنامه‌نویسی وب (REST API Layer):**
  * کنترلر مدیریت موجودی (`BalanceController`): اندپوینت‌های وب مجهز به اعتبارسنجی ورودی‌ها (`Bean Validation`) و مستندات تعاملی استاندارد اُپن‌ای‌پی‌آی (OpenAPI 3 / Swagger).
  * مدیریت سراسری خطاها (`GlobalExceptionHandler`): نگاشت استثناهای بیزینسی به کدهای وضعیت استاندارد پروتکل وب (`HTTP Status Codes`).

---

## ۲. ایمنی همزمانی و مدیریت نخ‌ها (Concurrency & Thread Safety)

### چگونه ایمنی نخ‌ها تضمین شده است؟
به جای اتکا به قفل‌های حافظه‌ای جاوا (`synchronized` یا `ReentrantLock`) که در صورت استقرار چند نسخه‌ای در کلاستر (Clustering / Horizontal Scaling) فاقد کارایی هستند، مدیریت همزمانی به **پایگاه داده رابطه‌ای (PostgreSQL)** واگذار شده است:
1. **قفل بدبینانه برای نوشتن (Pessimistic Write Lock):** هر تراکنش پیش از تغییر موجودی حساب، ردیف مربوطه را در پایگاه داده با دستور `SELECT ... FOR UPDATE` قفل می‌کند.
2. **عدم مسدودسازی حساب‌های مستقل (Independent Accounts):** تراکنش‌های روی حساب‌های مجزا (مثلاً حساب A و حساب B) قفل‌های جداگانه دریافت کرده و کاملاً به صورت موازی و مستقل از یکدیگر پردازش می‌شوند.
3. **سطح انزوای تراکنش (Transaction Isolation Level):** تراکنش‌ها با انزوای استاندارد `READ_COMMITTED` همراه با قفل بدبینانه اجرا می‌شوند که مانع از خواندن داده کثیف (Dirty Read)، به‌روزرسانی گم‌شده (Lost Update) و خواندن تکرارناپذیر (Non-repeatable Read) می‌گردد.

---

## ۳. تضمین توان‌انجام‌پذیری (Idempotency)

### جلوگیری از اعمال چندباره تراکنش‌های تکراری
هر عملیات مالی دارای شناسه یکتای تراکنش (`transactionId`) است:
1. **بررسی سریع اولیه (Fast-path Check):** ابتدا بررسی می‌شود که آیا این شناسه قبلاً با موفقیت اجرا شده است یا خیر؛ اگر با پارامترهای کاملاً یکسان ثبت شده باشد، بدون تغییر مجدد موجودی، بلافاصله پاسخ موفقیت‌آمیز برمی‌گرداند.
2. **بررسی دو مرحله‌ای زیر قفل (Double-Checked Locking):** در شرایطی که چند درخواست با شناسه یکسان به صورت همزمان ارسال شوند، پس از دریافت قفل در پایگاه داده، مجدداً وضعیت شناسه بررسی می‌شود تا هیچ اثر مالی مضاعفی ثبت نگردد.
3. **قید یکتایی در پایگاه داده (Unique Constraint):** ستون `transaction_id` کلید اصلی جدول سابقه‌ها است و از درج موازی رکوردهای هم‌نام در سطح پایگاه داده جلوگیری می‌کند.
4. **تناقض در پارامترها (Idempotency Conflict):** چنانچه شناسه‌ای تکراری با پارامترهای متفاوت (مبلغ یا حساب‌های دیگر) ارسال شود، سامانه با پرتاب خطای تعارض توان‌انجام‌پذیری (`IdempotencyConflictException`) درخواست را رد می‌کند.

---

## ۴. اتمیک بودن انتقال وجه و پیشگیری از بن‌بست (Transfer Atomicity & Deadlock Prevention)

### آیا در طراحی احتمال وقوع بن‌بست (Deadlock) وجود دارد؟
**خیر.** در انتقال وجه (`transfer`) بین دو حساب A و B، در صورتی که قفل‌ها بدون ترتیب مشخص دریافت شوند، سناریوی بن‌بست متقابل رخ می‌دهد:
* نخ ۱: انتقال از A به B (ابتدا A را قفل می‌کند و در انتظار B می‌ماند).
* نخ ۲: انتقال همزمان از B به A (ابتدا B را قفل می‌کند و در انتظار A می‌ماند).
* **نتیجه:** بن‌بست چرخه‌ای (Cyclic Deadlock).

### راهکار پیشگیری از بن‌بست با قفل‌گذاری ترتیبی (Deterministic Lock Ordering):
سرویس شناسه‌های حساب‌ها را پیش از درخواست قفل به ترتیب الفبایی مرتب می‌کند:
```java
String firstId = sourceAccountId.compareTo(destinationAccountId) < 0 ? sourceAccountId : destinationAccountId;
String secondId = sourceAccountId.compareTo(destinationAccountId) < 0 ? destinationAccountId : sourceAccountId;

Account firstAccount = accountRepository.findByIdForUpdate(firstId);
Account secondAccount = accountRepository.findByIdForUpdate(secondId);
```
به دلیل رعایت همیشگی این اولویت، تشکیل حلقه انتظار متقابل از نظر منطقی غیرممکن بوده و بن‌بست رخ نمی‌دهد.

### انتقال به همان حساب (Same Account Transfer):
در صورتی که حساب مبدأ و مقصد یکسان باشند (`sourceAccountId.equals(destinationAccountId)`)، عملیات فاقد اثر اقتصادی بوده و بلافاصله با خطای انتقال به خود (`SameAccountTransferException`) متوقف می‌شود تا قفل‌گذاری مکرر روی یک رکورد انجام نگیرد.

---

## ۵. مستندات واسط برنامه‌نویسی وب (REST API Endpoints)

تمامی اندپوینت‌ها تحت پیشوند `/api/v1` در دسترس هستند:

### ۱. ثبت حساب جدید (Create Account)
* **روش ارسال (Method):** `POST /api/v1/accounts`
* **بدنه درخواست (Request Body):**
  ```json
  {
    "accountId": "ACC-100",
    "initialBalance": 1000
  }
  ```
* **پاسخ موفق:** `201 Created`
* **خطاها:** `400 Bad Request` (موجودی اولیه منفی)، `409 Conflict` (شناسه حساب تکراری).

### ۲. استعلام موجودی حساب (Get Balance)
* **روش ارسال (Method):** `GET /api/v1/accounts/{accountId}/balance`
* **پاسخ موفق (200 OK):**
  ```json
  {
    "accountId": "ACC-100",
    "balance": 1000
  }
  ```
* **خطاها:** `404 Not Found` (عدم وجود حساب).

### ۳. واریز وجه به حساب (Credit)
* **روش ارسال (Method):** `POST /api/v1/accounts/{accountId}/credit`
* **بدنه درخواست (Request Body):**
  ```json
  {
    "amount": 500,
    "transactionId": "TX-CRED-101"
  }
  ```
* **پاسخ موفق:** `204 No Content`
* **خطاها:** `400 Bad Request` (مبلغ نامعتبر)، `404 Not Found`، `409 Conflict` (تعارض پارامتر در شناسه تراکنش تکراری).

### ۴. برداشت وجه از حساب (Debit)
* **روش ارسال (Method):** `POST /api/v1/accounts/{accountId}/debit`
* **بدنه درخواست (Request Body):**
  ```json
  {
    "amount": 300,
    "transactionId": "TX-DEB-202"
  }
  ```
* **پاسخ موفق:** `204 No Content`
* **خطاها:** `422 Unprocessable Entity` (کسری موجودی)، `400 Bad Request`، `409 Conflict`.

### ۵. انتقال اتمیک وجه بین دو حساب (Transfer)
* **روش ارسال (Method):** `POST /api/v1/transfers`
* **بدنه درخواست (Request Body):**
  ```json
  {
    "sourceAccountId": "ACC-A",
    "destinationAccountId": "ACC-B",
    "amount": 250,
    "transactionId": "TX-TR-303"
  }
  ```
* **پاسخ موفق:** `204 No Content`
* **خطاها:** `400 Bad Request` (انتقال به همان حساب یا مبلغ نامعتبر)، `422 Unprocessable Entity` (کسری موجودی مبدأ)، `404 Not Found`، `409 Conflict`.

### ۶. تاریخچه تراکنش‌های حساب با صفحه‌بندی (Paged Transaction History)
* **روش ارسال (Method):** `GET /api/v1/accounts/{accountId}/transactions?page=0&size=20&sort=createdAt,desc`
* **پاسخ موفق (200 OK):** بازگرداندن صفحه داده‌ها (`Page<TransactionResponse>`) شامل لیست تراکنش‌ها، تعداد کل عناصر (`totalElements`) و تعداد صفحات (`totalPages`).

---

## ۶. راه‌اندازی سریع با داکر (Docker & Docker Compose)

پروژه مجهز به ساختار چندمرحله‌ای بهینه (`Multi-stage Dockerfile`) است که فرآیند کامپایل را با Java 21 و Maven انجام داده و بسته نهایی را روی نسخه سبک JRE با کاربر غیرروت امن اجرا می‌کند.

### حالت اول: راه‌اندازی کامل کل سامانه (پایگاه‌داده و سرویس اسپرینگ‌بوت):
با یک دستور واحد، دیتابیس بالا آمده و پس از تایید سلامت (`Healthcheck`)، کانتینر اپلیکیشن ساخته شده و متصل می‌گردد:
```bash
docker compose up --build -d
```
پس از چند ثانیه، سرویس روی پورت ۸۰۸۰ و دیتابیس روی پورت ۵۴۳۲ آماده پذیرش درخواست خواهند بود.

### حالت دوم: اجرای محلی اپلیکیشن و تنها پایگاه داده روی داکر:
در صورتی که می‌خواهید اپلیکیشن را مستقیماً در IDE یا ترمینال اجرا و دیباگ کنید:
```bash
# ۱. اجرای تنها پایگاه داده
docker compose up postgres -d

# ۲. اجرای اپلیکیشن با مایون
mvn spring-boot:run
```

برای متوقف‌سازی سرویس‌ها:
```bash
docker compose down
```

---

## ۷. دسترسی به رابط کاربری تعاملی (Swagger UI)

پس از اجرای سرویس، مستندات زنده و ابزار آزمایش اندپوینت‌ها در آدرس‌های زیر در دسترس است:

* **رابط تعاملی کاربری (Swagger UI):**
  [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
* **سند مشخصات اُپن‌ای‌پی‌آی (OpenAPI JSON):**
  [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

---

## ۸. اجرای آزمون‌های خودکار و تست‌های همزمانی (Testing)

### اجرای آزمون‌ها با مایون (Maven):
```bash
mvn clean test
```

### تست‌های پوشش داده شده:
* **آزمون‌های واحد (`BalanceServiceUnitTest`):** اعتبارسنجی مبالغ نامعتبر، رد موجودی منفی، عدم وجود حساب، انتقال به خود، و تطابق پارامترهای توان‌انجام‌پذیری.
* **آزمون‌های یکپارچگی همزمانی (`BalanceServiceConcurrencyTest`):**
  * رقابت همزمان برای برداشت وجه (`concurrentDebits_preventNegativeBalance`) و اثبات عدم وقوع موجودی منفی.
  * درخواست‌های تکراری کاملاً همزمان (`concurrentDuplicateRequests_idempotentExecution`) و اثبات اعمال دقیقاً یک‌باره تراکنش.
  * انتقال دوطرفه همزمان بین دو حساب (`bidirectionalTransfers_noDeadlock_totalSumPreserved`) و اثبات ممانعت از بن‌بست و پایستگی مجموع دارایی‌ها.
* **آزمون‌های واسط وب (`BalanceControllerTest`):** صحت‌سنجی کدهای وضعیت پروتکل وب و رفتار کنترلر با ابزار تست وب (`MockMvc`).
* **اسکریپت آزمایش بار همزمان (`test-concurrency.sh`):** اجرای عملیاتی ۱۰۰ درخواست همزمان روی اندپوینت‌های وب با ابزار خط فرمان.

</div>
