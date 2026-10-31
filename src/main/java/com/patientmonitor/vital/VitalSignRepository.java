package com.patientmonitor.vital;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class VitalSignRepository {

    private final JdbcTemplate jdbc;

    public VitalSignRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public VitalSign insert(long patientId, VitalRequest req) {
        LocalDateTime now = LocalDateTime.now();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO vital_sign
                        (patient_id, heart_rate, systolic_bp, diastolic_bp, spo2, temperature, respiratory_rate, measured_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            ps.setLong(1, patientId);
            ps.setInt(2, req.heartRate());
            ps.setInt(3, req.systolicBp());
            ps.setInt(4, req.diastolicBp());
            ps.setInt(5, req.spo2());
            ps.setBigDecimal(6, BigDecimal.valueOf(req.temperature()));
            ps.setInt(7, req.respiratoryRate());
            ps.setTimestamp(8, Timestamp.valueOf(now));
            return ps;
        }, keyHolder);
        return new VitalSign(keyHolder.getKey().longValue(), patientId, req.heartRate(), req.systolicBp(),
                req.diastolicBp(), req.spo2(), req.temperature(), req.respiratoryRate(), now);
    }

    public List<VitalSign> findRecent(long patientId, int limit) {
        return jdbc.query("""
                SELECT * FROM vital_sign
                WHERE patient_id = ?
                ORDER BY measured_at DESC, id DESC
                LIMIT ?
                """, VitalSignRepository::mapRow, patientId, limit);
    }

    public List<VitalSign> findBetween(long patientId, LocalDateTime from, LocalDateTime to) {
        return jdbc.query("""
                SELECT * FROM vital_sign
                WHERE patient_id = ? AND measured_at BETWEEN ? AND ?
                ORDER BY measured_at
                """, VitalSignRepository::mapRow, patientId, Timestamp.valueOf(from), Timestamp.valueOf(to));
    }

    public Optional<VitalSign> findLatest(long patientId) {
        return findRecent(patientId, 1).stream().findFirst();
    }

    /** 최근 N분 동안의 항목별 평균/최소/최대 (SQL 집계 함수 활용) */
    public VitalSummary summarize(long patientId, int minutes) {
        return jdbc.queryForObject("""
                SELECT COUNT(*)              AS cnt,
                       AVG(heart_rate)       AS avg_hr,  MIN(heart_rate) AS min_hr,  MAX(heart_rate) AS max_hr,
                       AVG(systolic_bp)      AS avg_sys, AVG(diastolic_bp) AS avg_dia,
                       AVG(spo2)             AS avg_spo2, MIN(spo2) AS min_spo2,
                       AVG(temperature)      AS avg_temp, MAX(temperature) AS max_temp,
                       AVG(respiratory_rate) AS avg_rr
                FROM vital_sign
                WHERE patient_id = ? AND measured_at >= ?
                """, (rs, rowNum) -> new VitalSummary(
                        patientId, minutes, rs.getInt("cnt"),
                        rs.getDouble("avg_hr"), rs.getInt("min_hr"), rs.getInt("max_hr"),
                        rs.getDouble("avg_sys"), rs.getDouble("avg_dia"),
                        rs.getDouble("avg_spo2"), rs.getInt("min_spo2"),
                        rs.getDouble("avg_temp"), rs.getDouble("max_temp"),
                        rs.getDouble("avg_rr")),
                patientId, Timestamp.valueOf(LocalDateTime.now().minusMinutes(minutes)));
    }

    private static VitalSign mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new VitalSign(
                rs.getLong("id"),
                rs.getLong("patient_id"),
                rs.getInt("heart_rate"),
                rs.getInt("systolic_bp"),
                rs.getInt("diastolic_bp"),
                rs.getInt("spo2"),
                rs.getBigDecimal("temperature").doubleValue(),
                rs.getInt("respiratory_rate"),
                rs.getTimestamp("measured_at").toLocalDateTime());
    }
}
