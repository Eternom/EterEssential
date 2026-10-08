package fr.eternom.eterEssential.module.tpa;

import fr.eternom.eterLib.helper.cache.RedisCache;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Demandes de tpa en cours. Elles ne vivent que quelques secondes : dans Redis (visibles depuis tous les serveurs,
 * avec expiration), jamais en base. Un joueur n'a qu'une demande envoyée à la fois ; une nouvelle remplace la
 * précédente. Appels bloquants : hors du thread principal.
 */
public class TpaRequests {

    /** here : le destinataire vient chez le demandeur (/tpahere). */
    public record Request(UUID requester, String requesterName, boolean here, long expiresAt) {
    }

    private final RedisCache redis;
    private final Duration ttl;

    public TpaRequests(RedisCache redis, Duration ttl) {
        this.redis = redis;
        this.ttl = ttl;
    }

    public long ttlSeconds() {
        return ttl.toSeconds();
    }

    public void add(UUID target, UUID requester, String requesterName, boolean here) {
        sentTo(requester).ifPresent(previous -> remove(previous, requester));
        Request request = new Request(requester, requesterName, here, System.currentTimeMillis() + ttl.toMillis());
        redis.setHashField(receivedKey(target), requester.toString(), encode(request));
        redis.expire(receivedKey(target), ttl);
        redis.set(sentKey(requester), target.toString(), ttl);
    }

    /** Demandes reçues encore valables, la plus récente en premier. */
    public List<Request> received(UUID target) {
        long now = System.currentTimeMillis();
        return redis.getHash(receivedKey(target)).values().stream()
                .map(TpaRequests::decode)
                .filter(request -> request.expiresAt() > now)
                .sorted(Comparator.comparingLong(Request::expiresAt).reversed())
                .toList();
    }

    /** Destinataire de la demande envoyée par requester, si elle est encore en cours. */
    public Optional<UUID> sentTo(UUID requester) {
        return redis.get(sentKey(requester)).map(UUID::fromString);
    }

    public void remove(UUID target, UUID requester) {
        redis.deleteHashField(receivedKey(target), requester.toString());
        redis.get(sentKey(requester)).filter(target.toString()::equals).ifPresent(value -> redis.delete(sentKey(requester)));
    }

    /** Format : here;expiresAt;pseudo;uuid. */
    private static String encode(Request request) {
        return request.here() + ";" + request.expiresAt() + ";" + request.requesterName() + ";" + request.requester();
    }

    private static Request decode(String value) {
        String[] parts = value.split(";", 4);
        return new Request(UUID.fromString(parts[3]), parts[2], Boolean.parseBoolean(parts[0]), Long.parseLong(parts[1]));
    }

    private static String receivedKey(UUID target) {
        return "tpa:received:" + target;
    }

    private static String sentKey(UUID requester) {
        return "tpa:sent:" + requester;
    }
}
