# Cognify

Рабочая основа платформы для академических документов на армянском языке. **Это промежуточная версия, не завершенный SaaS и не production-релиз.** Полное исходное задание: `REQUEST.txt`. Реализованные функции и оставшиеся этапы: `DEVELOPMENT_STATUS.md`.

## Что работает

- Next.js App Router, JavaScript/JSX, React, Tailwind CSS; адаптивная армянская главная страница.
- Регистрация, вход, JWT (15 минут), хешированные refresh-токены (7 дней), ротация под блокировкой БД, обнаружение replay и немедленный отзыв JWT при выходе. Refresh хранится в HttpOnly/SameSite=Strict cookie через Next.js BFF; в production добавляется Secure.
- Список активных сессий, отзыв одной/всех, смена пароля, восстановление по одноразовой SMTP-ссылке (30 минут).
- Flyway V1–V11, внешние ключи и индексы, Hibernate validate по умолчанию.
- BCrypt, USER/ADMIN, ограничение частоты запросов авторизации для одного экземпляра сервера.
- PostgreSQL/JPA: аккаунты, refresh-сессии, кошельки, документы, CMS.
- Личный кабинет, кошелек с доступным/зарезервированным балансом, создание/редактирование/удаление личных документов; optimistic locking предотвращает перезапись чужой версии.
- CMS: отдельная страница `/admin`, редактирование бренда/лого URL/кнопок/SEO/контактов/политики/условий и публичных блоков с видимостью, порядком и элементами. Все изменения сохраняются в PostgreSQL с audit trail. Публичные маршруты `/tools`, `/pricing`, `/faq`, `/contact`, `/privacy`, `/terms`.
- Backend интеграционные тесты; frontend тесты API-клиента.

Работает основной сценарий: тема → редактируемый план → подтверждение → поэтапная генерация → редактор разделов с историей → DOCX. Перед запуском резервируются Coins; после результата списывается фактическая стоимость в пределах подтвержденной оценки. Ошибка/отмена освобождает резерв. Есть пауза, продолжение и повтор сбойного этапа.

Реальный OpenAI adapter подключен в коде, но внешние вызовы без ключа не проверены. По умолчанию AI выключен; тестовый mock включается только явно в dev и отмечается в интерфейсе и DOCX. Реальные платежи отключены. PDF, анализ образца, профили оформления и поиск источников еще не реализованы.

## Структура

```
cognify/
  cognify_frontend/       # Next.js → same-origin /api proxy
  cognify_backend/        # Spring Boot 3.5.6 → PostgreSQL
  compose.yaml           # локальная PostgreSQL 17
  PROJECT_SPEC.md
  DEVELOPMENT_STATUS.md
  REQUEST.txt
```

Auth-запросы браузера проходят через Next.js `/api/auth/[action]`: проверяются Origin и CSRF-заголовок; refresh-токен из backend JSON сохраняется сервером в cookie. Access JWT остается только в памяти браузера. Остальные `/api` запросы проксируются с Bearer JWT. Backend: `com.cognify.api.controller` → `service` → `repository` → `model.domain`. API возвращает DTO, а не JPA entities. Личные документы выбираются по UUID владельца из JWT, а не из запроса клиента. Секрет JWT обязателен; дефолтного ключа нет.

## Требования

JDK 17 или 21 **с javac**, Maven 3.9+, Node.js 22+, npm, Docker Compose либо локальная PostgreSQL 17. Откройте папку `cognify` в VS Code; frontend и backend — два отдельных приложения в одном репозитории.

## Запуск

Из корня `cognify`:

```bash
cp .env.example .env
# Замените DB_PASSWORD и JWT_SECRET случайными значениями.
# Секрет можно сгенерировать: openssl rand -hex 32
set -a
. ./.env
set +a
docker compose up -d
```

`.env` читается Compose автоматически, но Spring Boot и Next.js не читают корневой `.env` самостоятельно. Экспортируйте переменные в каждом терминале, как показано выше.

При собственной PostgreSQL:

```sql
CREATE DATABASE cognify;
```

Стандартное подключение: `jdbc:postgresql://localhost:5432/cognify`, пользователь `postgres`, локальный пароль по умолчанию `admin` (переопределите `DB_PASSWORD`). Можно переопределить `DB_URL` и `DB_USER`.

Терминал 1:

```bash
cd cognify_backend
mvn spring-boot:run
```

