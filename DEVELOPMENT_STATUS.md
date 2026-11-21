# Cognify — состояние разработки

Обновлено: 21 ноября 2026. **Полное ТЗ остается в силе. Основной сценарий реализован, весь продукт еще не завершен.**

## Уточнение пользователя

Упростить только дизайн. Coins и остальные функции исходного ТЗ сохраняются. Интерфейс белый/синий, без декоративного макета документа, градиентов и движения кнопок.

## Последнее продолжение: генерация → редактор → DOCX

- Flyway V10: persistent GenerationJob/GenerationStep, AI usage, разделы и текстовые снимки документов. V11 обновляет только неизмененные начальные тексты CMS.
- AiProvider + OpenAI Chat Completions: backend-only key, HTTPS, 90s timeout, безопасные коды ошибок. AI_MODE=disabled по умолчанию. Без ключа нет поддельной генерации. Mock включается явно только в dev, запрещен с prod и маркируется в UI/DOCX.
- Тема/тип/язык/объем/требования → план → редактирование/подтверждение → фоновые разделы → документ. Доступны пауза, продолжение, отмена и повтор после ошибки. Состояние хранится в PostgreSQL; 180s lease и проверка token отсекают запоздалые результаты. Просроченные задания подхватываются после перезапуска.
- Coins: сервер проверяет подтвержденную цену, резервирует максимум; итоговый документ и settle фиксируются одной транзакцией. Расчетная страница = 350 слов, итог не выше оценки. Ошибка/отмена освобождает резерв; retry создает новый резерв и сохраняет готовые разделы. Idempotency key защищает повторный старт. Два активных задания на пользователя, три попытки этапа.
- Редактор: отдельные разделы, добавление/удаление/порядок, изменение названия/типа, optimistic version общего документа. Текстовые снимки до изменения, просмотр последних десяти. Несохраненные изменения предупреждаются перед операциями, которые их заменяют.
- DOCX через Apache POI: A4, Sylfaen 14, интервал 1.5, поля, титульная страница, список разделов, заголовки и номера страниц. Только владелец имеет доступ. PDF и профили вуза пока отсутствуют.
- Добавлены Dockerfiles и compose.app.yaml для совместного запуска frontend/backend/PostgreSQL; инструкция и конфигурация AI в README. Полный Docker build еще не проверен.

## Проверки

- `mvn package`: **23 теста**, 0 failures/errors, JAR собран.
- **6 новых integration-сценариев** генерации прошли и на PostgreSQL 17: полный цикл/редактор/DOCX/изоляция владельцев, idempotency/цена, pause/cancel с запоздалым ответом, failure/retry, expired-lease recovery, запрет mock в prod.
- Ранее **8 финансовых сценариев** отдельно прошли на PostgreSQL, включая параллельные reserve/adjustment/settlement/refund/release/test payment.
- `npm test`: **20 тестов**, все прошли; добавлена проверка бинарного скачивания после refresh JWT. `npm run build` прошел.
- `tests/generation_smoke.py`: настоящий HTTP и фоновый worker на PostgreSQL, **явно MOCK AI и TEST payment**. Регистрация → пополнение 100 → резерв 30 → план → подтверждение → три раздела → итоговое списание 10 → редактор/история → DOCX. Итоговый баланс 90, резерв 0. Проверены повторный старт и запрет чужого скачивания.
- Браузерная проверка не выполнена: повторный запуск Sites preview завершился `relay could not open browser port 4173: timed out waiting for relay readiness`.
- Реальные OpenAI ответы/качество, банковские платежи и SMTP delivery не проверены. Наличие adapter не означает успешный внешний вызов.

## Статус полного ТЗ

