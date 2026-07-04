package com.bsn.backend.social.model;

/** The ladder — multiplier applies to every point earned (§5.3). */
public enum Tier {

    IRON(0, 1.0),
    BRONZE(100, 1.1),
    SILVER(250, 1.2),
    GOLD(450, 1.3),
    PLATINUM(700, 1.4),
    DIAMOND(1000, 1.5),
    IMMORTAL(1400, 1.6);

    private final int minRr;
    private final double multiplier;

    Tier(int minRr, double multiplier) {
        this.minRr = minRr;
        this.multiplier = multiplier;
    }

    public int minRr() {
        return minRr;
    }

    public double multiplier() {
        return multiplier;
    }

    public static Tier forRr(int rr) {
        Tier result = IRON;
        for (Tier t : values()) {
            if (rr >= t.minRr) {
                result = t;
            }
        }
        return result;
    }

    /** RR displayed within the tier (Valorant-style "137 RR"). */
    public static int displayRr(int rr) {
        return rr - forRr(rr).minRr;
    }
}
