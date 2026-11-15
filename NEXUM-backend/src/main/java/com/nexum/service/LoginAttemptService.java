package com.nexum.service;

import com.nexum.exception.DemasiadosIntentosException;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Service
public class LoginAttemptService {
    private static final long WINDOW = Duration.ofMinutes(15).toMillis();
    private final Clock clock;
    private final Map<String, Attempt> attempts = new HashMap<>();
    private record Attempt(int failures, long expires) {}
    public LoginAttemptService() { this(Clock.systemUTC()); }
    LoginAttemptService(Clock clock) { this.clock = clock; }
    public synchronized void verificar(String email, String ip) {
        long now = clock.millis();
        attempts.entrySet().removeIf(e -> e.getValue().expires() <= now);
        for (String key : keys(email, ip)) {
            Attempt a = attempts.get(key);
            if (a != null && a.failures() >= limit(key)) throw new DemasiadosIntentosException();
        }
    }
    public synchronized void fallo(String email, String ip) {
        long now = clock.millis();
        for (String key : keys(email, ip)) {
            Attempt old = attempts.get(key);
            if (old == null || old.expires() <= now) old = new Attempt(0, now + WINDOW);
            int count = old.failures() + 1;
            attempts.put(key, new Attempt(count, count == limit(key) ? now + WINDOW : old.expires()));
        }
    }
    public synchronized void exito(String email) { attempts.remove("email:" + email); }
    private static int limit(String key) { return key.startsWith("ip:") ? 30 : 5; }
    private static String[] keys(String email, String ip) { return new String[]{"email:" + email, "ip:" + ip}; }
}
