# Cognify — состояние разработки

Обновлено: 21 ноября 2026. **Полное ТЗ остается в силе. Продукт пока не завершен.**

## Последнее уточнение пользователя

Упростить только дизайн; не сокращать функциональность и не заменять ТЗ минимальным сайтом генерации. Coins остаются в основном сценарии. Убраны декоративный макет документа, градиент и движение кнопок; белый/синий интерфейс стал проще. Все рабочие разделы сохранены.

## Последнее продолжение: Coins

- Миграции V8–V9: reserved balance, coin transactions/reservations, packages, pricing rules, payment records и usage. Изначальные пакет/тариф выключены.
- WalletService: wallet-row lock, reserve/settle/release, partial refund, admin adjustment. Ledger и audit атомарны; повторный requestKey не проводит операцию повторно, измененный payload → 409. Нельзя потратить hold или вернуть больше списанного. Используются целые Coins и minor currency units.
- Reserve/settle/release — внутренние server-side методы для будущего AI pipeline. Пользователь не может прислать произвольную цену/списание через REST. Пока генерации нет, автоматического списания за AI нет.
- UI кошелька: доступно/амрագրված/всего, история, последние резервы/платежи, тестовые пакеты. Финансовая панель `/admin/finance`: пакеты, тарифы, поиск пользователя по email, корректировка с обязательной причиной, возврат Coins за операцию. Аудит в `/admin`.
- PaymentProvider: DISABLED по умолчанию и TEST при явном dev + TEST_PAYMENTS_ENABLED=true. С prod или без dev включенный test provider блокирует запуск. TEST payment проверяется локальным backend, не frontend callback, и начисляется один раз. Банковские API не выдуманы и не подключены.
- Frontend сохраняет request ID незавершенной финансовой операции через sessionStorage с хешем payload, чтобы повтор после сбоя/перезагрузки использовал тот же ключ. Запоздалый complete старой операции не удаляет новый pending key.

## Проверки текущего состояния

- Backend `mvn package`: **17 тестов**, 0 failures/errors, сборка JAR прошла. Из них WalletIntegrationTest — 8 сценариев.
- Те же **8 wallet-сценариев отдельно прошли на PostgreSQL 17**, включая параллельные reserve, adjustment, settlement, refund, release-vs-settle и test payment.
- Frontend `npm test`: **19 тестов**, все прошли. `npm run build` также прошел, включая `/admin/finance`.
- HTTP `tests/coins_smoke.py` на dev + test provider: повторный параллельный платеж начисляет один раз, чужая история недоступна, корректировка доступна только ADMIN и требует причину, changed-payload replay отклоняется, quote/audit работают.
- Повторно прошли `tests/api_smoke.py` и live BFF handler integration на backend с prod profile. Payment mode в prod проверен как disabled.
- Миграция существующей тестовой PostgreSQL V7→V9 прошла; отдельная пустая финансовая test DB также получила все миграции.
- Browser QA снова заблокирован: `relay could not open browser port 4173`. Визуальные изменения и новые UI-формы проверены сборкой, но не браузером.
- Реальные AI API, банковский платеж/денежный возврат и SMTP delivery не проверялись: интеграции/реквизиты отсутствуют. Полный сценарий ТЗ с анализом образца и DOCX/PDF пока невозможен.

## Статус этапов

| Этап | Состояние |
| --- | --- |
| 1. Анализ/архитектура | Есть PROJECT_SPEC и исходный REQUEST.txt |
| 2. Setup/DB | Next.js JS/JSX + Spring Boot 3.5.6/Java17 + PostgreSQL, Flyway V1–V9 |
| 3. Авторизация | JWT/sessions/rotation/replay/revoke, HttpOnly BFF/CSRF, password change/reset, SMTP adapter; нет email verification/shared limiter/outbox |
| 4. Landing/CMS | DB blocks, settings/SEO, публичные страницы, admin UI + audit; hero/intro фиксированы, logo только URL |
| 5. Dashboard | Документы, available balance, wallet history, account security; уведомления/генерации еще отсутствуют |
| 6. Wallet/Coins | Транзакционное ядро, каталог, quote, тестовый payment, UI и админка; AI integration/банковский checkout еще отсутствуют |
| 7. Академические инструменты | Четыре типа ручных черновиков; AI-параметры/генерация не реализованы |
| 8. Analyzer | Не реализован |
| 9. Formatting profiles | Не реализованы |
| 10. AI pipeline | Не реализован |
| 11. Editor | Plain text CRUD и optimistic locking; без секций/версий/rich text/AI |
| 12. DOCX/PDF export | Не реализован |
| 13. Research/formatter/checker/citations | Не реализованы |
| 14. Admin | CMS/audit и финансовая конфигурация/корректировки; нет полного управления пользователями/статистики/AI |
| 15. Payments | Интерфейс + явно TEST provider; real vPOS, статус/reconciliation/webhook/денежные refunds не реализованы |
| 16. QA | Описано выше; не полный E2E |

