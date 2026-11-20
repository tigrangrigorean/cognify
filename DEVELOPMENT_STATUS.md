# Cognify — состояние разработки

Обновлено: 21 ноября 2026. **Промежуточная версия; полное ТЗ не завершено.**

## Последнее завершенное продолжение

- Flyway V1–V4: исходная схема, FK, индексы, ограничения роли и неотрицательного баланса, отзываемые сессии, recovery tokens. Hibernate validate по умолчанию и в production; отдельный dev profile с update.
- Проверена миграция копии прежней Hibernate/PostgreSQL базы и пустой PostgreSQL. Исходные аккаунты, кошельки и CMS-строки сохранены; прежняя база не изменялась. В исходной базе документов не было. Проверен повторный запуск без повторного применения миграций.
- Отдельные UserSession, JWT sid и проверка состояния сессии при каждом запросе. Logout/revoke/password change блокируют access JWT со следующего запроса.
- Refresh rotation с account row lock; replay отзывает сессию, включая токен победившего параллельного запроса. Отзыв коммитится даже при ответе 401.
- UI «Անվտանգություն»: список сессий, отзыв одной/всех, смена пароля с отзывом сессий и recovery-ссылок.
- Next.js BFF `/api/auth/[action]`: HttpOnly/SameSite=Strict cookie, Secure в production, проверка Origin + CSRF header, no-store. Refresh не возвращается в browser JSON; access только в памяти. Legacy sessionStorage очищается.
- Forgot/reset password: SMTP adapter, одноразовый хешированный токен на 30 минут, fragment URL, армянские страницы. Сброс отзывает все сессии и outstanding recovery links. Без SMTP → явный 503. Реальные письма не отправлялись.

## Статус этапов

| Этап | Состояние |
| --- | --- |
| 1. Анализ/архитектура | Выполнен, исходное ТЗ в REQUEST.txt |
| 2. Setup/DB | Рабочая основа + миграции; сборки проверены |
| 3. Авторизация | Основные сценарии реализованы; SMTP delivery/browser E2E и distributed rate limiting еще требуют проверки/доработки |
| 4. Landing/CMS | Армянский landing, 3 DB-текста, 4 CMS API-ключа. Нет полной CMS, admin UI, SEO/порядка/видимости, отдельных информационных страниц |
| 5. Dashboard | Документы, количество, баланс, редактор, безопасность. Нет уведомлений/генераций/истории операций |
| 6. Wallet/Coins | Только wallet с 0; ledger, пакеты, резервы, списания/возвраты отсутствуют |
| 7. Академические инструменты | Ручные черновики четырех типов; AI-параметры и генерация отсутствуют |
| 8. Analyzer | Не реализован |
| 9. Formatting profiles | Не реализованы |
| 10. AI pipeline | Не реализован |
| 11. Editor | Plain text CRUD + optimistic locking; нет секций, истории версий, rich text/AI |
| 12. DOCX/PDF export | Не реализован |
| 13. Research/formatter/checker/citations | Не реализованы |
| 14. Admin | Только защищенный CMS API; нет UI и audit trail |
| 15. Payments | Не реализованы; платежи не принимаются |
| 16. QA | Частично: выполненные проверки ниже |

## Выполненные проверки

- Backend `mvn package`: **7 интеграционных тестов, 0 failures/errors**. H2 запускает реальные Flyway migrations и Hibernate validate. Проверки ownership, optimistic locking, roles, JWT, replay, revoke, смены пароля, reset expiry/single-use, отказа старого пароля, отзыва сессий и явного SMTP 503.
- Frontend `npm test`: **14 tests passed**, API-client single-flight refresh, BFF cookie flags/secret stripping, CSRF/origin rejection, rotation, logout, recovery, SMTP failure handling и порядок rewrites: backend proxy только fallback после local dynamic auth routes.
- `npm run build`: production Next.js build прошел, включая динамический auth route и две recovery pages.
- PostgreSQL 17: `python3 tests/api_smoke.py http://localhost:8083` — HTTP auth/document workflow прошел.
- PostgreSQL 17: `python3 tests/session_concurrency.py http://localhost:8083` — два concurrent refresh дают один 200 и один 401; replay отзывает результирующий JWT; последующий login и logout проверены.
- Live BFF handler → Spring → PostgreSQL: `node cognify_frontend/tests/auth-proxy-live.mjs http://localhost:8083` — скрытие refresh, установка/ротация cookie, logout, отсутствие SMTP проверены. Это вызов серверного handler, **не** проверка routing Next.js в браузере.
- PostgreSQL migration V4 применена после V3; все четыре migration entries успешны.
- Browser QA не выполнен: повторный preview start завершился `relay could not open browser port 4173`. Полный HTTP/browser flow через запущенный Next.js не проверен.
- Реальная доставка SMTP не проверена: нет реквизитов, mail adapter в reset tests заменен mock. AI/payment/export workflow не проверен, поскольку соответствующие модули еще не реализованы.

## Следующий этап

Продолжить этап 4, сохраняя нынешний UI и auth:

1. Добавить общий audit log и транзакционный CMS service; все admin изменения должны иметь actor, action, target, timestamp и безопасный diff.
2. Расширить CMS до блоков landing/FAQ/tools/contact/footer/SEO с порядком/видимостью, DTO validation и API.
3. Добавить защищенную admin страницу и отдельные публичные маршруты. Проверить USER denial, DB persistence и отражение изменений в публичном сайте.
4. Затем этап 6: финансовый ledger, резервы, settlement/refund, idempotency и PostgreSQL concurrency tests. До завершения финансового ядра не включать платные AI jobs.

Параллельные security-задачи перед production: trusted reverse proxy и распределенный limiter (сейчас IP может быть общим для Next.js), audit security events, CSP/XSS review, cleanup expired tokens, SMTP outbox, проверка доставки и browser E2E. Email verification не реализован. При отсутствии Web Locks несколько вкладок могут вызвать replay и потребовать повторного входа. Операция, уже начатая до отзыва JWT, может завершиться.

## Окружение и продолжение

Исходники `/workspace/cognify`, один Git-репозиторий. README содержит команды запуска, baseline старой БД, SMTP и cookie config. `.env.example` не содержит реальных секретов. Требуется корректный `AUTH_ORIGIN` для публичного адреса и HTTPS в production.

В этом окружении Maven/JDK скачаны в игнорируемый `work/`:

```bash
JAVA_HOME=/workspace/cognify/work/jdk-17.0.21.1+2 \
MAVEN_OPTS=-Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts \
/workspace/cognify/work/apache-maven-3.9.11/bin/mvn \
-s /workspace/cognify/work/maven-settings.xml \
-Dmaven.repo.local=/workspace/cognify/work/m2 \
-f /workspace/cognify/cognify_backend/pom.xml package
```

Docker: `cognify-postgres`, исходная dev-БД `cognify`, тестовые копии `cognify_phase2_legacy` и `cognify_phase2_fresh`. Последний backend с V4 тестировался на 8083. Тесты оставляют disposable accounts. Не полагайтесь на сохранение процессов между сеансами; запускайте приложения по README.

`work/` содержит локальные инструменты, backup тестовой базы и логи; не коммитить. Генераторы create.py/frontend.py/phase2.py/reset-phase.py были одноразовыми — **не запускать повторно**, они могут затереть исправления. Стандартная папка outputs `/codex/...` в этой среде read-only; исходники находятся в workspace.
