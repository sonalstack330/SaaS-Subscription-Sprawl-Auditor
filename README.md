# SaaS Subscription Sprawl Auditor

A database-design-first project that answers a real problem companies
pay FinOps/IT-ops tools to solve: **which SaaS subscriptions are we
wasting money on, and where are teams paying for overlapping tools?**

This isn't modeled after an existing product — it's built around an
original schema and a handful of non-trivial analytical queries, with the
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

## What it demonstrates

| Area | Where |
|---|---|
| Normalized relational schema (3NF) | `sql/schema.sql` |
| Composite index design | `idx_usage_sub_user_date` on `usage_events` |
| CTEs for multi-step aggregation | `SprawlAuditDao.findIdleSubscriptions()` |
| Self-join for overlap detection | `SprawlAuditDao.findCategoryOverlaps()` |
| Conditional aggregation (`SUM(CASE WHEN...)`) | `SprawlAuditDao.flagIdleSubscriptions()` |
| Views | `vw_seat_last_used`, `vw_idle_subscriptions` |
| Trigger / status state transitions | `trg_flag_zero_seat_subscription` |
| Read vs. write DB operations | `findIdleSubscriptions()` (SELECT) vs. `flagIdleSubscriptions()` (UPDATE) |
| Idempotent data seeding | `DataSeeder.alreadySeeded()` |
| JUnit tests against real query logic | `src/test/java/.../SprawlAuditDaoTest.java` |
| Verifying automated actions, not just trusting a count | `SprawlAuditDao.findFlaggedSubscriptions()` |

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

## Reseeding with fresh data

`DataSeeder` is idempotent — if `teams` already has rows, it skips seeding
entirely instead of inserting duplicates. This means running `Main` a second
time will **not** regenerate new random data; it just re-runs the reports
against whatever's already there.

To force fresh synthetic data (e.g. after changing pricing or the tool
catalog in `DataSeeder.java`), truncate all tables first:

```powershell
mysql -u root -p
```
Then inside the MySQL prompt:

```sql
USE sprawl_auditor;
SET FOREIGN_KEY_CHECKS=0;
TRUNCATE usage_events;
TRUNCATE subscription_seats;
TRUNCATE subscriptions;
TRUNCATE tools;
TRUNCATE users;
TRUNCATE teams;
SET FOREIGN_KEY_CHECKS=1;
exit;
```

Tables must be truncated in this order (child tables before parent tables)
to satisfy foreign key constraints — reversing the order will fail even with
`FOREIGN_KEY_CHECKS` disabled in some MySQL configurations.

Then rerun `Main.java` — it will detect the empty `teams` table and reseed from scratch.

## Sample output

```
== Idle subscriptions (no login in 60+ days) ==
  [Sub #12] Tableau        team=4  cost=₹12054.00  seats=6  idle=6  wasted=₹12054.00/mo
  [Sub #4 ] Asana          team=2  cost=₹12150.00  seats=6  idle=5  wasted=₹10125.00/mo
  [Sub #8 ] Canva          team=3  cost=₹10432.00  seats=8  idle=6  wasted=₹7824.00/mo
  ...
  --> Estimated total wasted spend: ₹66520.00/mo

== Category overlaps (same team, 2+ tools, same category) ==
  [Team 5] communication        Slack <-> Freshchat        combined=₹29358.00/mo
  [Team 3] design               Figma <-> Canva            combined=₹27208.00/mo
  [Team 4] analytics            Zoho Analytics <-> Tableau combined=₹24624.00/mo
  [Team 1] project-management   Jira <-> Zoho Projects     combined=₹11720.00/mo
  [Team 2] communication        Freshchat <-> Slack        combined=₹10350.00/mo

== Running idle-detection job ==
  Flagged 5 subscription(s) as UNDER_REVIEW

== Subscriptions currently UNDER_REVIEW ==
  [Sub #13] Slack       team=4  cost=₹12654.00
  [Sub #4 ] Asana       team=2  cost=₹12150.00
  [Sub #12] Tableau     team=4  cost=₹12054.00
  [Sub #8 ] Canva       team=3  cost=₹10432.00
  [Sub #5 ] Freshchat   team=2  cost=₹5784.00
```

## Tests

Run `SprawlAuditDaoTest` in IntelliJ. Tests use `@BeforeEach` to reset every
subscription to `ACTIVE` before each test runs — without this, tests that
flag subscriptions would interfere with tests that check for active-only
overlaps, since JUnit doesn't guarantee test execution order.

