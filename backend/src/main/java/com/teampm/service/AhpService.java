package com.teampm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teampm.algo.AhpCalculator;
import com.teampm.domain.TeamAhp;
import com.teampm.exception.ApiException;
import com.teampm.mapper.TeamAhpMapper;
import com.teampm.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AhpService {

    private final TeamAhpMapper teamAhpMapper;
    private final TeamService teamService;
    private final ObjectMapper objectMapper;

    public TeamAhp getForTeam(Long teamId, UserPrincipal actor) {
        var team = teamService.requireTeam(teamId);
        teamService.assertManager(actor, team);
        return teamAhpMapper.findByTeamId(teamId);
    }

    @Transactional
    public TeamAhp saveMatrix(Long teamId, double[][] matrix, UserPrincipal actor) {
        var team = teamService.requireTeam(teamId);
        teamService.assertManager(actor, team);
        if (matrix == null || matrix.length != 3 || matrix[0].length != 3) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "须为 3x3 判断矩阵");
        }
        AhpCalculator.AhpResult r = AhpCalculator.compute(matrix);
        if (!r.isConsistent()) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    String.format("一致性未通过 CR=%.4f，请调整矩阵", r.getCr()));
        }
        TeamAhp row = new TeamAhp();
        row.setTeamId(teamId);
        try {
            row.setMatrixJson(objectMapper.writeValueAsString(matrix));
            row.setWeightsJson(objectMapper.writeValueAsString(r.getWeights()));
        } catch (JsonProcessingException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "序列化失败");
        }
        row.setCrValue(BigDecimal.valueOf(r.getCr()));
        row.setConsistentFlag(1);
        teamAhpMapper.upsert(row);
        return teamAhpMapper.findByTeamId(teamId);
    }

    public double[] weightsOrDefault(Long teamId) {
        TeamAhp a = teamAhpMapper.findByTeamId(teamId);
        if (a == null || a.getWeightsJson() == null || a.getWeightsJson().isBlank()) {
            return new double[]{1.0 / 3, 1.0 / 3, 1.0 / 3};
        }
        try {
            double[] w = objectMapper.readValue(a.getWeightsJson(), double[].class);
            if (w.length != 3) {
                return new double[]{1.0 / 3, 1.0 / 3, 1.0 / 3};
            }
            return w;
        } catch (Exception e) {
            return new double[]{1.0 / 3, 1.0 / 3, 1.0 / 3};
        }
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
}
