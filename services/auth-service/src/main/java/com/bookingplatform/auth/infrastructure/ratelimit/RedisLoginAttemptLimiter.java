package com.bookingplatform.auth.infrastructure.ratelimit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.bookingplatform.auth.config.LoginLimitProperties;
import com.bookingplatform.auth.domain.exception.TooManyAttemptsException;
import com.bookingplatform.auth.domain.model.OpaqueTokens;
import com.bookingplatform.auth.domain.port.LoginAttemptLimiter;

/**
 * Đếm số lần đăng nhập sai trong Redis, theo email và theo IP. Khóa tự hết hạn
 * sau "window".
 * Email được băm trước khi làm khóa để Redis không chứa địa chỉ email thật.
 */
@Component
public class RedisLoginAttemptLimiter implements LoginAttemptLimiter {
    private final StringRedisTemplate redis;
    private final LoginLimitProperties properties;

    public RedisLoginAttemptLimiter(StringRedisTemplate redis, LoginLimitProperties properties) {
        this.redis = redis;
        this.properties = properties;

    }

    @Override
    public void assertAllowed(String normailzedEmail, String ipAddress) {
        String emailKey = emailKey(normailzedEmail);
        String ipKey = ipKey(ipAddress);
        boolean blocked = false;
        long retryAfter = 1;
        if (count(emailKey) >= properties.maxPerEmail()) {
            blocked = true;
            retryAfter = Math.max(retryAfter, ttlSeconds(emailKey));
        }
        if (count(ipKey) >= properties.maxPerIp()) {
            blocked = true;
            retryAfter = Math.max(retryAfter, ttlSeconds(ipKey));
        }
        if (blocked) {
            throw new TooManyAttemptsException(retryAfter);
        }
    }

    @Override
    public void recordFailure(String normalizedEmail, String ipAddress) {
        increment(emailKey(normalizedEmail));
        increment(ipKey(ipAddress));
    }

    @Override
    public void recordSuccess(String normailzedEmail) {
        redis.delete(emailKey(normailzedEmail));
    }

    private long count(String key) {
        String value = redis.opsForValue().get(key);
        return value == null ? 0 : Long.parseLong(value);
    }

    private void increment(String key) {
        Long value = redis.opsForValue().increment(key);
        if (value != null && value == 1L) {
            redis.expire(key, properties.window());
        }
    }

    private long ttlSeconds(String key) {
        Long ttl = redis.getExpire(key);
        return (ttl == null || ttl < 0) ? properties.window().toSeconds() : ttl;
    }

    private static String emailKey(String normalizedEmail) {
        return "login:fail:email:" + OpaqueTokens.hash(normalizedEmail);
    }

    private static String ipKey(String ipAddress) {
        return "login:fail:ip:" + (ipAddress == null ? "unknown" : ipAddress);
    }
}
