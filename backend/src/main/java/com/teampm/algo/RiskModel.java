package com.teampm.algo;

import com.teampm.config.AppProperties;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public final class RiskModel {

    private RiskModel() {
    }
//涉及的参数有risk:风险模型配置参数，progressPct：任务进度，start：开始时间，deadline：截止时间，difficulttyOnetoFive:难度系数（1-5），delayHistory01:历史延期率（0-1）
    public static BigDecimal delayProbability(AppProperties.Risk risk, double progressPct,
                                              LocalDate start, LocalDate deadline,
                                              double difficultyOneToFive,
                                              double delayHistory01) {
        double timeRatio;//已经过去的时间占总体时间的比率
        if (deadline != null && start != null) {
            long total = ChronoUnit.DAYS.between(start, deadline);
            if (total < 1) {
                total = 1;
            }//设置总天数最低为1
            long elapsed = ChronoUnit.DAYS.between(start, LocalDate.now());//已经过去天数
            if (elapsed < 0) {
                elapsed = 0;
            }//防止天数为负数
            timeRatio = Math.min(1.0, elapsed / (double) total);
        } else {
            timeRatio = 0.5;
        }//如果没有提供日期，就按照0.5来估计
        double progRatio = Math.max(0, Math.min(1, progressPct / 100.0));//进度百分比转化成小数
        double x1 = timeRatio - progRatio;//核心参照量，表示时间耗尽率与进度的差值，如果是正说明进度落后，如果是负说明进度超前
        double x2 = Math.max(0, Math.min(1, difficultyOneToFive / 5.0));//难度评分标准化
        double x3 = Math.max(0, Math.min(1, delayHistory01));//历史延期率标准化
        //逻辑回归模型，Beta0=-2表示在「特征取 0」这个参考点时，即进度与时间同步，难度/历史都视为 0（轻松、无不良记录），模型给出的延期概率大约 12%
        //Beta1=3.5，Beta2 = 0.4，Beta3 = 1.2,表示进度的是否落后影响大于历史延期情况大于难度高低，这三个参数的相对大小表示了，影响延期概率的权重
        //Beta1，Beta2，Beta3的数值是根据事先确定好的比例加上预先设定好的某一情况下的预期概率反推出来的，并非基于历史数据的训练
        double z = risk.getBeta0() + risk.getBeta1() * x1 + risk.getBeta2() * x2 + risk.getBeta3() * x3;
        double p = 1.0 / (1.0 + Math.exp(-z));//逻辑函数，将z转化为一个概率
        return BigDecimal.valueOf(p).setScale(6, RoundingMode.HALF_UP);//输出结果，转换成BigDecimal格式，保留六位，四舍五入
    }

    public static String level(AppProperties.Risk risk, BigDecimal p) {
        if (p == null) {
            return "GREEN";//防御性处理
        }
        double v = p.doubleValue();//转成浮点值便于比较
        if (v >= risk.getRedThreshold()) {
            return "RED";
        }
        if (v >= risk.getOrangeThreshold()) {
            return "ORANGE";
        }
        return "GREEN";
    }
}
