package com.patientmonitor.auth;

import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {

    private final JdbcTemplate jdbc;

    public UserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<AppUser> findByUsername(String username) {
        return jdbc.query("SELECT username, password_hash, role, enabled FROM users WHERE username = ?",
                (rs, rowNum) -> new AppUser(
                        rs.getString("username"),
                        rs.getString("password_hash"),
                        rs.getString("role"),
                        rs.getBoolean("enabled")),
                username).stream().findFirst();
    }

    public long count() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM users", Long.class);
    }

    public void insert(String username, String passwordHash, String name, String email, String role, boolean enabled) {
        jdbc.update("INSERT INTO users (username, password_hash, name, email, role, enabled) VALUES (?, ?, ?, ?, ?, ?)",
                username, passwordHash, name, email, role, enabled);
    }
}
