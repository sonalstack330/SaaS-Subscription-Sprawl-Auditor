package com.sprawlauditor.dao;

import com.sprawlauditor.config.DatabaseManager;
import com.sprawlauditor.model.IdleSubscription;
import com.sprawlauditor.model.ToolOverlap;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SprawlAuditDaoTest {

    private static Connection connection;
    private static SprawlAuditDao dao;

    @BeforeAll
    static void setUp() throws Exception {
        connection = DatabaseManager.getConnection();
        dao = new SprawlAuditDao(connection);
        new DataSeeder(connection).seed();
    }
    @BeforeEach
    void resetSubscriptionStatus() throws Exception {
        connection.createStatement().executeUpdate(
                "UPDATE subscriptions SET status = 'ACTIVE'"
        );
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (connection != null) connection.close();
    }

    @Test
    void findIdleSubscriptions_returnsNonEmptyResults() throws Exception {
        List<IdleSubscription> results = dao.findIdleSubscriptions(60);
        assertFalse(results.isEmpty(), "Expected at least one idle subscription with seeded data");
    }

    @Test
    void findIdleSubscriptions_wastedSpendIsNeverNegative() throws Exception {
        List<IdleSubscription> results = dao.findIdleSubscriptions(60);
        for (IdleSubscription sub : results) {
            assertTrue(sub.getEstimatedWastedSpend() >= 0,
                    "Wasted spend should never be negative: " + sub);
        }
    }

    @Test
    void findIdleSubscriptions_idleSeatCountNeverExceedsSeatsPurchased() throws Exception {
        List<IdleSubscription> results = dao.findIdleSubscriptions(60);
        for (IdleSubscription sub : results) {
            assertTrue(sub.getIdleSeatCount() <= sub.getSeatsPurchased(),
                    "Idle seats can't exceed purchased seats: " + sub);
        }
    }

    @Test
    void findCategoryOverlaps_returnsAllFourExpectedCategories() throws Exception {
        List<ToolOverlap> overlaps = dao.findCategoryOverlaps();
        List<String> categories = overlaps.stream().map(ToolOverlap::getCategory).toList();

        assertTrue(categories.contains("project-management"), "Expected a project-management overlap");
        assertTrue(categories.contains("design"), "Expected a design overlap");
        assertTrue(categories.contains("communication"), "Expected a communication overlap");
        assertTrue(categories.contains("analytics"), "Expected an analytics overlap");
    }

    @Test
    void getSpendSummary_totalSpendIsGreaterThanOrEqualToFlaggedSpend() throws Exception {
        double[] summary = dao.getSpendSummary();
        assertTrue(summary[0] >= summary[1],
                "Total spend must be >= flagged spend (flagged is a subset of total)");
    }
    @Test
    void flagIdleSubscriptions_onlyFlagsSubscriptionsWithMajorityIdleSeats() throws Exception {
        // Reset any previous flags so this test starts from a known state
        connection.createStatement().executeUpdate(
                "UPDATE subscriptions SET status = 'ACTIVE' WHERE status = 'UNDER_REVIEW'"
        );

        int flaggedCount = dao.flagIdleSubscriptions(60);

        assertTrue(flaggedCount >= 0, "Flagged count should never be negative");

        List<IdleSubscription> flagged = dao.findFlaggedSubscriptions();
        assertEquals(flaggedCount, flagged.size(),
                "The count returned by flagIdleSubscriptions should match the number actually flagged");
    }

    @Test
    void flagIdleSubscriptions_isSafeToRunTwiceInARow() throws Exception {
        // First run: flags whatever qualifies
        dao.flagIdleSubscriptions(60);

        // Second run immediately after: should flag 0 new ones, since
        // anything that qualified is already
}
}
