package com.teampm.algo;

import java.util.List;

public final class ExponentialSmoothing {

    private ExponentialSmoothing() {
    }

    /**
     * 历史序列从旧到新；返回下一步预测 F_{t+1}。
     */
    public static double nextForecast(List<Double> historyOldestFirst, double alpha, double initialF) {
        if (historyOldestFirst == null || historyOldestFirst.isEmpty()) {
            return initialF;
        }
        double f = initialF;
        for (double y : historyOldestFirst) {
            f = alpha * y + (1 - alpha) * f;
        }
        return f;
    }
}
