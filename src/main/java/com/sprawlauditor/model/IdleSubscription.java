package com.sprawlauditor.model;

public class IdleSubscription {

    private final int subscriptionId;
    private final int teamId;
    private final String toolName;
    private final double monthlyCost;
    private final int seatsPurchased;
    private final int idleSeatCount;
    private final double estimatedWastedSpend;

    public IdleSubscription(int subscriptionId, int teamId, String toolName,
                            double monthlyCost, int seatsPurchased,
                            int idleSeatCount, double estimatedWastedSpend) {
        this.subscriptionId = subscriptionId;
        this.teamId = teamId;
        this.toolName = toolName;
        this.monthlyCost = monthlyCost;
        this.seatsPurchased = seatsPurchased;
        this.idleSeatCount = idleSeatCount;
        this.estimatedWastedSpend = estimatedWastedSpend;
    }

    public int getSubscriptionId() { return subscriptionId; }
    public int getTeamId() { return teamId; }
    public String getToolName() { return toolName; }
    public double getMonthlyCost() { return monthlyCost; }
    public int getSeatsPurchased() { return seatsPurchased; }
    public int getIdleSeatCount() { return idleSeatCount; }
    public double getEstimatedWastedSpend() { return estimatedWastedSpend; }

    @Override
    public String toString() {
        return String.format(
                "[Sub #%-3d] %-16s team=%-2d cost=₹%-8.2f seats=%d idle=%d wasted=₹%.2f/mo",
                subscriptionId, toolName, teamId, monthlyCost, seatsPurchased, idleSeatCount, estimatedWastedSpend
        );
    }
}