# SaaS Subscription Sprawl Auditor

A database-design-first project that answers a real problem companies
pay FinOps/IT-ops tools to solve: **which SaaS subscriptions are we
wasting money on, and where are teams paying for overlapping tools?**

This isn't modeled after an existing product — it's built around an
original schema and two non-trivial analytical queries, with the
database doing the real work (not application-side loops).

## What it demonstrates

| Area | Where |
|---|---|
| Normalized relational schema (3NF) | `sql/schema.sql` |
| Composite / covering index design | `idx_usage_sub_user_date` — verified with `EXPLAIN QUERY PLAN` below |
| Window functions (`LAG`, gap analysis) | `sql/analytical_queries.sql` Q1 |
| CTEs for multi-step aggregation | `SprawlAuditDao.findIdleSubscriptions()` |
| Self-join for overlap detection | `SprawlAuditDao.findCategoryOverlaps()` |
| Views | `vw_seat_last_used`, `vw_idle_subscriptions` |
| Triggers / status state transitions | `trg_flag_zero_seat_subscription` |
| Scheduled batch job pattern | `IdleDetectionJob` (stands in for Spring `@Scheduled`) |
| Schema migration versioning | `sql/migrations/` (Flyway-style) |
| Polyglot analysis (Java + Python) | `python/analyze.py` reproduces the SQL reports in pandas |
