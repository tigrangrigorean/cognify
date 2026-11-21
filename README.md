# Cognify

Рабочая основа платформы для академических документов на армянском языке. **Это промежуточная версия, не завершенный SaaS и не production-релиз.** Полное исходное задание: `REQUEST.txt`. Реализованные функции и оставшиеся этапы: `DEVELOPMENT_STATUS.md`.

## Что работает

- Next.js App Router, JavaScript/JSX, React, Tailwind CSS; адаптивная армянская главная страница.
- Регистрация, вход, JWT (15 минут), хешированные refresh-токены (7 дней), ротация под блокировкой БД, обнаружение replay и немедленный отзыв JWT при выходе. Refresh хранится в HttpOnly/SameSite=Strict cookie через Next.js BFF; в production добавляется Secure.
- Список активных сессий, отзыв одной/всех, смена пароля, восстановление по одноразовой SMTP-ссылке (30 минут).
- Flyway V1–V7, внешние ключи и индексы, Hibernate validate по умолчанию.
- BCrypt, USER/ADMIN, ограничение частоты запросов авторизации для одного экземпляра сервера.
- PostgreSQL/JPA: аккаунты, refresh-сессии, кошельки, документы, CMS.
- Личный кабинет, нулевой баланс, создание/редактирование/удаление личных документов; optimistic locking предотвращает перезапись чужой версии.
- CMS: отдельная страница `/admin`, редактирование бренда/лого URL/кнопок/SEO/контактов/политики/условий и публичных блоков с видимостью, порядком и элементами. Все изменения сохраняются в PostgreSQL с audit trail. Публичные маршруты `/tools`, `/pricing`, `/faq`, `/contact`, `/privacy`, `/terms`.
- Backend интеграционные тесты; frontend тесты API-клиента.

AI, платежи и экспорт не имитируются: они еще не реализованы. Никаких списаний или начислений Coins в этой версии нет.

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

`npm test` проверяет API-клиент, обработку ошибок и однократную ротацию при параллельных 401 и восстановлении сессии. Для проверки живого backend на PostgreSQL запустите из корня `python3 tests/api_smoke.py`; скрипт создает два тестовых аккаунта. Полный браузерный E2E и заявленный в ТЗ сценарий с AI/Coins/экспортом пока не выполнены.

## AI и платежи

В текущей версии API-провайдеров нет. API-ключи не требуются. Не вводите реальные банковские данные. Следующие этапы должны добавить интерфейсы AI/PaymentProvider, конфигурацию на backend, явный dev/mock режим, подтвержденные webhooks и транзакционный ledger с резервами. До этого покупка Coins недоступна.

## Миграция существующей базы

Для новой пустой БД Flyway V1–V7 выполняются автоматически. Hibernate по умолчанию только валидирует схему. `application-prod.properties` сохраняет validate и запрещает Flyway clean. Для локального legacy dev-режима предусмотрен профиль `dev` с `ddl-auto=update`; для обычной работы он не нужен.

Старая база, созданная предыдущим Hibernate update, не имеет flyway_schema_history. **Сначала сделайте backup и проверьте миграцию на копии.** Однократно запустите с `SPRING_FLYWAY_BASELINE_ON_MIGRATE=true` (baseline-version уже 0). V1 сохраняет существующие таблицы, V2 добавляет ограничения, V3 отзывает старые токены без session binding, V4 добавляет password_reset; V5–V7 добавляют CMS blocks, audit log и начальные публичные тексты. Затем уберите флаг. Несовместимые данные, например отрицательный баланс или owner без accounts, приводят к ошибке миграции; не удаляйте их автоматически.

```bash
pg_dump -U postgres -d cognify -Fc -f cognify-backup.dump
# Backup храните вне Git.
SPRING_FLYWAY_BASELINE_ON_MIGRATE=true mvn spring-boot:run
```

После миграции пользователям предыдущей версии потребуется войти заново. Данные документов и кошельков сохраняются. Повторный запуск проверяет checksums и не применяет миграции заново. Файлы примененных миграций не редактируйте; добавляйте V8 и далее.

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
- Загрузка документов, DOCX/PDF, финансовый ledger, AI и административные разделы пользователей/финансов/генераций еще не реализованы.

Это промежуточная версия; перед публикацией завершите перечисленные проверки и оставшиеся требования.

## CMS и аудит

`GET /api/public/blocks` возвращает только видимые блоки в порядке position/key. `GET /api/admin/blocks` возвращает все. `PUT /api/admin/blocks/{key}` принимает title, body, items (`title`, `body`), position (0–1000), visible, version. При конфликте version → 409; перечитайте страницу перед сохранением. Для pricing/cta items должны быть пустыми. Стандартные блоки: benefits, tools, how, examples, pricing, faq, cta.

`PUT /api/admin/content/{key}` меняет текстовые настройки; существующие строки блокируются до завершения транзакции. Настройки пока не имеют клиентского version check (последний успешно сохраненный вариант становится текущим). Ключи: headline, subtitle, siteName, logoUrl, primaryCta, secondaryCta, ctaButton, toolButton, seoTitle, seoDescription, contact, footer, privacy, terms. Logo допускает HTTPS URL или локальный путь; `/logo.svg` возвращает стандартный знак. Загрузка собственного файла пока не реализована.

Каждое изменение записывается атомарно в audit_log: actor UUID, action, target, before/after и timestamp. `GET /api/admin/audit?page=0` возвращает 50 записей на страницу. Журнал не содержит содержимого личных документов и не имеет API изменения/удаления. Это аудит CMS; финансовый и общий security audit еще не добавлены.

Главная страница получает блоки из API. SEO и отдельные публичные страницы читают PostgreSQL через backend без Next.js cache. Изменения становятся видны при следующем открытии/обновлении страницы; live push не реализован. Герой и вводная полоса остаются фиксированными по позиции/видимости; их основные тексты редактируются настройками. Пока нет произвольного конструктора новых типов блоков и редактора предпросмотра. Политика/условия — явно отмеченные предварительные тексты, которые владелец должен заполнить перед запуском.

CMS PostgreSQL concurrency/regression test (только на тестовой БД):

```bash
python3 tests/cms_smoke.py http://localhost:8080 cognify_phase2_fresh
```

Тест использует Docker-контейнер `cognify-postgres`, создает отдельный тестовый ADMIN, проверяет запреты USER, hide/publish, optimistic concurrency, аудит и отклонение опасного logo URL. Отредактированный FAQ восстанавливается в finally; тестовый аккаунт и audit entries остаются. Backend tests используют изолированные H2-базы и также проверяют CMS.
