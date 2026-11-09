package fr.eternom.eterEssential.module.tpa;

import fr.eternom.eterLib.helper.cache.RedisCache;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Demandes de tpa en cours. Elles ne vivent que quelques secondes : dans Redis (visibles depuis tous les serveurs,
 * avec expiration), jamais en base. Appels bloquants : hors du thread principal.
 * - Un joueur n'a qu'une demande envoyée à la fois ; une nouvelle remplace la précédente.
 * - Un joueur reçoit au plus maxReceived demandes ; au-delà, la plus ancienne part.
 * - Pas deux demandes au même joueur en moins de spamSeconds (tryThrottle).
 * - Une demande acceptée dont le départ échoue (combat, mouvement...) revient (restore), avec son heure de fin.
 */
public class TpaRequests {

    /** here : le destinataire vient chez le demandeur (/tpahere). server : où était le demandeur à l'envoi. */
    public record Request(UUID requester, String requesterName, boolean here, long expiresAt, String server) {

        public long secondsLeft(long now) {
            return Math.max(0, (expiresAt - now + 999) / 1000);
        }
    }

    private final RedisCache redis;
    private final Duration ttl;
    private final Duration hereTtl;
    private final int maxReceived;
    private final Duration spam;

    public TpaRequests(RedisCache redis, Duration ttl, Duration hereTtl, int maxReceived, Duration spam) {
        this.redis = redis;
        this.ttl = ttl;
        this.hereTtl = hereTtl;
        this.maxReceived = Math.max(1, maxReceived);
        this.spam = spam;
    }

    public long ttlSeconds(boolean here) {
        return (here ? hereTtl : ttl).toSeconds();
    }

    /** Une nouvelle demande (remplace celle que le demandeur avait envoyée ailleurs). */
    public Request add(UUID target, UUID requester, String requesterName, boolean here, String server) {
        sentTo(requester).ifPresent(previous -> remove(previous, requester));
        Request request = new Request(requester, requesterName, here, System.currentTimeMillis() + (here ? hereTtl : ttl).toMillis(),
                server);
        store(target, request);
        return request;
    }

    /** Une demande de plus, sans retirer celles déjà envoyées (/tpaclan). */
    public Request addKeeping(UUID target, UUID requester, String requesterName, boolean here, String server) {
        Request request = new Request(requester, requesterName, here, System.currentTimeMillis() + (here ? hereTtl : ttl).toMillis(),
                server);
        store(target, request);
        return request;
    }

    /** Remet une demande retirée (le départ a échoué), si elle n'est pas finie. */
    public void restore(UUID target, Request request) {
        if (request.expiresAt() > System.currentTimeMillis() + 1000) {
            store(target, request);
        }
    }

    private void store(UUID target, Request request) {
        List<Request> received = received(target);
        // Trop de demandes : la plus ancienne part (sauf si c'est une mise à jour du même demandeur)
        if (received.size() >= maxReceived && received.stream().noneMatch(found -> found.requester().equals(request.requester()))) {
            Request oldest = received.getLast();
            remove(target, oldest.requester());
        }
        Duration left = Duration.ofMillis(Math.max(1000, request.expiresAt() - System.currentTimeMillis()));
        redis.setHashField(receivedKey(target), request.requester().toString(), encode(request));
        redis.expire(receivedKey(target), Duration.ofMillis(Math.max(ttl.toMillis(), hereTtl.toMillis())));
        redis.set(sentKey(request.requester()), target.toString(), left);
    }

    /** Pas plus d'une demande au même joueur toutes les spamSeconds : false s'il faut attendre. */
    public boolean tryThrottle(UUID requester, UUID target) {
        return spam.isZero() || redis.setIfAbsent("tpa:spam:" + requester + ":" + target, "1", spam);
    }

    public long spamSeconds() {
        return spam.toSeconds();
    }

    /** Demandes reçues encore valables, la plus récente en premier. */
    public List<Request> received(UUID target) {
        long now = System.currentTimeMillis();
        return redis.getHash(receivedKey(target)).values().stream()
                .map(TpaRequests::decode)
                .filter(request -> request != null && request.expiresAt() > now)
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

    /** Format : here;expiresAt;pseudo;uuid;serveur (le serveur est absent des demandes d'avant 1.8.0). */
    static String encode(Request request) {
        return request.here() + ";" + request.expiresAt() + ";" + request.requesterName() + ";" + request.requester() + ";"
                + (request.server() == null ? "" : request.server());
    }

    static Request decode(String value) {
        String[] parts = value.split(";", 5);
        if (parts.length < 4) {
            return null;
        }
        try {
            return new Request(UUID.fromString(parts[3]), parts[2], Boolean.parseBoolean(parts[0]), Long.parseLong(parts[1]),
                    parts.length > 4 ? parts[4] : "");
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String receivedKey(UUID target) {
        return "tpa:received:" + target;
    }

    private static String sentKey(UUID requester) {
        return "tpa:sent:" + requester;
    }
}
