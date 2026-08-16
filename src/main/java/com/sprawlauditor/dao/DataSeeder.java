package com.sprawlauditor.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generates a realistic-ish synthetic dataset: teams, users, a tool
 * catalog with deliberate category overlaps, subscriptions, seats,
 * and usage events skewed so some seats look genuinely idle.
 * This exists purely so the project has data to query. In a real
 * production app this class wouldn't exist at all — it's a stand-in
 * for actual user signups and usage tracking.
 */
public class DataSeeder {

    private final Connection connection;
    private final Random rng = new Random(42); // fixed seed = reproducible runs

    public DataSeeder(Connection connection) {
        this.connection = connection;
    }

    public void seed() throws SQLException {
        if (alreadySeeded()) {
            System.out.println("Data already seeded — skipping (truncate tables manually to reseed).");
            return;
        }
        int[] teamIds = insertTeams();
        int[] toolIds = insertTools();
        int[][] usersByTeam = insertUsers(teamIds);
        insertSubscriptionsSeatsAndEvents(teamIds, toolIds, usersByTeam);
    }

    private boolean alreadySeeded() throws SQLException {
        try (var stmt = connection.createStatement();
             var rs = stmt.executeQuery("SELECT COUNT(*) FROM teams")) {
            rs.next();
            return rs.getInt(1) > 0;
        }
    }

