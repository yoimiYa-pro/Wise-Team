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
