package com.patientmonitor.auth;

import java.util.List;
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

    public Optional<String> findUsernameByNameAndEmail(String name, String email) {
        return jdbc.queryForList("SELECT username FROM users WHERE name = ? AND email = ?", String.class, name, email)
                .stream().findFirst();
    }

    public List<PendingUser> findPending() {
        return jdbc.query("SELECT id, username, name, email, created_at FROM users WHERE enabled = FALSE ORDER BY id",
                (rs, rowNum) -> new PendingUser(
                        rs.getLong("id"),
                        rs.getString("username"),
                        rs.getString("name"),
                        rs.getString("email"),
                        rs.getTimestamp("created_at").toLocalDateTime()));
    }

    public int approvePending(long id) {
        return jdbc.update("UPDATE users SET enabled = TRUE WHERE id = ? AND enabled = FALSE", id);
    }

    /** 승인 대기 계정만 지운다. 활성 계정은 이 경로로 삭제되지 않는다 */
    public int deletePending(long id) {
        return jdbc.update("DELETE FROM users WHERE id = ? AND enabled = FALSE", id);
    }

    public long count() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM users", Long.class);
    }

    public void insert(String username, String passwordHash, String name, String email, String role, boolean enabled) {
        jdbc.update("INSERT INTO users (username, password_hash, name, email, role, enabled) VALUES (?, ?, ?, ?, ?, ?)",
                username, passwordHash, name, email, role, enabled);
    }
}
