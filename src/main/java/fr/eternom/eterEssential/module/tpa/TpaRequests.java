package fr.eternom.eterEssential.module.tpa;

import fr.eternom.eterLib.helper.cache.RedisCache;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Demandes de tpa en cours. Elles ne vivent que quelques secondes : dans Redis (visibles depuis tous les serveurs,
 * avec expiration), sinon en mémoire (tpa limité à ce serveur). Jamais en base.
 * Un joueur n'a qu'une demande envoyée à la fois ; une nouvelle remplace la précédente.
 * Appels bloquants avec Redis : hors du thread principal.
 */
public class TpaRequests {

    /** here : le destinataire vient chez le demandeur (/tpahere). */
    public record Request(UUID requester, String requesterName, boolean here, long expiresAt) {
    }

    private final RedisCache redis; // null sans Redis
    private final Duration ttl;
    /** Sans Redis : destinataire -> demandeur -> demande ; demandeur -> destinataire. */
    private final Map<UUID, Map<UUID, Request>> received = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> sent = new ConcurrentHashMap<>();

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
        if (redis != null) {
            redis.setHashField(receivedKey(target), requester.toString(), encode(request));
            redis.expire(receivedKey(target), ttl);
            redis.set(sentKey(requester), target.toString(), ttl);
        } else {
            received.computeIfAbsent(target, key -> new ConcurrentHashMap<>()).put(requester, request);
            sent.put(requester, target);
        }
    }

    /** Demandes reçues encore valables, la plus récente en premier. */
    public List<Request> received(UUID target) {
        List<Request> requests = new ArrayList<>();
        long now = System.currentTimeMillis();
        if (redis != null) {
            redis.getHash(receivedKey(target)).values().forEach(value -> requests.add(decode(value)));
        } else {
            Map<UUID, Request> pending = received.get(target);
            if (pending != null) {
                pending.values().removeIf(request -> request.expiresAt() <= now); // pas d'expiration automatique en mémoire
                requests.addAll(pending.values());
            }
        }
        return requests.stream()
                .filter(request -> request.expiresAt() > now)
                .sorted(Comparator.comparingLong(Request::expiresAt).reversed())
                .toList();
    }

    /** Destinataire de la demande envoyée par requester, si elle est encore en cours. */
    public Optional<UUID> sentTo(UUID requester) {
        if (redis != null) {
            return redis.get(sentKey(requester)).map(UUID::fromString);
        }
        return Optional.ofNullable(sent.get(requester))
                .filter(target -> received.getOrDefault(target, Map.of()).containsKey(requester));
    }

    public void remove(UUID target, UUID requester) {
        if (redis != null) {
            redis.deleteHashField(receivedKey(target), requester.toString());
            redis.get(sentKey(requester)).filter(target.toString()::equals).ifPresent(value -> redis.delete(sentKey(requester)));
        } else {
            Map<UUID, Request> requests = received.get(target);
            if (requests != null) {
                requests.remove(requester);
            }
            sent.remove(requester, target);
        }
    }

    /** Format Redis : here;expiresAt;pseudo;uuid. */
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