## Следующий точный шаг

Продолжить академические инструменты и основу поэтапной генерации (этапы 7/10), не менять рабочие auth/CMS/ledger APIs:

1. Добавить AI-provider interface и реальный конфигурируемый adapter с backend-only key, timeout/error handling, usage logging. Без ключа — явная недоступность, не поддельный AI.
2. Ввести GenerationJob/GenerationStep с durable state и lease: план → подтверждение → разделы → сборка. Выполнение асинхронное, состояние сохраняется в PostgreSQL, нужны pause/resume/cancel/retry и recovery после перезапуска.
3. Перед стартом вычислять quote на backend, показывать цену пользователю, reserve через WalletService. После результата settle actual cost, при окончательной ошибке/отмене release. Сохранять reservationId/jobId; не списывать заново при retry.
4. Простой армянский интерфейс: тема, тип, язык, объем, требования, затем план/прогресс/редактор. Все оставшиеся функции исходного ТЗ, включая анализ образца, оформление, экспорт и источники, сохраняются в очереди.
5. Добавить тесты job recovery и согласованности job/reservation. Затем Analyzer/Formatting profiles и DOCX/PDF export.

## Ограничения и важные инварианты

- Баланс total >= reserved >= 0; available=total-reserved. `/api/me.balance` теперь available.
- Ledger и audit не имеют REST API редактирования/удаления. Ручные корректировки имеют reason и ADMIN check. Проверка пользователя в платежах всегда по JWT, сумма — по DB package snapshot.
- Возврат Coins за потребленную операцию не является денежным банковским refund.
- Тестовые Coins нужно держать в отдельной DB; переносить ее в production нельзя. PaymentProvider пока синхронный локальный TEST; для настоящего vPOS необходимы outbox, проверка подписи/status и reconciliation.
- Held reservations пока не истекают автоматически: освобождение будет связано с lifecycle будущих durable jobs. Не создавать реальные holds до подключения job recovery.
- В pricing/usage пока нет AI tokens/real provider costs; отображается расчет по заданным единицам и запись settled Coins.
- Rate limit in-memory и видит адрес прокси; нужен distributed limiter/trusted proxy. Остались CSP/XSS review, cleanup tokens, security audit и SMTP delivery/outbox.
- CMS текстовые настройки не имеют client-side version check; блоки/пакеты/тарифы имеют. Политика/условия предварительные, требуют данных владельца.

## Окружение и продолжение

Один репозиторий `/workspace/cognify`. README содержит startup, API, migration, SMTP, Coins и тестовые команды. `.env.example` не содержит реальных секретов. ZIP исходников обновляется в `work/cognify-source.zip` после commit.

Maven/JDK этого окружения в игнорируемом work/:

```bash
JAVA_HOME=/workspace/cognify/work/jdk-17.0.21.1+2 \
MAVEN_OPTS=-Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts \
/workspace/cognify/work/apache-maven-3.9.11/bin/mvn \
-s /workspace/cognify/work/maven-settings.xml \
-Dmaven.repo.local=/workspace/cognify/work/m2 \
-f /workspace/cognify/cognify_backend/pom.xml package
```

Для PostgreSQL wallet tests добавьте env `COGNIFY_POSTGRES_TEST_URL=jdbc:postgresql://localhost:5432/cognify_coins_test`, `COGNIFY_POSTGRES_TEST_PASSWORD` и `-Dtest=WalletIntegrationTest test`. Контейнер `cognify-postgres`. Исходная dev-БД `cognify` не менялась. `cognify_phase2_fresh` используется для prod-profile smoke без реальных платежей; `cognify_coins_test` — отдельная финансовая тестовая база с TEST credits и тестовыми администраторами. Последние HTTP тесты: 8080 prod profile, 8081 dev test payments. Процессы могут не сохраниться между сеансами.

Не перезаписывайте JAR, из которого запущен backend: для runtime берите отдельную копию в work/. Временные генераторы файлов в work/ были одноразовыми; повторный запуск может затереть изменения. Не коммитить work/, .env, DB dumps. Папка выдачи `/codex/...` в этой среде read-only; исходники и ZIP доступны в workspace.