Терминал 2 (с экспортированным `BACKEND_URL=http://localhost:8080`):

```bash
cd cognify_frontend
npm ci
npm run dev
```

Для разработки используйте `npm run dev`. Production cookies требуют HTTPS; `AUTH_ORIGIN` должен точно совпадать с внешним origin, а `FRONTEND_URL` — с адресом, который будет в письмах.

Откройте `http://localhost:3000`. Зарегистрируйтесь, создайте документ, отредактируйте и сохраните. Данные остаются в PostgreSQL. Баланс нового аккаунта — 0; ручные документы бесплатны.

## REST API

| Метод | URL | Доступ |
| --- | --- | --- |
| POST | /api/auth/register | email, password (10–72 символа, не более 72 UTF-8 байт), name |
| POST | /api/auth/login | email, password |
| POST | /api/auth/refresh | refreshToken; одноразовая ротация |
| POST | /api/auth/logout | отзыв refreshToken |
| GET | /api/me | Bearer JWT |
| GET, POST | /api/documents | Bearer JWT |
| PUT, DELETE | /api/documents/{id} | Только владелец |
| GET | /api/public/content | Публичный |
| PUT | /api/admin/content/{key} | ADMIN; `{ "value": "..." }` |

Документ: `{ "title": "...", "kind": "ESSAY", "content": "...", "version": 0 }`. Типы: COURSEWORK, THESIS, ESSAY, REPORT. При редактировании передавайте последнюю возвращенную `version`. Конфликт → HTTP 409. Чужой документ → HTTP 404.

OpenAPI подключен: `/v3/api-docs` и `/swagger-ui/index.html` требуют аутентификацию. Swagger UI еще не настроен как самостоятельный интерактивный сценарий входа.

Первого администратора пока назначает оператор БД, после регистрации проверенного аккаунта:

```sql
UPDATE accounts SET role = 'ADMIN' WHERE email = 'your-verified-email@example.com';
```

Затем войдите повторно для нового JWT. После входа с ролью ADMIN откройте `/admin` или ссылку в кабинете. API дополнительно проверяет роль; скрытие ссылки не заменяет проверку доступа. При обновлении старого JWT роль не меняется до нового входа.

## Проверки и сборка

```bash
cd cognify_backend
mvn test
mvn package
# Для запуска jar обязательно задать JWT_SECRET и DB_PASSWORD:
java -jar target/cognify-0.1.0.jar
```

Проверка конкуренции на PostgreSQL: `python3 tests/session_concurrency.py http://localhost:8080`. Скрипт создает один тестовый аккаунт и намеренно повторяет refresh-токен; ожидается отзыв всей сессии.

Backend tests используют H2 в PostgreSQL compatibility mode для изолированных проверок, а не как рабочую БД. Это не заменяет PostgreSQL concurrency-тесты.

```bash
cd cognify_frontend
npm ci
npm test
npm run build
npm start
```

`npm test` проверяет API-клиент, обработку ошибок и однократную ротацию при параллельных 401 и восстановлении сессии. Для проверки живого backend на PostgreSQL запустите из корня `python3 tests/api_smoke.py`; скрипт создает два тестовых аккаунта. Основной HTTP-сценарий с mock AI/Coins/DOCX проверен на PostgreSQL (`tests/generation_smoke.py`). Браузерный E2E не выполнен: облачный preview не открывает relay port 4173.

## AI и платежи

Есть AiProvider и OpenAI Chat Completions adapter; настройка описана ниже. Для платежей есть интерфейс PaymentProvider и явно тестовая реализация; по умолчанию провайдер отключен. Реальный Armenian vPOS, проверка банковских статусов/уведомлений, денежные возвраты и reconciliation еще не реализованы. Банковские API не имитируются. Тестовый платеж подтверждается самим backend, публичного callback для начисления Coins нет.

## Миграция существующей базы

Для новой пустой БД Flyway V1–V11 выполняются автоматически. Hibernate по умолчанию только валидирует схему. `application-prod.properties` сохраняет validate и запрещает Flyway clean. Для локального legacy dev-режима предусмотрен профиль `dev` с `ddl-auto=update`; для обычной работы он не нужен.

