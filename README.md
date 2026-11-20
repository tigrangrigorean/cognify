# Cognify

Рабочая основа платформы для академических документов на армянском языке. **Это промежуточная версия, не завершенный SaaS и не production-релиз.** Полное исходное задание: `REQUEST.txt`. Реализованные функции и оставшиеся этапы: `DEVELOPMENT_STATUS.md`.

## Что работает

- Next.js App Router, JavaScript/JSX, React, Tailwind CSS; адаптивная армянская главная страница.
- Регистрация, вход, JWT (15 минут), хешированные refresh-токены (7 дней), ротация под блокировкой БД и отзыв при выходе.
- BCrypt, USER/ADMIN, ограничение частоты запросов авторизации для одного экземпляра сервера.
- PostgreSQL/JPA: аккаунты, refresh-сессии, кошельки, документы, CMS.
- Личный кабинет, нулевой баланс, создание/редактирование/удаление личных документов; optimistic locking предотвращает перезапись чужой версии.
- Четыре CMS-ключа в API: headline, subtitle, contact, footer. Заголовок, подзаголовок и footer читаются публичной страницей из БД. Контактный блок еще не подключен.
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

Backend: `com.cognify.api.controller` → `service` → `repository` → `model.domain`. API возвращает DTO, а не JPA entities. Личные документы выбираются по UUID владельца из JWT, а не из запроса клиента. Секрет JWT обязателен; дефолтного ключа нет.

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

Затем войдите повторно для нового JWT. UI администрирования и аудит изменений CMS еще не реализованы; административный API не следует открывать в production.

## Проверки и сборка

```bash
cd cognify_backend
mvn test
mvn package
# Для запуска jar обязательно задать JWT_SECRET и DB_PASSWORD:
java -jar target/cognify-0.1.0.jar
```

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

## Ограничения безопасности перед production

- Flyway-миграций пока нет. `ddl-auto=update` предназначен только для локальной разработки. Перед production добавить baseline миграцию, `ddl-auto=validate`, безопасные секреты, TLS и резервное копирование.
- Refresh-токен хранится в sessionStorage браузера, access-токен — в памяти. Перед production перенести refresh в HttpOnly Secure cookie и добавить соответствующую CSRF-защиту; CSP и аудит XSS.
- Logout отзывает refresh; уже выпущенный access JWT действует до 15 минут. Управление сессиями, отзыв семейства при replay и мгновенный revoke JWT пока отсутствуют.
- Rate limit in-memory и по адресу непосредственного подключения. При Next.js proxy пользователи могут делить один лимит. Нужен доверенный reverse proxy и распределенный limiter с лимитами по аккаунту и IP.
- Восстановление/изменение пароля, email verification и смена статуса пользователей пока отсутствуют.
- Загружаемые документы, DOCX/PDF, финансовый ledger, AI и административный аудит еще не реализованы.

Не публикуйте эту промежуточную версию с реальными платежами или конфиденциальными рабочими данными.
