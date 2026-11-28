package com.stackultra.essentials.content.magnet;

public enum MagnetTier {
    TIER_1("rotational_magnet", 5, 10, 2.0D),
    TIER_2("precision_magnet", 5, 10, 10.0D);

    public static final int TICKS_PER_MINUTE = 1200;
    public static final int MAX_CAPACITY_LEVEL = 3;

    private final String id;
    private final int baseMinutes;
    private final int maxMinutes;
    private final double pullRadius;

    MagnetTier(String id, int baseMinutes, int maxMinutes, double pullRadius) {
        this.id = id;
        this.baseMinutes = baseMinutes;
        this.maxMinutes = maxMinutes;
        this.pullRadius = pullRadius;
    }

    public String id() {
        return id;
    }

    public int maxCharge(int capacityLevel) {
        int level = Math.clamp(capacityLevel, 0, MAX_CAPACITY_LEVEL);
        int minutes = baseMinutes + (maxMinutes - baseMinutes) * level / MAX_CAPACITY_LEVEL;
        return minutes * TICKS_PER_MINUTE;
    }

    public double pullRadius() {
        return pullRadius;
    }

    public boolean hasFilterSlots() {
        return this == TIER_2;
    }

    public static String formatRemaining(int charge) {
        int totalSeconds = Math.max(0, charge) / 20;
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }
}
