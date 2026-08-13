-- SaaS Subscription Sprawl Auditor — MySQL Schema --

CREATE DATABASE IF NOT EXISTS sprawl_auditor;
USE sprawl_auditor

-- ---------------------------------------------------------------------
-- 1. Core entities
-- ---------------------------------------------------------------------

CREATE TABLE teams (
    team_id     INT PRIMARY KEY AUTO_INCREMENT,
    team_name   VARCHAR(100) NOT NULL,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE users(
    user_id     INT PRIMARY KEY AUTO_INCREMENT,
    team_id     INT NOT NULL,
    full_name   VARCHAR(50) NOT NULL,
    email       VARCHAR(50) NOT NULL UNIQUE,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (team_id) REFERENCES teams(team_id)
);

-- Reference catalog of known SaaS tools, so overlap detection has a
-- shared vocabulary of "category" to match tools against each other
CREATE TABLE tools
(
    tool_id   INT PRIMARY KEY AUTO_INCREMENT,
    tool_name VARCHAR(50) NOT NULL,
    vendor    VARCHAR(50),
    category  VARCHAR(50) NOT NULL,
    UNIQUE (tool_name)
);

-- A team's paid subscription to a tool
CREATE TABLE subscriptions
(
    subscription_id INT PRIMARY KEY AUTO_INCREMENT,
    team_id         INT            NOT NULL,
    tool_id         INT            NOT NULL,
    plan_name       VARCHAR(50),
    monthly_cost    DECIMAL(10, 2) NOT NULL,
    seats_purchased INT            NOT NULL DEFAULT 1,
    start_date      DATE           NOT NULL,
    status          VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    ACTIVE | UNDER_REVIEW |  CANNCELLED
    FOREIGN KEY (team_id)   REFERENCES teams(team_id),
    FOREIGN KEY (tool_id) REFERENCES tools (tool_id),
        UNIQUE (team_id, tool_id)
);

-- Who on the team actually has a seat on that subscription
CREATE TABLE subscription_seats
(

    seat_id           INT PRIMARY KEY AUTO_INCREMENT,
    subscription_id   INT NOT NULL,
    user_id           INT NOT NULL,
    assigned_at       DATE NOT NULL,
    FOREIGN KEY (subscription_id) REFERENCES subscriptions(subscription_id),
    FOREIGN KEY (user_id) REFERENCES users(user_id),
    UNIQUE (subscription_id, user_id)
);

-- Event log: every login/usage ping for a tool by a user
CREATE TABLE usage_events
(
    event_id        BIGINT PRIMARY KEY AUTO_INCREMENT,
    subscription_id INT         NOT NULL,
    user_id         INT         NOT NULL,
    event_id        DATE        NOT NULL,
    event_type      VARCHAR(20) NOT NULL DEFAULT 'LOGIN',
    FOREIGN KEY (subscription_id) REFERENCES subscriptions (subscription_id),
    FOREIGN KEY (user_id) REFERENCES users (user_id)
);

-- ---------------------------------------------------------------------
-- 2. Indexes
-- ---------------------------------------------------------------------

-- Composite/covering index: leftmost column (subscription_id) covers
-- per-tool scans; including user_id + event_date lets the idle-detection query
-- be answered entirely from the index.

CREATE INDEX idx_usage_sub_user_date
    ON subscriptions (subscription_id, user_id, event_date DESC);

CREATE INDEX idx_subscription_team_tool
    ON subscriptions(team_id, tool_id);

CREATE INDEX idx_tools_category
    ON tools (category);

-- ---------------------------------------------------------------------
-- 3. Views
-- ---------------------------------------------------------------------

CREATE VIEW vw_seat_last_used AS
SELECT
    ss.subscription_id,
    ss.user_id,
    MAX(ue.event_date) AS last_used_date
FROM subscription_seats ss
LEFT JOIN usage_events ue
        ON ue.subscription_id = ss.subscription_id
        AND ue.user_id  = ss.user_id
GROUP BY ss.subscription_id, ss.user_id;

CREATE VIEW vw_idle_subscriptions AS
SELECT
    s.subscription_id,
    s.team_id,
    t.tool_name,
    s.monthly_cost,
    s.seats_purchased,
    COUNT(DISTINCT vslu.user_id) AS seats_with_no_recent_use
FROM subscriptions s
         JOIN tools t ON t.tool_id = s.tool_id
         JOIN vw_seat_last_used vslu ON vslu.subscription_id = s.subscription_id
WHERE s.status = 'ACTIVE'
  AND (vslu.last_used_date IS NULL
    OR vslu.last_used_date < DATE_SUB(CURDATE(), INTERVAL 60 DAY))
GROUP BY s.subscription_id, s.team_id, t.tool_name, s.monthly_cost, s.seats_purchased;

-- ---------------------------------------------------------------------
-- 4. Trigger — auto-flag a subscription UNDER_REVIEW once its last remaining seat is removed
-- ---------------------------------------------------------------------

DELIMITER $$

CREATE TRIGGER trg_flag_zero_seat_subscription
    AFTER DELETE ON subscription_seats
    FOR EACH ROW
BEGIN
    DECLARE remaining_seats INT;
    SELECT COUNT(*) INTO remaining_seats
    FROM subscription_seats
    WHERE subscription_id = OLD.subscription_id;

    IF remaining_seats = 0 THEN
    UPDATE subscriptions
    SET status = 'UNDER_REVIEW'
    WHERE subscription_id = OLD.subscription_id;
END IF;
END$$

DELIMITER ;




