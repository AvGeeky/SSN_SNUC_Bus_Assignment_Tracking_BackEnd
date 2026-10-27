
package com.bustracking.bustrack.Services;

import com.github.f4b6a3.uuid.UuidCreator;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;

@Service
public class NotificationService {

    private static final String PREFIX = "bt:notif:";

    private static final String BROADCAST_IDX = PREFIX + "idx:broadcast";
    private static final String EVERY_IDX     = PREFIX + "idx:every";   // for da admin view
    private static final String EMAIL_IDX     = PREFIX + "idx:email:";

    private static final double NEVER = 32_503_680_000_000d; // year 3000 = no expiry
    private static final List<Object> FIELDS = List.of("html", "exp", "bc");

    private final StringRedisTemplate redis;

    public NotificationService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public record Notification(UUID id, String htmlNotification,
                               long toDateEpoch, boolean broadcast) {}


    /** emailsAllowed == null -> broadcast. expiresAt <= 0 -> never expires. */
    public UUID addNotification(String html, Collection<String> emailsAllowed, long expiresAt) {
        UUID id = UuidCreator.getTimeOrderedEpoch();
        save(id, html, emailsAllowed, expiresAt, Set.of());
        return id;
    }

    public UUID addNotification(String html) {
        return addNotification(html, null, 0);
    }

    public boolean editNotification(UUID id, String html,
                                    Collection<String> emailsAllowed, long expiresAt) {
        if (id == null || !redis.hasKey(key(id))) return false;
        save(id, html, emailsAllowed, expiresAt, currentEmails(id));
        return true;
    }

    public boolean removeNotification(UUID id) {
        if (id == null) return false;
        boolean existed = redis.hasKey(key(id));
        Set<String> emails = currentEmails(id);
        String s = id.toString();
        tx(ops -> {
            ops.delete(key(id));
            ops.delete(allowKey(id));
            ops.opsForZSet().remove(BROADCAST_IDX, s);
            ops.opsForZSet().remove(EVERY_IDX, s);
            emails.forEach(e -> ops.opsForZSet().remove(EMAIL_IDX + e, s));
        });
        return existed;
    }

    @SuppressWarnings("unchecked")
    public List<Notification> getNotifications(String email) {
        String idx = EMAIL_IDX + normalize(email);
        double now = System.currentTimeMillis();

        // One round trip for both index lookups.
        List<Object> r = pipeline(ops -> {
            ops.opsForZSet().rangeByScore(BROADCAST_IDX, now, NEVER);
            ops.opsForZSet().rangeByScore(idx, now, NEVER);
        });

        Set<String> ids = new HashSet<>();
        for (Object o : r) if (o != null) ids.addAll((Set<String>) o);
        return load(ids);
    }

    @SuppressWarnings("unchecked")
    public List<Notification> getAllNotifications() {
        Set<String> ids = redis.opsForZSet()
                .rangeByScore(EVERY_IDX, System.currentTimeMillis(), NEVER);
        return load(ids == null ? Set.of() : ids);
    }

    /** Index housekeeping; reads never depend on this for correctness. */
    @Scheduled(fixedDelay = 600_000)
    public void purgeExpired() {
        double now = System.currentTimeMillis();
        redis.opsForZSet().removeRangeByScore(BROADCAST_IDX, 0, now);
        redis.opsForZSet().removeRangeByScore(EVERY_IDX, 0, now);
        // Per-email zsets: purge lazily on write, or SCAN here if they grow large.
    }

    // ---------- internals ----------

    private void save(UUID id, String html, Collection<String> emailsAllowed,
                      long expiresAt, Set<String> previousEmails) {
        if (html == null || html.isBlank())
            throw new IllegalArgumentException("Notification HTML cannot be empty");
        if (expiresAt > 0 && expiresAt <= System.currentTimeMillis())
            throw new IllegalArgumentException("Notification expiry must be in the future");

        boolean broadcast = emailsAllowed == null;
        Set<String> emails = new HashSet<>();
        if (!broadcast) emailsAllowed.forEach(e -> emails.add(normalize(e)));

        String s = id.toString(), key = key(id), allow = allowKey(id);
        double score = expiresAt > 0 ? expiresAt : NEVER;

        tx(ops -> {
            ops.delete(key);
            ops.delete(allow);
            previousEmails.forEach(e -> ops.opsForZSet().remove(EMAIL_IDX + e, s));
            ops.opsForZSet().remove(BROADCAST_IDX, s);

            ops.opsForHash().putAll(key, Map.of(
                    "html", html, "exp", Long.toString(expiresAt), "bc", broadcast ? "1" : "0"));
            ops.opsForZSet().add(EVERY_IDX, s, score);

            if (broadcast) {
                ops.opsForZSet().add(BROADCAST_IDX, s, score);
            } else if (!emails.isEmpty()) {
                ops.opsForSet().add(allow, emails.toArray(new String[0]));
                emails.forEach(e -> ops.opsForZSet().add(EMAIL_IDX + e, s, score));
            }
            if (expiresAt > 0) {
                Instant at = Instant.ofEpochMilli(expiresAt);
                ops.expireAt(key, at);
                ops.expireAt(allow, at);
            }
        });
    }

    /** Fetches all hashes in a single pipelined round trip, newest first (UUIDv7 order). */
    @SuppressWarnings("unchecked")
    private List<Notification> load(Collection<String> ids) {
        if (ids.isEmpty()) return List.of();
        List<String> ordered = ids.stream().sorted(Comparator.reverseOrder()).toList();

        List<Object> rows = pipeline(ops ->
                ordered.forEach(id -> ops.opsForHash().multiGet(PREFIX + id, FIELDS)));

        List<Notification> out = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            List<String> f = (List<String>) rows.get(i);
            if (f == null || f.get(0) == null) continue;   // hash gone (deleted/expired)
            out.add(new Notification(UUID.fromString(ordered.get(i)),
                    f.get(0), Long.parseLong(f.get(1)), "1".equals(f.get(2))));
        }
        return out;
    }

    private Set<String> currentEmails(UUID id) {
        Set<String> m = redis.opsForSet().members(allowKey(id));
        return m == null ? Set.of() : m;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<Object> pipeline(Consumer<RedisOperations<String, String>> cmds) {
        return redis.executePipelined(new SessionCallback<Object>() {
            public Object execute(RedisOperations ops) { cmds.accept(ops); return null; }
        });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<Object> tx(Consumer<RedisOperations<String, String>> cmds) {
        return redis.execute(new SessionCallback<List<Object>>() {
            public List<Object> execute(RedisOperations ops) {
                ops.multi();
                cmds.accept(ops);
                return ops.exec();
            }
        });
    }

    private String key(UUID id)      { return PREFIX + id; }
    private String allowKey(UUID id)  { return PREFIX + id + ":emails"; }

    private String normalize(String email) {
        if (email == null || email.isBlank())
            throw new IllegalArgumentException("Email cannot be empty");
        return email.trim().toLowerCase(Locale.ROOT);
    }
}