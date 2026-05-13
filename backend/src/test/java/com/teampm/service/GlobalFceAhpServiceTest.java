package com.teampm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teampm.domain.GlobalFceAhp;
import com.teampm.mapper.GlobalFceAhpMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalFceAhpServiceTest {

    @Mock
    private GlobalFceAhpMapper globalFceAhpMapper;

    @Mock
    private SystemConfigService systemConfigService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private GlobalFceAhpService service;

    @BeforeEach
    void setUp() {
        service = new GlobalFceAhpService(globalFceAhpMapper, systemConfigService, objectMapper);
    }

    @Test
    void fceWeightsOrFallback_usesSystemConfigWhenNoRow() {
        when(globalFceAhpMapper.findSingleton()).thenReturn(null);
        when(systemConfigService.get("fce.weights.manager")).thenReturn("0.2");
        when(systemConfigService.get("fce.weights.system")).thenReturn("0.3");
        when(systemConfigService.get("fce.weights.peer")).thenReturn("0.5");

        double[] w = service.fceWeightsOrFallback();

        assertThat(w).containsExactly(0.2, 0.3, 0.5);
    }

    @Test
    void fceWeightsOrFallback_usesAhpWhenConsistent() throws Exception {
        GlobalFceAhp row = new GlobalFceAhp();
        row.setConsistentFlag(1);
        row.setWeightsJson(objectMapper.writeValueAsString(new double[]{0.5, 0.3, 0.2}));

        when(globalFceAhpMapper.findSingleton()).thenReturn(row);

        double[] w = service.fceWeightsOrFallback();

        assertThat(w).containsExactly(0.5, 0.3, 0.2);
    }

    @Test
    void fceWeightsOrFallback_ignoresInconsistentRow() {
        GlobalFceAhp row = new GlobalFceAhp();
        row.setConsistentFlag(0);
        row.setWeightsJson("[0.5,0.3,0.2]");

        when(globalFceAhpMapper.findSingleton()).thenReturn(row);
        when(systemConfigService.get("fce.weights.manager")).thenReturn(null);
        when(systemConfigService.get("fce.weights.system")).thenReturn(null);
        when(systemConfigService.get("fce.weights.peer")).thenReturn(null);

        double[] w = service.fceWeightsOrFallback();

        assertThat(w[0]).isEqualTo(0.4);
        assertThat(w[1]).isEqualTo(0.35);
        assertThat(w[2]).isEqualTo(0.25);
    }

    @Test
    void fceWeightsOrFallback_ignoresBadWeightsJson() {
        GlobalFceAhp row = new GlobalFceAhp();
        row.setConsistentFlag(1);
        row.setWeightsJson("not-json");

        when(globalFceAhpMapper.findSingleton()).thenReturn(row);
        when(systemConfigService.get("fce.weights.manager")).thenReturn("0.1");
        when(systemConfigService.get("fce.weights.system")).thenReturn("0.2");
        when(systemConfigService.get("fce.weights.peer")).thenReturn("0.7");

        double[] w = service.fceWeightsOrFallback();

        assertThat(w).containsExactly(0.1, 0.2, 0.7);
    }
}