Старая база, созданная предыдущим Hibernate update, не имеет flyway_schema_history. **Сначала сделайте backup и проверьте миграцию на копии.** Однократно запустите с `SPRING_FLYWAY_BASELINE_ON_MIGRATE=true` (baseline-version уже 0). V1 сохраняет существующие таблицы, V2 добавляет ограничения, V3 отзывает старые токены без session binding, V4 добавляет password_reset; V5–V7 добавляют CMS blocks, audit log и начальные публичные тексты; V8–V9 добавляют финансовый ledger, резервы, payment/usage records и неактивные начальные пакеты/тарифы. Затем уберите флаг. Несовместимые данные, например отрицательный баланс или owner без accounts, приводят к ошибке миграции; не удаляйте их автоматически.

```bash
pg_dump -U postgres -d cognify -Fc -f cognify-backup.dump
# Backup храните вне Git.
SPRING_FLYWAY_BASELINE_ON_MIGRATE=true mvn spring-boot:run
```

После миграции пользователям предыдущей версии потребуется войти заново. Данные документов и кошельков сохраняются. Повторный запуск проверяет checksums и не применяет миграции заново. Файлы примененных миграций не редактируйте; добавляйте V12 и далее.

## Сессии и восстановление пароля

- `GET /api/account/sessions` — активные сессии текущего пользователя.
- `DELETE /api/account/sessions/{id}` — отзыв одной; чужая → 404.
- `DELETE /api/account/sessions` — отзыв всех, включая текущую.
- `POST /api/account/password` — `currentPassword`, `newPassword`; все сессии и reset-ссылки отзываются.
- `POST /api/auth/forgot-password` — `email`; одинаковый ответ для существующего и неизвестного адреса при доступном SMTP.
- `POST /api/auth/reset-password` — `token`, `newPassword`; одноразовый токен действителен 30 минут, после смены все сессии и recovery-ссылки недействительны.

Frontend: раздел «Անվտանգություն» в кабинете; `/forgot-password` и `/reset-password`. Токен в ссылке передается во fragment, не в query, и удаляется из адресной строки после чтения. В БД хранится только SHA-256 хеш. Access JWT проверяет состояние сессии в БД на каждом запросе. Повтор старого refresh отзывает всю сессию, включая уже выпущенный новый JWT. В пределах вкладки запросы объединяются, а при наличии Web Locks сериализуются между вкладками; в браузерах без Web Locks одновременный refresh из разных вкладок может потребовать повторного входа.

Для SMTP задайте `MAIL_ENABLED=true`, `MAIL_FROM`, `SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`, `SMTP_PASSWORD`, `SMTP_AUTH`, `SMTP_STARTTLS`, `FRONTEND_URL`. Используется реальный JavaMailSender с 5-секундными timeouts; без настроенного SMTP возвращается 503. Не включайте отправку без своих реквизитов. Реальная доставка письма в этом окружении не проверялась: backend tests заменяют mail adapter. Письмо отправляется синхронно; transactional outbox для масштабирования еще не реализован.

Browser auth route требует `Origin: <AUTH_ORIGIN>`, `X-Cognify-CSRF: 1` и JSON. Он не возвращает refresh-токен в JSON. Backend REST auth остается доступен серверным клиентам с JSON-токенами и не использует cookie authentication. Не открывайте backend напрямую в публичную сеть без reverse proxy.

## Оставшиеся ограничения перед production

- Rate limit in-memory и по адресу непосредственного подключения. При Next.js proxy пользователи могут делить один лимит. Нужен доверенный reverse proxy и распределенный limiter по аккаунту/IP.
- Нужны CSP, аудит XSS, email verification, cleanup истекших токенов и сессий, аудит событий безопасности и административных действий.
- Вход уже отозванной сессии блокируется со следующего запроса; операция, начавшаяся до отзыва, может завершиться.
- Браузерный E2E/визуальная проверка не завершены из-за недоступного preview relay. BFF проверен unit-тестами, production build и прямым вызовом handler с настоящим Spring/PostgreSQL (`node cognify_frontend/tests/auth-proxy-live.mjs http://localhost:8080`); полный HTTP-сценарий через routing Next.js пока не проверен.
- Загрузка документов, DOCX/PDF, AI и административные разделы управления пользователями/генерациями еще не реализованы. Финансовая панель реализована для пакетов, тарифов, корректировок и возвратов Coins; банковская интеграция отсутствует.

Это промежуточная версия; перед публикацией завершите перечисленные проверки и оставшиеся требования.

## CMS и аудит

