package com.sprawlauditor.model;

public class ToolOverlap {

    private final int teamId;
    private final String category;
    private final String toolA;
    private final String toolB;
    private final double combinedMonthlyCost;

    public ToolOverlap(int teamId, String category, String toolA, String toolB, double combinedMonthlyCost) {
        this.teamId = teamId;
        this.category = category;
        this.toolA = toolA;
        this.toolB = toolB;
        this.combinedMonthlyCost = combinedMonthlyCost;
    }

    public int getTeamId() { return teamId; }
    public String getCategory() { return category; }
    public String getToolA() { return toolA; }
    public String getToolB() { return toolB; }
    public double getCombinedMonthlyCost() { return combinedMonthlyCost; }

    @Override
    public String toString() {
        return String.format(
                "[Team %-2d] %-20s %s <-> %s combined=$%.2f/mo",
                teamId, category, toolA, toolB, combinedMonthlyCost
        );
    }
}