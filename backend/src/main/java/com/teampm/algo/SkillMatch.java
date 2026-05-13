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
        //将Json转化为技能-熟练度的Map（字典）
        Map<String, Double> req = parse(requiredJson);
        Map<String, Double> usr = parse(userSkillsJson);
        if (req.isEmpty()) {
            return 0.85;
        }//如果没有需求，则默认为0.85
        Set<String> keys = new HashSet<>(req.keySet());
        keys.addAll(usr.keySet());//把所有技能项目合并成一个总列表
        double dot = 0, n1 = 0, n2 = 0;//初始化三个变量，dot点积：两个向量对应元素乘积之和，n1:需求向量长度的平方，n2:用户向量长度的平方
        for (String k : keys) {
            double a = req.getOrDefault(k, 0.0);//获取任务需求中，技能k的熟练度
            double b = usr.getOrDefault(k, 0.0);//获取用户技能中，技能k的熟练度
            dot += a * b;
            n1 += a * a;
            n2 += b * b;
        }
        if (n1 <= 0 || n2 <= 0) {
            return 0;
        }//如果需求向量或用户向量是“零向量”，所有技能熟练度都是0，说明一方或双方没有任何有效技能信息，此时相似度定义为0。
        return Math.min(1.0, dot / (Math.sqrt(n1) * Math.sqrt(n2)));//返回余弦相似度，确保在0-1之间
    }
    //解析函数，将JSON字符串安全地转换为Map
    private static Map<String, Double> parse(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();//如果输入是 null 或空白字符串，则返回一个不可变的空Map
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
