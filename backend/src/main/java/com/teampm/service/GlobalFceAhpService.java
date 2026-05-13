package com.teampm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teampm.algo.AhpCalculator;
import com.teampm.domain.GlobalFceAhp;
import com.teampm.exception.ApiException;
import com.teampm.mapper.GlobalFceAhpMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 全平台 FCE 三维度权重（管理者评价 / 系统指标 / 同事互评），与
 * {@link com.teampm.algo.FuzzyComprehensiveEvaluation} 中行顺序一致：下标 0,1,2。
 */
@Service
@RequiredArgsConstructor
public class GlobalFceAhpService {

    private final GlobalFceAhpMapper globalFceAhpMapper;
    private final SystemConfigService systemConfigService;
    private final ObjectMapper objectMapper;

    public GlobalFceAhp getSingleton() {
        return globalFceAhpMapper.findSingleton();
    }

    public Map<String, Object> preview(double[][] matrix) {
        AhpCalculator.AhpResult r = AhpCalculator.compute(matrix);
        Map<String, Object> m = new HashMap<>();
        m.put("weights", r.getWeights());
        m.put("lambdaMax", r.getLambdaMax());
        m.put("ci", r.getCi());
        m.put("cr", r.getCr());
        m.put("consistent", r.isConsistent());
        return m;
    }

    @Transactional
    public GlobalFceAhp saveMatrix(double[][] matrix) {
        if (matrix == null || matrix.length != 3 || matrix[0].length != 3) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "须为 3x3 判断矩阵");
        }
        AhpCalculator.AhpResult r = AhpCalculator.compute(matrix);
        if (!r.isConsistent()) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    String.format("一致性未通过 CR=%.4f，请调整矩阵", r.getCr()));
        }
        GlobalFceAhp row = new GlobalFceAhp();
        row.setId(GlobalFceAhp.SINGLETON_ID);
        try {
            row.setMatrixJson(objectMapper.writeValueAsString(matrix));
            row.setWeightsJson(objectMapper.writeValueAsString(r.getWeights()));
        } catch (JsonProcessingException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "序列化失败");
        }
        row.setCrValue(BigDecimal.valueOf(r.getCr()));
        row.setConsistentFlag(1);
        globalFceAhpMapper.upsertSingleton(row);
        return globalFceAhpMapper.findSingleton();
    }

    /**
     * 关账用：优先使用已通过一致性检验的全局 AHP 权向量 [管理者, 系统, 互评]；
     * 否则使用 system_config 中 fce.weights.* 与代码默认。
     */
    public double[] fceWeightsOrFallback() {
        GlobalFceAhp row = globalFceAhpMapper.findSingleton();
        if (row != null && row.getConsistentFlag() != null && row.getConsistentFlag() == 1
                && row.getWeightsJson() != null && !row.getWeightsJson().isBlank()) {
            try {
                double[] w = objectMapper.readValue(row.getWeightsJson(), double[].class);
                if (w != null && w.length == 3 && allPositiveFinite(w)) {
                    return w.clone();
                }
            } catch (Exception ignored) {
                // fall through
            }
        }
        double wm = parseConfigWeight("fce.weights.manager", 0.4);
        double ws = parseConfigWeight("fce.weights.system", 0.35);
        double wp = parseConfigWeight("fce.weights.peer", 0.25);
        return new double[]{wm, ws, wp};
    }

    private static boolean allPositiveFinite(double[] w) {
        for (double v : w) {
            if (!Double.isFinite(v) || v <= 0) {
                return false;
            }
        }
        return true;
    }

    private double parseConfigWeight(String key, double def) {
        String v = systemConfigService.get(key);
        if (v == null) {
            return def;
        }
        try {
            return Double.parseDouble(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
