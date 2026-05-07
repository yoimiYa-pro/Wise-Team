package com.teampm.algo;

import java.util.Arrays;

/**
 * 评语 4 档：优秀、良好、合格、不合格；加权平均型合成。
 */
public final class FuzzyComprehensiveEvaluation {

    private static final double[] GRADE_SCORES = {95, 82, 68, 50};//定义评分标准，优秀95，良好82，合格68，不合格50

    private FuzzyComprehensiveEvaluation() {
    }//私有构造方法，工具类常用

    /**
     * @param r 3x4 隶属度矩阵，行：管理者/系统/互评；列：四档评语
     * @param w 行权重，长度 3，和为 1，对应三个评价者的权重
     */
    public static double score(double[][] r, double[] w) {
        if (r.length != 3 || r[0].length != 4 || w.length != 3) {
            throw new IllegalArgumentException("R 须为 3x4，w 须为 3 维");
        }//检查输入是否合法，否则抛出异常
        double[] b = new double[4];//综合隶属度向量
        for (int j = 0; j < 4; j++) {
            double s = 0;
            for (int i = 0; i < 3; i++) {
                s += w[i] * r[i][j];
            }
            b[j] = s;
        }//计算出评价为优秀良好等四个档次的的最终得分
        double sumB = Arrays.stream(b).sum();//计算b所有元素的总和
        if (sumB > 0) {
            for (int j = 0; j < 4; j++) {
                b[j] /= sumB;//进行归一化处理
            }
        }
        double out = 0;
        for (int j = 0; j < 4; j++) {
            out += b[j] * GRADE_SCORES[j];
        }//求得最终分数
        return Math.max(0, Math.min(100, out));
    }
}