`GET /api/public/blocks` возвращает только видимые блоки в порядке position/key. `GET /api/admin/blocks` возвращает все. `PUT /api/admin/blocks/{key}` принимает title, body, items (`title`, `body`), position (0–1000), visible, version. При конфликте version → 409; перечитайте страницу перед сохранением. Для pricing/cta items должны быть пустыми. Стандартные блоки: benefits, tools, how, examples, pricing, faq, cta.

`PUT /api/admin/content/{key}` меняет текстовые настройки; существующие строки блокируются до завершения транзакции. Настройки пока не имеют клиентского version check (последний успешно сохраненный вариант становится текущим). Ключи: headline, subtitle, siteName, logoUrl, primaryCta, secondaryCta, ctaButton, toolButton, seoTitle, seoDescription, contact, footer, privacy, terms. Logo допускает HTTPS URL или локальный путь; `/logo.svg` возвращает стандартный знак. Загрузка собственного файла пока не реализована.

Каждое изменение записывается атомарно в audit_log: actor UUID, action, target, before/after и timestamp. `GET /api/admin/audit?page=0` возвращает 50 записей на страницу. Журнал не содержит содержимого личных документов и не имеет API изменения/удаления. Этот же audit log теперь содержит финансовые изменения, пакеты и тарифы. Общий security audit еще не добавлен.

Главная страница получает блоки из API. SEO и отдельные публичные страницы читают PostgreSQL через backend без Next.js cache. Изменения становятся видны при следующем открытии/обновлении страницы; live push не реализован. Герой и вводная полоса остаются фиксированными по позиции/видимости; их основные тексты редактируются настройками. Пока нет произвольного конструктора новых типов блоков и редактора предпросмотра. Политика/условия — явно отмеченные предварительные тексты, которые владелец должен заполнить перед запуском.

CMS PostgreSQL concurrency/regression test (только на тестовой БД):

```bash
python3 tests/cms_smoke.py http://localhost:8080 cognify_phase2_fresh
```

Тест использует Docker-контейнер `cognify-postgres`, создает отдельный тестовый ADMIN, проверяет запреты USER, hide/publish, optimistic concurrency, аудит и отклонение опасного logo URL. Отредактированный FAQ восстанавливается в finally; тестовый аккаунт и audit entries остаются. Backend tests используют изолированные H2-базы и также проверяют CMS.

## Упрощенный дизайн

По уточнению пользователя функциональное ТЗ остается полным, включая Coins, AI, анализ и экспорт. Упрощена только подача: белый/синий интерфейс, плоские блоки, меньше декора, удален демонстрационный макет документа, отключено движение кнопок. CMS, авторизация, документы и финансовые страницы сохранены.

## Coins: правила учета и API

`wallet.balance` — всего Coins, `wallet.reserved` — удержанная часть, `available = balance - reserved`. `/api/me.balance` теперь означает доступный баланс для dashboard. Деньги пакетов хранятся целыми minor units: для AMD 100 лум = 1 драм. Coins — целые числа, без float.

Каждая финансовая мутация блокирует строку кошелька; wallet, coin_transaction, reservation, usage и audit изменяются в одной транзакции. БД не допускает отрицательный баланс или reserve > balance. Ключ идемпотентности ограничен владельцем и типом операции; повтор с другим payload → 409. Сумма amount ledger равна total balance, сумма reserved_delta равна reserved. Реальные старые ненулевые балансы вне ledger следует сверить до production; прежняя проектная база содержала только нулевые кошельки.

Серверные методы WalletService (не доступны как пользовательские price-controlled endpoints):

- `reserve(owner, operation, amount, requestKey)` — проверить available и удержать Coins.
- `settle(owner, reservationId, actualCost)` — actualCost не выше резерва, списать фактическую стоимость, освободить весь hold, записать usage. Повтор с той же стоимостью ничего не списывает.
- `release(owner, reservationId)` — освободить hold при отмене/ошибке, идемпотентно.
- `refund(...)` — возвратить ранее списанные Coins; сумма частичных возвратов не превышает settled cost.

Пока нет AI jobs, эти методы проверяются интеграционными тестами и еще не вызываются генерацией. Автоматическое освобождение hold по таймауту будет добавлено с durable job pipeline.

