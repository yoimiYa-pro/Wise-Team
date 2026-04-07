package com.teampm.service;

import com.teampm.mapper.SystemConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SystemConfigService {

    private final SystemConfigMapper systemConfigMapper;

    public String get(String key) {
        return systemConfigMapper.findValue(key);
    }

    @Transactional
    public void put(String key, String value) {
        systemConfigMapper.upsert(key, value);
    }

    public Map<String, String> defaults() {
        return Map.of(
                "load.smoothing.alpha", "0.4",
                "fce.weights.manager", "0.4",
                "fce.weights.system", "0.35",
                "fce.weights.peer", "0.25"
        );
    }

    public Map<String, String> allEffective() {
        Map<String, String> m = new HashMap<>(defaults());
        for (String k : defaults().keySet()) {
            String v = systemConfigMapper.findValue(k);
            if (v != null) {
                m.put(k, v);
            }
        }
        return m;
    }
}
