package com.patientmonitor.auth;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PasswordResetCodeRepository {

    private final JdbcTemplate jdbc;

    public PasswordResetCodeRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<ResetCode> find(long userId) {
        return jdbc.query("SELECT code_hash, created_at, expires_at FROM password_reset_code WHERE user_id = ?",
                (rs, rowNum) -> new ResetCode(
                        rs.getString("code_hash"),
                        rs.getTimestamp("created_at").toLocalDateTime(),
                        rs.getTimestamp("expires_at").toLocalDateTime()),
                userId).stream().findFirst();
    }

    public void replace(long userId, String codeHash, LocalDateTime createdAt, LocalDateTime expiresAt) {
        delete(userId);
        jdbc.update("INSERT INTO password_reset_code (user_id, code_hash, created_at, expires_at) VALUES (?, ?, ?, ?)",
                userId, codeHash, Timestamp.valueOf(createdAt), Timestamp.valueOf(expiresAt));
    }

    /** 시도 횟수를 한 번에 올려 동시 요청으로 제한을 넘지 못하게 한다. 이미 한도면 0 을 반환 */
    public int incrementAttempts(long userId, int maxAttempts) {
        return jdbc.update("UPDATE password_reset_code SET attempts = attempts + 1 WHERE user_id = ? AND attempts < ?",
                userId, maxAttempts);
    }

    public void delete(long userId) {
        jdbc.update("DELETE FROM password_reset_code WHERE user_id = ?", userId);
    }

    public record ResetCode(String codeHash, LocalDateTime createdAt, LocalDateTime expiresAt) {
    }
}