| Endpoint | Назначение |
| --- | --- |
| GET /api/wallet | total, reserved, available |
| GET /api/wallet/transactions?page=0 | История, 30 строк на страницу |
| GET /api/wallet/reservations?page=0 | Резервы текущего пользователя |
| GET /api/public/coin-packages | Активные пакеты |
| GET /api/public/payment-mode | Доступность и TEST flag |
| GET /api/pricing/quote?operation=DOCUMENT_GENERATION&units=3 | Авторизованная оценка стоимости по активному тарифу |
| POST /api/payments | packageId + requestKey, сумма только из БД |
| GET /api/payments?page=0 | История платежей текущего пользователя |
| GET/POST /api/admin/coin-packages | Список/создание пакетов |
| PUT /api/admin/coin-packages/{id} | Обновление с version |
| GET /api/admin/pricing | Тарифы |
| PUT /api/admin/pricing/{operation} | Unit, unitCost, active, version |
| GET /api/admin/wallets/lookup?email=... | Найти пользователя для корректировки |
| POST /api/admin/wallets/{userId}/adjustments | amount (+/−), обязательные reason и requestKey |
| POST /api/admin/wallets/{userId}/reservations/{id}/refunds | Возврат Coins за списанную операцию |

Тарифы поддерживают OPERATION, WORD, PAGE, CHAPTER, ANALYSIS, EXPORT. Неактивный/неизвестный тариф → 503. Сервис генерации должен самостоятельно рассчитывать объем и фиксировать quoted cost в резерве; нельзя доверять сумме из frontend.

UI: «Դրամապանակ» показывает остатки, операции, последние 30 резервов/платежей и тестовые пакеты; `/admin/finance` содержит управление пакетами/тарифами, поиск по email, корректировку с причиной и возвраты. История финансовых действий доступна в журнале `/admin`. Изменения пакетов/тарифов проверяют version; сохраняемые финансовые операции держат requestKey в sessionStorage до успешного подтверждения/обновления данных, включая повтор после сетевого сбоя. Там хранятся только хеш параметров и request ID, не суммы/причины/refresh credentials.

## Тестовое пополнение

Нужна **отдельная тестовая PostgreSQL база**. Тестовые Coins не должны переноситься в production. Установите `SPRING_PROFILES_ACTIVE=dev` и `TEST_PAYMENTS_ENABLED=true`; с prod или без dev приложение откажется запускаться с включенным тестовым провайдером. По умолчанию payments disabled, POST /payments → 503.

Через `/admin/finance` создайте/активируйте тестовый пакет. Начальные пакет и тариф из миграции специально неактивны, их цены не являются утвержденным коммерческим предложением. В кошельке появляется кнопка «Թեստային համալրում» с явным подтверждением, что реальная сумма не списывается. Backend фиксирует payment snapshot и начисляет Coins одной транзакцией; повтор запроса возвращает тот же payment. В ledger виден TEST_PAYMENT, в payment record test=true.

Текущий PaymentProvider не готов для реальных внешних списаний: добавление реального vPOS требует документированного adapter, server-side verification, защищенного webhook, outbox/reconciliation и банковского refund flow. Финансовая транзакция вокруг локального тестового провайдера не заменяет эти механизмы.

## Тестирование Coins

Обычный `mvn test` включает WalletIntegrationTest с H2 и реальными транзакциями/параллельными потоками. Для отдельной PostgreSQL:

```bash
# Сначала создайте отдельную пустую БД, например cognify_coins_test.
COGNIFY_POSTGRES_TEST_URL=jdbc:postgresql://localhost:5432/cognify_coins_test \
COGNIFY_POSTGRES_TEST_USER=postgres \
COGNIFY_POSTGRES_TEST_PASSWORD=your-test-db-password \
mvn -Dtest=WalletIntegrationTest test
```

Команду запускайте из cognify_backend. Тесты создают только свои аккаунты, ledger и конфигурации; не используйте рабочую БД. Проверяются reserve/settle/release/refund, competing holds, duplicate settlements/payments, rollback, quote и запрет test provider в prod.

HTTP-проверка на запущенном dev backend с тестовым провайдером:

```bash
python3 tests/coins_smoke.py http://localhost:8081 cognify_coins_test
```

Скрипт использует Docker-контейнер cognify-postgres, создает отдельного тестового администратора/пользователей, пакет и тариф, делает только TEST payments. Данные теста остаются в тестовой БД. Реальное списание, банковский возврат и визуальный browser E2E не проверялись.


## Запуск всего приложения через Docker

После создания `.env` и замены секретов:

```bash
docker compose -f compose.yaml -f compose.app.yaml up --build -d
```

