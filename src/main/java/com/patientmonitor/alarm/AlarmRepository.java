package com.patientmonitor.alarm;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AlarmRepository {

    /** 알람 목록에는 환자 이름/병실을 같이 보여주기 위해 patient 와 JOIN 한다 */
    private static final String SELECT_WITH_PATIENT = """
            SELECT a.*, p.name AS patient_name, p.room_number
            FROM alarm a
            JOIN patient p ON p.id = a.patient_id
            """;

    private final JdbcTemplate jdbc;

    public AlarmRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insertAll(long patientId, long vitalSignId, List<AlarmEvaluator.AlarmCandidate> candidates) {
        jdbc.batchUpdate("""
                        INSERT INTO alarm (patient_id, vital_sign_id, vital_type, measured_value, severity, message)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """, candidates, candidates.size(),
                (ps, c) -> {
                    ps.setLong(1, patientId);
                    ps.setLong(2, vitalSignId);
                    ps.setString(3, c.type().name());
                    ps.setBigDecimal(4, BigDecimal.valueOf(c.value()));
                    ps.setString(5, c.severity().name());
                    ps.setString(6, c.message());
                });
    }

    /** 조건이 주어진 것만 WHERE 에 붙이는 동적 쿼리 */
    public List<Alarm> search(Long patientId, Boolean acknowledged, Severity severity, int limit) {
        StringBuilder sql = new StringBuilder(SELECT_WITH_PATIENT).append("WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (patientId != null) {
            sql.append(" AND a.patient_id = ?");
            params.add(patientId);
        }
        if (acknowledged != null) {
            sql.append(" AND a.acknowledged = ?");
            params.add(acknowledged);
        }
        if (severity != null) {
            sql.append(" AND a.severity = ?");
            params.add(severity.name());
        }
        sql.append(" ORDER BY a.created_at DESC, a.id DESC LIMIT ?");
        params.add(limit);
        return jdbc.query(sql.toString(), AlarmRepository::mapRow, params.toArray());
    }

    public List<Alarm> findByVitalSignId(long vitalSignId) {
        return jdbc.query(SELECT_WITH_PATIENT + "WHERE a.vital_sign_id = ? ORDER BY a.id",
                AlarmRepository::mapRow, vitalSignId);
    }

    public int acknowledge(long id) {
        return jdbc.update("UPDATE alarm SET acknowledged = TRUE WHERE id = ?", id);
    }

    public int acknowledgeAllForPatient(long patientId) {
        return jdbc.update("UPDATE alarm SET acknowledged = TRUE WHERE patient_id = ? AND acknowledged = FALSE", patientId);
    }

    /** 미확인 알람이 있는 환자별 건수 (GROUP BY + 조건부 집계) */
    public List<AlarmStats> unacknowledgedStats() {
        return jdbc.query("""
                SELECT p.id, p.name, p.room_number,
                       COUNT(*) AS unacknowledged,
                       SUM(CASE WHEN a.severity = 'CRITICAL' THEN 1 ELSE 0 END) AS critical
                FROM alarm a
                JOIN patient p ON p.id = a.patient_id
                WHERE a.acknowledged = FALSE
                GROUP BY p.id, p.name, p.room_number
                ORDER BY critical DESC, unacknowledged DESC
                """, (rs, rowNum) -> new AlarmStats(
                        rs.getLong("id"), rs.getString("name"), rs.getString("room_number"),
                        rs.getInt("unacknowledged"), rs.getInt("critical")));
    }

    private static Alarm mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new Alarm(
                rs.getLong("id"),
                rs.getLong("patient_id"),
                rs.getString("patient_name"),
                rs.getString("room_number"),
                rs.getLong("vital_sign_id"),
                VitalType.valueOf(rs.getString("vital_type")),
                rs.getBigDecimal("measured_value").doubleValue(),
                Severity.valueOf(rs.getString("severity")),
                rs.getString("message"),
                rs.getBoolean("acknowledged"),
                rs.getTimestamp("created_at").toLocalDateTime());
    }
}
