package com.sprawlauditor;

import com.sprawlauditor.config.DatabaseManager;
import com.sprawlauditor.dao.DataSeeder;
import com.sprawlauditor.dao.SprawlAuditDao;
import com.sprawlauditor.model.IdleSubscription;
import com.sprawlauditor.model.ToolOverlap;

import java.sql.Connection;
import java.util.List;

public class Main {

    private static final int IDLE_THRESHOLD_DAYS = 60;

    public static void main(String[] args) throws Exception {
        try (Connection conn = DatabaseManager.getConnection()) {

            System.out.println("== Seeding synthetic demo data ==");
            new DataSeeder(conn).seed();

            SprawlAuditDao dao = new SprawlAuditDao(conn);

            System.out.println("\n== Company-wide spend summary ==");
            double[] summary = dao.getSpendSummary();
            System.out.printf("  Total monthly SaaS spend:   $%.2f%n", summary[0]);
            System.out.printf("  Already flagged for review: $%.2f%n", summary[1]);

            System.out.println("\n== Idle subscriptions (no login in " + IDLE_THRESHOLD_DAYS + "+ days) ==");
            List<IdleSubscription> idle = dao.findIdleSubscriptions(IDLE_THRESHOLD_DAYS);
            if (idle.isEmpty()) {
                System.out.println("  (none found)");
            } else {
                idle.forEach(sub -> System.out.println("  " + sub));
                double totalWasted = idle.stream().mapToDouble(IdleSubscription::getEstimatedWastedSpend).sum();
                System.out.printf("  --> Estimated total wasted spend: $%.2f/mo%n", totalWasted);
            }

            System.out.println("\n== Category overlaps (same team, 2+ tools, same category) ==");
            List<ToolOverlap> overlaps = dao.findCategoryOverlaps();
            if (overlaps.isEmpty()) {
                System.out.println("  (none found)");
            } else {
                overlaps.forEach(o -> System.out.println("  " + o));
            }
        }
    }
}