    private int[] insertTeams() throws SQLException {
        String[] teamNames = {"Growth", "Platform Eng", "Design", "Data Science", "Sales Ops"};
        int[] teamIds = new int[teamNames.length];
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO teams (team_name) VALUES (?)", Statement.RETURN_GENERATED_KEYS)) {
            for (int i = 0; i < teamNames.length; i++) {
                ps.setString(1, teamNames[i]);
                ps.executeUpdate();
                teamIds[i] = lastId(ps);
            }
        }
        return teamIds;
    }

    private int[] insertTools() throws SQLException {
        // Deliberate category collisions (3 project-management tools,
        // 2 design tools, 3 analytics tools) so overlap detection has
        // something real to find.
        String[][] toolCatalog = {
                {"Jira", "Atlassian", "project-management"},
                {"Asana", "Asana Inc", "project-management"},
                {"Linear", "Linear", "project-management"},
                {"Figma", "Figma Inc", "design"},
                {"Sketch", "Sketch B.V.", "design"},
                {"Slack", "Salesforce", "communication"},
                {"Zoom", "Zoom", "communication"},
                {"Snowflake", "Snowflake Inc", "data-warehouse"},
                {"Looker", "Google", "analytics"},
                {"Tableau", "Salesforce", "analytics"},
        };
        int[] toolIds = new int[toolCatalog.length];
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO tools (tool_name, vendor, category) VALUES (?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            for (int i = 0; i < toolCatalog.length; i++) {
                ps.setString(1, toolCatalog[i][0]);
                ps.setString(2, toolCatalog[i][1]);
                ps.setString(3, toolCatalog[i][2]);
                ps.executeUpdate();
                toolIds[i] = lastId(ps);
            }
        }
        return toolIds;
    }

    private int[][] insertUsers(int[] teamIds) throws SQLException {
        String[] teamNames = {"Growth", "PlatformEng", "Design", "DataScience", "SalesOps"};
        int[][] usersByTeam = new int[teamIds.length][];
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO users (team_id, full_name, email) VALUES (?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            for (int t = 0; t < teamIds.length; t++) {
                int usersInTeam = 6 + rng.nextInt(4); // 6-9 users per team
                usersByTeam[t] = new int[usersInTeam];
                for (int u = 0; u < usersInTeam; u++) {
                    String name = teamNames[t] + "_user" + u;
                    ps.setInt(1, teamIds[t]);
                    ps.setString(2, name);
                    ps.setString(3, name.toLowerCase() + "@company.com");
                    ps.executeUpdate();
                    usersByTeam[t][u] = lastId(ps);
                }
            }
        }
        return usersByTeam;
    }

    private void insertSubscriptionsSeatsAndEvents(int[] teamIds, int[] toolIds, int[][] usersByTeam)
            throws SQLException {
        LocalDate today = LocalDate.now();

        try (PreparedStatement subPs = connection.prepareStatement(
                "INSERT INTO subscriptions (team_id, tool_id, plan_name, monthly_cost, seats_purchased, start_date, status) " +
                        "VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE')", Statement.RETURN_GENERATED_KEYS);
             PreparedStatement seatPs = connection.prepareStatement(
                     "INSERT INTO subscription_seats (subscription_id, user_id, assigned_at) VALUES (?, ?, ?)");
             PreparedStatement eventPs = connection.prepareStatement(
                     "INSERT INTO usage_events (subscription_id, user_id, event_date, event_type) VALUES (?, ?, ?, 'LOGIN')")) {

            for (int t = 0; t < teamIds.length; t++) {
                int[] toolsForTeam = pickToolsForTeam(t, toolIds.length);

                for (int toolIdx : toolsForTeam) {
                    double baseCostPerSeat = 8 + rng.nextInt(40);
                    int seatsPurchased = usersByTeam[t].length;

                    subPs.setInt(1, teamIds[t]);
                    subPs.setInt(2, toolIds[toolIdx]);
                    subPs.setString(3, "Team plan");
                    subPs.setDouble(4, baseCostPerSeat * seatsPurchased);
                    subPs.setInt(5, seatsPurchased);
                    subPs.setString(6, today.minusMonths(6).toString());
                    subPs.executeUpdate();
                    int subscriptionId = lastId(subPs);

                    for (int userId : usersByTeam[t]) {
                        seatPs.setInt(1, subscriptionId);
                        seatPs.setInt(2, userId);
                        seatPs.setString(3, today.minusMonths(6).toString());
                        seatPs.executeUpdate();
                    }

                    // ~60% of seats stay "active" (recent logins), ~40% go stale (90-150 days since last login)
                    // so idle detection has something real to flag

                    for (int userId : usersByTeam[t]) {
                        boolean isActiveUser = rng.nextDouble() < 0.6;
                        int numEvents = isActiveUser ? 15 + rng.nextInt(20) : 1 + rng.nextInt(3);
                        int maxDaysAgo = isActiveUser ? 10 : 90 + rng.nextInt(60);

                        for (int e = 0; e < numEvents; e++) {
                            int daysAgo = isActiveUser
                                    ? rng.nextInt(Math.max(maxDaysAgo, 1))
                                    : maxDaysAgo + rng.nextInt(30);
                            eventPs.setInt(1, subscriptionId);
                            eventPs.setInt(2, userId);
                            eventPs.setString(3, today.minusDays(daysAgo).toString());
                            eventPs.executeUpdate();
                        }
                    }
                }
            }
        }
    }

    private int[] pickToolsForTeam(int teamIndex, int toolCatalogSize) {
        List<Integer> picks = new ArrayList<>();
        if (teamIndex == 0) { picks.add(0); picks.add(1); }        // Growth: Jira + Asana overlap
        else if (teamIndex == 2) { picks.add(3); picks.add(4); }   // Design: Figma + Sketch overlap
        else if (teamIndex == 1) { picks.add(2); }                 // Platform Eng: Linear
        else if (teamIndex == 3) { picks.add(7); picks.add(8); picks.add(9); } // Data Science: overlap
        else { picks.add(5); }                                     // Sales Ops: Slack

        picks.add(5); // everyone also gets Slack
        while (picks.size() < 3) {
            int candidate = rng.nextInt(toolCatalogSize);
            if (!picks.contains(candidate)) picks.add(candidate);
        }
        return picks.stream().distinct().mapToInt(Integer::intValue).toArray();
    }

    private int lastId(PreparedStatement ps) throws SQLException {
        try (var rs = ps.getGeneratedKeys()) {
            rs.next();
            return rs.getInt(1);
        }
    }

}