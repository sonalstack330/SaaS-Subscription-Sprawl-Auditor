# SaaS Subscription Sprawl Auditor

A database-design-first project that answers a real problem companies
pay FinOps/IT-ops tools to solve: **which SaaS subscriptions are we
wasting money on, and where are teams paying for overlapping tools?**

This isn't modeled after an existing product — it's built around an
original schema and two non-trivial analytical queries, with the
database doing the real work (not application-side loops).

## What it does

1. Seeds a MySQL database with a synthetic company: teams, Indian SaaS tools
   (Zoho, Freshworks, Chargebee, etc.), subscriptions, and login history —
   deliberately shaped so some seats look unused and some teams pay for
   overlapping tools.
2. Reports which subscriptions have mostly-idle seats, and estimates the
   wasted monthly spend.
3. Reports which teams are paying for 2+ tools that do the same job.
4. Flags subscriptions with 50%+ idle seats as `UNDER_REVIEW` — a nightly
   batch-job pattern that marks waste for a human to review, without
   auto-cancelling anything.
5. 
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

## Project layout

```
sprawl-auditor/
├── sql/
│ └── schema.sql # MySQL schema: tables, indexes, views, trigger
├── src/
│ ├── main/java/com/sprawlauditor/
│ │ ├── Main.java # runs the seeder + all reports
│ │ ├── config/DatabaseManager.java # reads db.properties, opens JDBC connection
│ │ ├── dao/
│ │ │ ├── DataSeeder.java # synthetic teams/tools/subscriptions/usage
│ │ │ └── SprawlAuditDao.java # all queries: idle detection, overlaps, flagging
│ │ └── model/
│ │ ├── IdleSubscription.java
│ │ └── ToolOverlap.java
│ ├── main/resources/
│ │ └── db.properties.example # copy to db.properties, fill in your credentials
│ └── test/java/com/sprawlauditor/dao/
│ └── SprawlAuditDaoTest.java # 8 tests covering all DAO methods
└── pom.xml

```
## Setup

1. Install MySQL locally, confirm it's running on port 3306
2. Run the schema:
```powershell
   mysql -u root -p < sql\schema.sql
```
3. Copy `db.properties.example` to `db.properties` (same folder), fill in your
   actual MySQL username/password — this file is gitignored and never committed
4. `mvn compile` to confirm the build works
5. Run `Main.java` — first run seeds the database; subsequent runs skip
   seeding automatically and just re-run the reports

##Sample Output

## Setup

1. Install MySQL locally, confirm it's running on port 3306
2. Run the schema:
```powershell
   mysql -u root -p < sql\schema.sql
```
3. Copy `db.properties.example` to `db.properties` (same folder), fill in your
   actual MySQL username/password — this file is gitignored and never committed
4. `mvn compile` to confirm the build works
5. Run `Main.java` — first run seeds the database; subsequent runs skip
   seeding automatically and just re-run the reports

## Tests

Run `SprawlAuditDaoTest` in IntelliJ. Tests use `@BeforeEach` to reset every
subscription to `ACTIVE` before each test runs — without this, tests that
flag subscriptions would interfere with tests that check for active-only
overlaps, since JUnit doesn't guarantee test execution order.
