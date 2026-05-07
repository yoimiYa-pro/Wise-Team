package com.teampm.algo;

import lombok.Data;

/**
 * 3 阶判断矩阵：行/列顺序为 [能力/技能匹配, 负载, 绩效]。
 */
public final class AhpCalculator {

    private static final double[] RI = {0, 0, 0.58, 0.9, 1.12, 1.24, 1.32, 1.41, 1.45, 1.49};//基准矩阵

    private AhpCalculator() {
    }

    @Data
    public static class AhpResult {
        private double[] weights;
        private double lambdaMax;
        private double ci;
        private double cr;
        private boolean consistent;
    }
    //归一化处理函数
    public static AhpResult compute(double[][] matrix) {
        int n = matrix.length;
        if (n == 0 || n != matrix[0].length) {
            throw new IllegalArgumentException("矩阵须为方阵");
        }
        double[][] norm = new double[n][n];
        for (int j = 0; j < n; j++) {
            double colSum = 0;
            for (int i = 0; i < n; i++) {
                colSum += matrix[i][j];//计算第j列的和
            }
            for (int i = 0; i < n; i++) {
                norm[i][j] = matrix[i][j] / colSum;//每一个元素都除以和
            }
        }
        double[] w = new double[n];//权重向量
        for (int i = 0; i < n; i++) {
            double rowSum = 0;
            for (int j = 0; j < n; j++) {
                rowSum += norm[i][j];//第i行的和
            }
            w[i] = rowSum / n;//每行取平均值
        }
        double lambdaMax = 0;//最大特征值
        for (int i = 0; i < n; i++) {
            double sum = 0;
            for (int j = 0; j < n; j++) {
                sum += matrix[i][j] * w[j];//矩阵乘以权重
            }
            lambdaMax += sum / w[i];//累加
        }
        lambdaMax /= n;
        double ci = (lambdaMax - n) / (n - 1);//计算一致性指标
        double ri = n < RI.length ? RI[n] : RI[RI.length - 1];
        double cr = ri > 0 ? ci / ri : 0;
        AhpResult r = new AhpResult();
        r.setWeights(w);
        r.setLambdaMax(lambdaMax);
        r.setCi(ci);
        r.setCr(cr);
        r.setConsistent(cr < 0.1);
        return r;
    }
}
