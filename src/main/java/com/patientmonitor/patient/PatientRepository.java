package com.patientmonitor.patient;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class PatientRepository {

    private final JdbcTemplate jdbc;

    public PatientRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Patient> findAll() {
        return jdbc.query("SELECT * FROM patient ORDER BY id", PatientRepository::mapRow);
    }

    public List<Patient> findByStatus(PatientStatus status) {
        return jdbc.query("SELECT * FROM patient WHERE status = ? ORDER BY id",
                PatientRepository::mapRow, status.name());
    }

    public Optional<Patient> findById(long id) {
        return jdbc.query("SELECT * FROM patient WHERE id = ?", PatientRepository::mapRow, id)
                .stream().findFirst();
    }

    public long insert(PatientRequest req) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO patient (name, birth_date, gender, room_number, diagnosis) VALUES (?, ?, ?, ?, ?)",
                    new String[]{"id"});
            ps.setString(1, req.name());
            ps.setDate(2, Date.valueOf(req.birthDate()));
            ps.setString(3, req.gender());
            ps.setString(4, req.roomNumber());
            ps.setString(5, req.diagnosis());
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public int update(long id, PatientRequest req) {
        return jdbc.update(
                "UPDATE patient SET name = ?, birth_date = ?, gender = ?, room_number = ?, diagnosis = ? WHERE id = ?",
                req.name(), Date.valueOf(req.birthDate()), req.gender(), req.roomNumber(), req.diagnosis(), id);
    }

    public int updateStatus(long id, PatientStatus status) {
        return jdbc.update("UPDATE patient SET status = ? WHERE id = ?", status.name(), id);
    }

    public int delete(long id) {
        return jdbc.update("DELETE FROM patient WHERE id = ?", id);
    }

    private static Patient mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new Patient(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getDate("birth_date").toLocalDate(),
                rs.getString("gender"),
                rs.getString("room_number"),
                rs.getString("diagnosis"),
                PatientStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("admitted_at").toLocalDateTime());
    }
}
