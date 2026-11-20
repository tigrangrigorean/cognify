# Cognify — состояние разработки

Обновлено: 21 ноября 2026. **Промежуточная рабочая основа. Полное ТЗ не завершено.**

## Текущее состояние

Существующего проекта в рабочем пространстве не было. Создана единая папка `/workspace/cognify`, два интегрированных приложения и PostgreSQL-конфигурация. Рабочий вертикальный сценарий: регистрация → вход → dashboard → создание документа → ручное редактирование → сохранение в PostgreSQL → повторное чтение → удаление → выход.

Ни AI, ни платежи, ни экспорт не выданы за готовые функции. На странице явно указана их недоступность. Система не генерирует фиктивные научные источники, не начисляет тестовые Coins как реальные и не принимает платежи.

## Этапы

| Этап | Статус | Реализовано / остаток |
| --- | --- | --- |
| 1. Анализ и архитектура | Выполнен | PROJECT_SPEC.md, исходное REQUEST.txt |
| 2. Frontend/backend/database | Рабочая dev-основа | Next.js JS/JSX, Spring Boot 3.5.6, PostgreSQL, Compose; production-миграции еще нужны |
| 3. Авторизация | Частично | BCrypt, JWT, refresh rotation, logout, USER/ADMIN, in-memory rate limit; нет password recovery/change, email verification, UI сессий |
| 4. Landing/CMS | Частично | Армянский адаптивный landing, 3 текста из БД и 4 ключа CMS API; нет полной CMS, ее UI, SEO/порядка/видимости блоков и отдельных информационных страниц |
| 5. Dashboard | Частично | Документы, их количество, баланс, редактор; нет уведомлений, истории операций и активных генераций |
| 6. Wallet/Coins | Только сущность | Кошелек создается с 0; нет ledger, пакетов, резервов, списаний и возвратов |
| 7. Академические инструменты | Только ручные черновики | Четыре типа документа в CRUD; нет специализированных AI-параметров и генерации |
| 8. Analyzer | Не реализован | DOCX/PDF upload, parsing и confidence |
| 9. Formatting profiles | Не реализован | Все настройки, CRUD и применение |
| 10. AI pipeline | Не реализован | Provider interface, durable jobs, steps, retry/pause/resume/cancel |
| 11. Editor | Частично | Редактирование plain text, title/kind, optimistic locking; нет секций, истории версий, AI-редактирования и rich text |
| 12. Export | Не реализован | DOCX/PDF, preview, export tests |
| 13. Дополнительные AI-инструменты | Не реализованы | Research, checker, formatter, citations |
| 14. Admin | Только защищенный CMS API | Нет UI, пользователей, статистики, audit trail |
| 15. Payments | Не реализован | Ни тестовый, ни реальный провайдер не подключен |
| 16. Проверки | Частично | См. ниже; полный сценарий ТЗ пока невозможен |

## Проверено

- Backend `mvn package`: компиляция на JDK 17 и 2 интеграционных теста H2, без failures/errors.
- Тесты проверяют валидацию, JWT, начальный баланс 0, запрет чужого чтения/изменения/удаления, устаревшую версию, запрет ADMIN API для USER, ротацию refresh и logout.
- Frontend `npm test`: 5 API-client tests (headers/body, errors, parallel 401 refresh, 204, concurrent session restoration).
- Frontend `npm run build`: production-сборка Next.js 15.5.29 прошла. Файлы TypeScript/TSX в исходниках не создавались.
- PostgreSQL 17 в Docker: `python3 tests/api_smoke.py` прошел через настоящий HTTP backend. Проверены регистрация, вход, wallet 0, CRUD, ownership, version conflict, RBAC, CMS, rotation/logout. Скрипт оставляет два тестовых аккаунта, созданный документ удаляет.
- Проверка браузером **не выполнена**: Sites preview relay не смог открыть порт 4173. Next.js build не является доказательством визуального/browser E2E тестирования.
- ТЗ-сценарий тестовой покупки → sample upload → AI → DOCX/PDF не выполнен: соответствующие модули еще не реализованы, а не просто отсутствуют credentials.

## Исправления по результатам проверок

- Имя SQL-колонки CMS `value` заменено на `content_value` для совместимости с H2.
- API-client объединяет параллельные refresh-запросы; восстановление сессии также не дублирует rotation при повторном React effect.
- Добавлена проверка лимита BCrypt в UTF-8 байтах.
- Для Next.js добавлен адаптер dev CLI, переводящий `--host` в `--hostname` и убирающий Vite-only `--strictPort`.

## Следующий точный шаг

Продолжить этап 2/3 до production-пригодной основы, не переписывая рабочий CRUD:

1. Добавить Flyway baseline для существующих таблиц, FK к accounts, индексы ownerId/updatedAt и production profile с validate. Проверить миграцию на пустой PostgreSQL и повторный запуск.
2. Вынести refresh в HttpOnly Secure cookie/BFF с CSRF-защитой; добавить семейства сессий, replay detection, revoke-all, password recovery через реальный mail adapter и password change.
3. Добавить тесты конкурентной refresh rotation на PostgreSQL, account/IP rate limiting через доверенный proxy/shared store.
4. Выделить UI page.jsx в компоненты/маршруты без изменения рабочего сценария; добавить form и browser E2E тесты, доступность модального окна (focus trap/Escape).
5. Далее завершить CMS + административный audit и UI, затем финансовый ledger (reservation/settlement/idempotency) с PostgreSQL concurrency tests. Только после этого подключать платные AI jobs.

## Как продолжить в этом окружении

Обычные команды см. README. В этом запуске Maven и полный JDK отсутствовали; они скачаны в игнорируемый `work/`. Maven использует прокси и системный truststore:

```bash
JAVA_HOME=/workspace/cognify/work/jdk-17.0.21.1+2 \
MAVEN_OPTS=-Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts \
/workspace/cognify/work/apache-maven-3.9.11/bin/mvn \
-s /workspace/cognify/work/maven-settings.xml \
-Dmaven.repo.local=/workspace/cognify/work/m2 \
-f /workspace/cognify/cognify_backend/pom.xml package
```

`work/` — локальные инструменты/логи, не часть исходников и не переносимый runtime. Не запускайте генераторы create.py/frontend.py: они были одноразовыми и могут перезаписать исправленный код. PostgreSQL тестовый контейнер называется `cognify-postgres`; использует только тестовые данные. В этой среде стандартная папка outputs `/codex/...` оказалась read-only; исходники сохранены в `/workspace/cognify`.

## Продолжение: миграции и сессии (21 ноября 2026)

Этот блок заменяет устаревшие сведения выше о миграциях и отзыве JWT. Добавлены Flyway V1–V3, validate по умолчанию/production, FK, индексы, ограничения роли/баланса. На копии прежней PostgreSQL и на пустой базе миграции прошли. Сессии теперь сохраняются отдельно; logout/revoke/revoke-all/password change немедленно блокируют JWT. Повтор refresh отзывает семейство и фиксируется несмотря на HTTP 401. Account row lock сериализует изменения сессий. Добавлены армянский UI безопасности и backend endpoints. Проверено: 4 backend tests, 5 frontend tests, production frontend/backend builds, HTTP smoke на migrated PostgreSQL и конкурентная ротация двух запросов (один 200, один 401, результирующий JWT отозван). Cookies и документация следующего шага еще в работе.
