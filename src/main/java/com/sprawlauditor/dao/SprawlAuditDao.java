package com.sprawlauditor.dao;

import com.sprawlauditor.model.IdleSubscription;
import com.sprawlauditor.model.ToolOverlap;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class SprawlAuditDao {

    private final Connection connection;

    public SprawlAuditDao(Connection connection) {
        this.connection = connection;
    }

    /**
     * Finds subscriptions where 1+ seats haven't logged in during the
     * given idle-threshold window, and estimates wasted monthly spend
     * as (per-seat cost * idle seat count).
     */
    public List<IdleSubscription> findIdleSubscriptions(int idleThresholdDays) throws SQLException {
        String sql =
                "WITH seat_activity AS ( " +
                        "    SELECT ss.subscription_id, ss.user_id, MAX(ue.event_date) AS last_used_date " +
                        "    FROM subscription_seats ss " +
                        "    LEFT JOIN usage_events ue " +
                        "        ON ue.subscription_id = ss.subscription_id " +
                        "       AND ue.user_id = ss.user_id " +
                        "    GROUP BY ss.subscription_id, ss.user_id " +
                        "), " +
                        "idle_seats AS ( " +
                        "    SELECT subscription_id, COUNT(*) AS idle_seat_count " +
                        "    FROM seat_activity " +
                        "    WHERE last_used_date IS NULL " +
                        "       OR last_used_date < DATE_SUB(CURDATE(), INTERVAL ? DAY) " +
                        "    GROUP BY subscription_id " +
                        ") " +
                        "SELECT s.subscription_id, s.team_id, t.tool_name, s.monthly_cost, " +
                        "       s.seats_purchased, i.idle_seat_count, " +
                        "       ROUND((s.monthly_cost / s.seats_purchased) * i.idle_seat_count, 2) AS estimated_wasted_spend " +
                        "FROM subscriptions s " +
                        "JOIN tools t ON t.tool_id = s.tool_id " +
                        "JOIN idle_seats i ON i.subscription_id = s.subscription_id " +
                        "ORDER BY estimated_wasted_spend DESC";

        List<IdleSubscription> results = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, idleThresholdDays);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(new IdleSubscription(
                            rs.getInt("subscription_id"),
                            rs.getInt("team_id"),
                            rs.getString("tool_name"),
                            rs.getDouble("monthly_cost"),
                            rs.getInt("seats_purchased"),
                            rs.getInt("idle_seat_count"),
                            rs.getDouble("estimated_wasted_spend")
                    ));
                }
            }
        }
        return results;
    }

    /**
     * Self-join on subscriptions: finds pairs of ACTIVE subscriptions
     * held by the same team, in the same tool category.
     */
    public List<ToolOverlap> findCategoryOverlaps() throws SQLException {
        String sql =
                "SELECT s1.team_id, t1.category, t1.tool_name AS tool_a, t2.tool_name AS tool_b, " +
                        "       s1.monthly_cost + s2.monthly_cost AS combined_monthly_cost " +
                        "FROM subscriptions s1 " +
                        "JOIN subscriptions s2 " +
                        "    ON s1.team_id = s2.team_id " +
                        "   AND s1.subscription_id < s2.subscription_id " +
                        "JOIN tools t1 ON t1.tool_id = s1.tool_id " +
                        "JOIN tools t2 ON t2.tool_id = s2.tool_id " +
                        "WHERE t1.category = t2.category " +
                        "  AND s1.status = 'ACTIVE' " +
                        "  AND s2.status = 'ACTIVE' " +
                        "ORDER BY combined_monthly_cost DESC";

        List<ToolOverlap> results = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                results.add(new ToolOverlap(
                        rs.getInt("team_id"),
                        rs.getString("category"),
                        rs.getString("tool_a"),
                        rs.getString("tool_b"),
                        rs.getDouble("combined_monthly_cost")
                ));
            }
        }
        return results;
    }

    /** Headline dashboard metric: total spend vs. spend already flagged for review. */
    public double[] getSpendSummary() throws SQLException {
        String sql =
                "SELECT SUM(monthly_cost) AS total_monthly_spend, " +
                        "       SUM(CASE WHEN status = 'UNDER_REVIEW' THEN monthly_cost ELSE 0 END) AS flagged_monthly_spend " +
                        "FROM subscriptions";

        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return new double[]{rs.getDouble("total_monthly_spend"), rs.getDouble("flagged_monthly_spend")};
        }
    }
}