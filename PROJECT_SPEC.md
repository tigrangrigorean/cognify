# Cognify architecture and requirements
Requested product: Armenian academic document SaaS, no subscriptions, Next.js App Router JavaScript frontend and Spring Boot 3.5.6 Java 17 backend, PostgreSQL.

## Confirmed design change — 21 November 2026
The complete original functional scope is retained, including Coins and payments. Only visual presentation is simplified: plain white/blue layout, fewer decorative elements, no mock document illustration or button motion. No functional modules are canceled or replaced by a minimal generator-only product.

## Architecture
Browser → Next.js auth BFF (HttpOnly cookie, Origin/CSRF checks) or same-origin Bearer /api proxy → Spring Security JWT + persisted session validation → controllers → transactional services → JPA → PostgreSQL. Passwords use BCrypt. Refresh tokens are opaque, hashed at rest, rotated and bound to persisted user sessions. Account row locks serialize rotation and revocation; replay revokes the session. Password reset uses hashed, expiring one-time tokens and a real configurable SMTP adapter. Flyway V1–V11 manage the schema and Hibernate validates it by default. Documents belong to their creator. API uses DTOs. Backend owns all credentials and financial decisions.

## Delivery stages
1. Repository analysis and architecture
2. Frontend/backend/PostgreSQL setup
3. Authentication
4. Landing and CMS
5. Dashboard
6. Wallet and Coins
7. Academic tools
8. DOCX/PDF analysis
9. Formatting profiles
10. Durable generation pipeline
11. Versioned editor
12. DOCX/PDF export
13. Research, formatting, checking, citations
14. Administration
15. Payment provider architecture
16. Integration/security/export/end-to-end tests

## Subsequent implementation constraints
Generation must persist steps and leases, survive restart, support pause/resume/cancel/retry, reserve coins transactionally and settle exactly once. PDF analysis must report uncertainty. Scientific search and payment integrations must use real documented APIs; no fabricated sources or production payment callbacks. AI providers need a common interface and backend-only keys. Profiles must be applied to exported documents. Admin access must not expose private document content. Production requires Flyway, TLS, managed secrets, rate limits, backups and a deployment security review.

The original complete request is preserved in REQUEST.txt.

## CMS implementation checkpoint
CmsBlock stores ordered, visible, versioned public blocks with validated JSON item lists. CmsContent stores branding, hero, button, SEO and information-page strings. CmsService saves changes and AuditLog in one transaction; controllers expose DTOs only. ADMIN-only UI `/admin` uses protected APIs. Public dynamic pages fetch backend data without caching. Fixed hero/intro layout and absence of custom block types/file-upload are explicit remaining limitations. Audit covers CMS and financial changes.

## Coin accounting implementation
Wallet.balance is total Coins; wallet.reserved is the held part; available = balance - reserved. Every mutation takes a pessimistic wallet-row lock. CoinTransaction and audit log commit atomically with wallet/reservation changes. Request keys are scoped to owner and operation; a replay with changed parameters is rejected. Reserve/settle/release are server-only job methods, never endpoints accepting a browser price. Refunds cannot exceed settled cost. Package/price configuration uses optimistic versions. Payment snapshots freeze purchased quantity and amount. Real payment processing remains disabled; TEST provider needs explicit dev mode and a dedicated test database. Production must use a real documented vPOS adapter, authenticated notifications, reconciliation and an outbox before any real charge.


## Generation/editor/export implementation checkpoint
GenerationJob and GenerationStep persist input snapshots, plans, attempts, provider/model, 180-second leases and billing reservation IDs. Network calls run outside transactions; completion is fenced by lease token. Approved plans create sections. Completed generation creates the document, sections, AI usage and settles Coins atomically. Retry preserves completed sections, allocates a fresh reservation after release, and never settles the same reservation twice. At most three attempts per stage; pause clears the lease and retains the reservation. The worker recovers expired leases after restart. Provider calls are at-least-once; only document/Coins commits are fenced. Provider usage logs cover accepted successful responses only.

AI_MODE defaults disabled. OpenAI Chat Completions uses a backend-only key, HTTPS, request timeout and safe error codes. Explicit mock is restricted to dev, disallowed with prod, and marked in the UI/export. No external AI call has been verified without credentials. Generation supports hy/ru/en, four academic document types, 1–40 requested pages, editable 3–12 heading plans, cancel/pause/resume/retry. Word count determines final Coins (350 words/page), capped by accepted server quote.

Document sections and metadata share the aggregate document version and a document-row lock. Edits snapshot previous title/content and reject stale writes. The UI exposes section CRUD/order and the last ten snapshots. Apache POI exports owned documents as DOCX with fixed A4 formatting, title page, heading list, headings and page numbering. No dynamic TOC/page references, university profile application, PDF, source retrieval, sample analysis, rich text or fragment regeneration yet. These original requirements remain pending.