| Этап | Состояние |
| --- | --- |
| Архитектура/setup | Один репозиторий, Next.js JS/JSX, Spring Boot 3.5.6/Java17, PostgreSQL, Flyway V1–V11 |
| Авторизация | JWT, persisted sessions, rotation/replay/revoke, HttpOnly BFF/CSRF, смена/сброс пароля, SMTP adapter |
| Landing/CMS | Публичные блоки/страницы, branding/SEO/settings, admin, audit |
| Dashboard | Документы, генерации, Coins, история, настройки безопасности |
| Wallet/Coins | Ledger, резервы/списания/возвраты, каталог, quote, ADMIN adjustments, AI integration |
| Академические инструменты | Четыре типа документов, hy/ru/en, параметры, план и генерация |
| Analyzer | Не реализован |
| Formatting profiles | Не реализованы |
| AI pipeline | Durable jobs/leases, plan approval, sections, pause/resume/cancel/retry, usage; external AI еще не проверен |
| Editor | Разделы, порядок, версии, plain text; без rich text/восстановления снимка/AI fragment rewrite |
| Export | DOCX с фиксированным оформлением; нет PDF, профилей и динамического оглавления |
| Research/checker/citations | Не реализованы |
| Admin | CMS/audit, финансовая конфигурация/корректировки; нет полного управления пользователями/AI статистики |
| Payments | Интерфейс + TEST provider; real vPOS/webhook/reconciliation/денежные refunds не реализованы |
| QA | Unit/integration/PostgreSQL/HTTP; нет полного browser E2E и production review |

## Следующие этапы

1. Проверить реальный AI с backend-ключом: объем, качество армянского текста, ошибки/таймауты и стоимость. Пройти UI в браузере после восстановления preview.
2. Реализовать загрузку и анализ DOCX/PDF образцов, уверенность анализа и редактируемые профили оформления.
3. Применять профили к DOCX, добавить PDF и полноценное оглавление.
4. Добавить источники/цитирования через документированные API, проверку и доработку фрагментов, остальную админку.
5. Подключить реальный платежный провайдер после получения документации/реквизитов, outbox/reconciliation; завершить production deployment/security/SMTP проверки.

## Ограничения и инварианты

- total >= reserved >= 0; available=total-reserved. Цены/владелец определяются backend; ledger/audit недоступны для редактирования через REST. Возврат Coins не является возвратом банковских денег.
- При паузе/ожидании подтверждения hold сохраняется до отмены/продолжения. Автоматического удаления брошенных заданий нет.
- Внешний AI-запрос при crash recovery может повториться: exactly-once относится к результату/Coins, не к расходам провайдера. Usage учитывает только успешно сохраненные ответы; не заменяет счет провайдера.
- Запрошенные страницы приблизительны; физическая пагинация зависит от оформления. Нет автоматической проверки источников/цитат/оригинальности.
- Тестовые Coins/mock держать в отдельной DB. Не переносить ее в production.
- Rate limiter in-memory, нет email verification/shared limiter/outbox, нужны production TLS/backups/security review. Политика/условия предварительные.

## Окружение и продолжение

Репозиторий `/workspace/cognify`. Исходное ТЗ — REQUEST.txt. Архив после commit — `work/cognify-source.zip`, без секретов и зависимостей.

JDK/Maven — в игнорируемом work/:

```bash
JAVA_HOME=/workspace/cognify/work/jdk-17.0.21.1+2 \
MAVEN_OPTS=-Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts \
/workspace/cognify/work/apache-maven-3.9.11/bin/mvn \
-s /workspace/cognify/work/maven-settings.xml \
-Dmaven.repo.local=/workspace/cognify/work/m2 \
-f /workspace/cognify/cognify_backend/pom.xml package
```

Контейнер `cognify-postgres`; отдельная БД `cognify_generation_test` для тестов генерации, `cognify_coins_test` для финансов, `cognify_phase2_fresh` для prod-profile smoke. Исходная `cognify` этими тестами не менялась. HTTP mock backend запускался на 8082; процессы могут не сохраниться между сеансами.

Не перезаписывать JAR работающего backend: использовать отдельную копию work/. Одноразовые генераторы файлов в work/ не запускать повторно. Не коммитить work/, .env или DB dumps. Папка выдачи /codex/... в этой среде read-only; исходники/ZIP находятся в workspace.
