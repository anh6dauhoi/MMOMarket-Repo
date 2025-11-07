package com.mmo.util;

public final class TierNameUtil {
    private TierNameUtil() {}

    public static String getTierName(Number levelNum) {
        if (levelNum == null) return "Starter";
        int level = levelNum.intValue();
        return switch (level) {
            case 0 -> "Starter";
            case 1 -> "Apprentice";
            case 2 -> "Trusted";
            case 3 -> "Professional";
            case 4 -> "Silver Partner";
            case 5 -> "Gold Partner";
            case 6 -> "Platinum Partner";
            case 7 -> "Diamond Partner";
            default -> (level < 0 ? "Starter" : "Diamond Partner");
        };
    }
}