Frontend: `http://localhost:3000`, backend: `http://localhost:8080`. Compose ожидает готовность PostgreSQL, Flyway применяет миграции. Порты привязаны к localhost. Dockerfiles добавлены; полный контейнерный build в этом окружении пока не проверен. Проверены нативные Maven/Next.js сборки и PostgreSQL HTTP-сценарий.

## Включение генерации

1. Для настоящего AI задайте **только backend**: `AI_MODE=openai`, `OPENAI_API_KEY`, при необходимости `OPENAI_MODEL` (по умолчанию `gpt-4.1-mini`). Перезапустите backend. Ключ не нужен frontend и не должен иметь префикс `NEXT_PUBLIC_`.
2. Назначьте первого администратора по инструкции выше. В `/admin/finance` включите тариф `DOCUMENT_GENERATION`, единица `PAGE`, например 10 Coins. Изначально тариф отключен.
3. Через ту же панель начислите пользователю Coins с указанием причины. Реальный банковский checkout пока не подключен.
4. В кабинете откройте «AI գեներացիա», задайте тему/тип/язык/объем/требования. Проверьте максимальную цену, создайте задание, отредактируйте и подтвердите план. После завершения откройте документ, сохраните изменения и скачайте DOCX.

Для демонстрации без внешних API используйте **отдельную тестовую БД**, `SPRING_PROFILES_ACTIVE=dev`, `AI_MODE=mock`, `SPRING_JPA_HIBERNATE_DDL_AUTO=validate`. При желании включите `TEST_PAYMENTS_ENABLED=true` и активируйте тестовый пакет в `/admin/finance`. Mock создает короткий маркированный текст, не академическую работу. Эти данные и тестовые Coins не переносить в production.

Ограничения текущей генерации: 1–40 запрошенных страниц, 3–12 разделов, до двух незавершенных заданий пользователя, максимум три попытки этапа. Расчетная страница — 350 слов, не физическая страница DOCX. Стоимость равна `ceil(слов / 350) × тариф`, минимум одна страница, максимум подтвержденная оценка. Пауза сохраняет резерв; отмена/ошибка освобождает. Повтор после ошибки резервирует сумму снова, сохраняет завершенные разделы и не проводит повторное итоговое списание. После сбоя процесса внешний AI-запрос может повториться; exactly-once обеспечивается для сохранения результата и Coins, не для расходов внешнего API. Usage содержит токены успешно сохраненных этапов, не полный счет провайдера.

DOCX: A4, Sylfaen 14, интервал 1.5, поля 30/15/20/20 мм, титульная страница, список разделов, заголовки и номера страниц. Список разделов пока без номеров страниц; профиль вуза и PDF еще не поддержаны. История показывает последние 10 текстовых снимков; rich text, восстановление снимка и AI-переписывание отдельных фрагментов — следующие этапы. Источники не ищутся и не проверяются автоматически.

### API генерации и экспорта

Все маршруты ниже требуют Bearer JWT; документы/задания доступны только владельцу.

- `GET /api/generation/config`, `GET /api/generation/quote?pages=3`
- `POST /api/generation/jobs`: topic, kind, language, pages, subject, university, requirements, acceptedQuote, requestKey; ответ 202.
- `GET /api/generation/jobs`, `GET /api/generation/jobs/{id}`
- `PUT /api/generation/jobs/{id}/plan`: headings, version.
- `POST /api/generation/jobs/{id}/{pause|resume|cancel|retry}`
- `GET /api/documents/{id}/sections`, `POST .../sections`, `PUT .../sections/{sectionId}`, `DELETE .../sections/{sectionId}?version=N`.
- `PUT /api/documents/{id}/section-order`: ids, version.
- `GET /api/documents/{id}/versions`, `GET /api/documents/{id}/export.docx`.

Проверки: `mvn test` включает GenerationIntegrationTest. Для той же проверки на отдельной PostgreSQL задайте `COGNIFY_POSTGRES_TEST_URL`, `COGNIFY_POSTGRES_TEST_PASSWORD` и запустите `mvn -Dtest=GenerationIntegrationTest test`. При работающем backend с mock AI и TEST payments: `python3 tests/generation_smoke.py http://localhost:8082 cognify_generation_test`. HTTP-скрипт требует локальный Docker-контейнер `cognify-postgres` и создает только тестовые аккаунты/пакеты.
