package com.teampm.algo;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 任务需求技能与成员技能的余弦相似度，键为小写 skill 名，值为 0~1。
 */
public final class SkillMatch {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SkillMatch() {
    }

    public static double cosineSimilarity(String requiredJson, String userSkillsJson) {
        Map<String, Double> req = parse(requiredJson);
        Map<String, Double> usr = parse(userSkillsJson);
        if (req.isEmpty()) {
            return 0.85;
        }
        Set<String> keys = new HashSet<>(req.keySet());
        keys.addAll(usr.keySet());
        double dot = 0, n1 = 0, n2 = 0;
        for (String k : keys) {
            double a = req.getOrDefault(k, 0.0);
            double b = usr.getOrDefault(k, 0.0);
            dot += a * b;
            n1 += a * a;
            n2 += b * b;
        }
        if (n1 <= 0 || n2 <= 0) {
            return 0;
        }
        return Math.min(1.0, dot / (Math.sqrt(n1) * Math.sqrt(n2)));
    }

    private static Map<String, Double> parse(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> raw = MAPPER.readValue(json, new TypeReference<>() {
            });
            Map<String, Double> out = new HashMap<>();
            raw.forEach((k, v) -> {
                double d = v instanceof Number n ? n.doubleValue() : Double.parseDouble(String.valueOf(v));
                out.put(k.toLowerCase(), Math.max(0, Math.min(1, d)));
            });
            return out;
        } catch (Exception e) {
            return Map.of();
        }
    }
}
