package com.teampm.algo;

import java.util.Arrays;

/**
 * 评语 4 档：优秀、良好、合格、不合格；加权平均型合成。
 */
public final class FuzzyComprehensiveEvaluation {

    private static final double[] GRADE_SCORES = {95, 82, 68, 50};

    private FuzzyComprehensiveEvaluation() {
    }

    /**
     * @param r 3x4 隶属度矩阵，行：管理者/系统/互评；列：四档评语
     * @param w 行权重，长度 3，和为 1
     */
    public static double score(double[][] r, double[] w) {
        if (r.length != 3 || r[0].length != 4 || w.length != 3) {
            throw new IllegalArgumentException("R 须为 3x4，w 须为 3 维");
        }
        double[] b = new double[4];
        for (int j = 0; j < 4; j++) {
            double s = 0;
            for (int i = 0; i < 3; i++) {
                s += w[i] * r[i][j];
            }
            b[j] = s;
        }
        double sumB = Arrays.stream(b).sum();
        if (sumB > 0) {
            for (int j = 0; j < 4; j++) {
                b[j] /= sumB;
            }
        }
        double out = 0;
        for (int j = 0; j < 4; j++) {
            out += b[j] * GRADE_SCORES[j];
        }
        return Math.max(0, Math.min(100, out));
    }
}
