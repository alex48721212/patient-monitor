package com.patientmonitor.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.patientmonitor.auth.PasswordResetCodeRepository.ResetCode;
import com.patientmonitor.common.BadRequestException;

@Service
public class PasswordResetService {

    private static final Duration CODE_LIFETIME = Duration.ofMinutes(10);
    private static final Duration RESEND_COOLDOWN = Duration.ofMinutes(1);
    private static final int MAX_ATTEMPTS = 5;
    // 틀린 이유(아이디, 코드, 만료)를 구분해 알려주지 않는다
    private static final String INVALID_CODE = "인증 코드가 올바르지 않거나 만료되었습니다.";

    private final SecureRandom random = new SecureRandom();
    private final UserRepository users;
    private final PasswordResetCodeRepository codes;
    private final PasswordEncoder passwordEncoder;
    private final AccountNotifier notifier;

    public PasswordResetService(UserRepository users, PasswordResetCodeRepository codes,
                                PasswordEncoder passwordEncoder, AccountNotifier notifier) {
        this.users = users;
        this.codes = codes;
        this.passwordEncoder = passwordEncoder;
        this.notifier = notifier;
    }

    /** 계정 존재 여부가 드러나지 않도록 호출자에게는 결과를 알려주지 않는다 */
    @Transactional
    public void requestCode(PasswordResetRequest req) {
        String email = AccountService.normalizeEmail(req.email());
        users.findEnabledId(req.username(), email).ifPresent(userId -> {
            LocalDateTime now = LocalDateTime.now();
            // 재발급으로 시도 횟수를 초기화해 코드를 계속 추측하는 것을 막는다
            boolean recentlySent = codes.find(userId)
                    .filter(code -> code.createdAt().plus(RESEND_COOLDOWN).isAfter(now))
                    .isPresent();
            if (recentlySent) {
                return;
            }
            String code = "%06d".formatted(random.nextInt(1_000_000));
            codes.replace(userId, sha256(code), now, now.plus(CODE_LIFETIME));
            notifier.sendPasswordResetCode(email, code);
        });
    }

    /** 틀린 시도 횟수가 남아야 하므로 BadRequestException 은 롤백하지 않는다 */
    @Transactional(noRollbackFor = BadRequestException.class)
    public void confirm(PasswordResetConfirmRequest req) {
        AccountService.validatePassword(req.newPassword());

        long userId = users.findEnabledId(req.username()).orElseThrow(() -> new BadRequestException(INVALID_CODE));
        Optional<ResetCode> stored = codes.find(userId)
                .filter(code -> code.expiresAt().isAfter(LocalDateTime.now()));
        if (stored.isEmpty() || codes.incrementAttempts(userId, MAX_ATTEMPTS) == 0) {
            throw new BadRequestException(INVALID_CODE);
        }
        if (!MessageDigest.isEqual(bytes(stored.get().codeHash()), bytes(sha256(req.code())))) {
            throw new BadRequestException(INVALID_CODE);
        }

        users.updatePassword(userId, passwordEncoder.encode(req.newPassword()));
        codes.delete(userId);
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes(value)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
