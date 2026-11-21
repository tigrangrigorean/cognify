# Cognify architecture and requirements
Requested product: Armenian academic document SaaS, no subscriptions, Next.js App Router JavaScript frontend and Spring Boot 3.5.6 Java 17 backend, PostgreSQL.

## Architecture
Browser → Next.js auth BFF (HttpOnly cookie, Origin/CSRF checks) or same-origin Bearer /api proxy → Spring Security JWT + persisted session validation → controllers → transactional services → JPA → PostgreSQL. Passwords use BCrypt. Refresh tokens are opaque, hashed at rest, rotated and bound to persisted user sessions. Account row locks serialize rotation and revocation; replay revokes the session. Password reset uses hashed, expiring one-time tokens and a real configurable SMTP adapter. Flyway V1–V7 manage the schema and Hibernate validates it by default. Documents belong to their creator. API uses DTOs. Backend owns all credentials and financial decisions.

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
CmsBlock stores ordered, visible, versioned public blocks with validated JSON item lists. CmsContent stores branding, hero, button, SEO and information-page strings. CmsService saves changes and AuditLog in one transaction; controllers expose DTOs only. ADMIN-only UI `/admin` uses protected APIs. Public dynamic pages fetch backend data without caching. Fixed hero/intro layout and absence of custom block types/file-upload are explicit remaining limitations. Audit covers CMS only at this stage.
