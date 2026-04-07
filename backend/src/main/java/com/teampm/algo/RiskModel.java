package com.teampm.algo;

import com.teampm.config.AppProperties;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public final class RiskModel {

    private RiskModel() {
    }

    public static BigDecimal delayProbability(AppProperties.Risk risk, double progressPct,
                                              LocalDate start, LocalDate deadline,
                                              double difficultyOneToFive,
                                              double delayHistory01) {
        double timeRatio;
        if (deadline != null && start != null) {
            long total = ChronoUnit.DAYS.between(start, deadline);
            if (total < 1) {
                total = 1;
            }
            long elapsed = ChronoUnit.DAYS.between(start, LocalDate.now());
            if (elapsed < 0) {
                elapsed = 0;
            }
            timeRatio = Math.min(1.0, elapsed / (double) total);
        } else {
            timeRatio = 0.5;
        }
        double progRatio = Math.max(0, Math.min(1, progressPct / 100.0));
        double x1 = timeRatio - progRatio;
        double x2 = Math.max(0, Math.min(1, difficultyOneToFive / 5.0));
        double x3 = Math.max(0, Math.min(1, delayHistory01));
        double z = risk.getBeta0() + risk.getBeta1() * x1 + risk.getBeta2() * x2 + risk.getBeta3() * x3;
        double p = 1.0 / (1.0 + Math.exp(-z));
        return BigDecimal.valueOf(p).setScale(6, RoundingMode.HALF_UP);
    }

    public static String level(AppProperties.Risk risk, BigDecimal p) {
        if (p == null) {
            return "GREEN";
        }
        double v = p.doubleValue();
        if (v >= risk.getRedThreshold()) {
            return "RED";
        }
        if (v >= risk.getOrangeThreshold()) {
            return "ORANGE";
        }
        return "GREEN";
    }
}
