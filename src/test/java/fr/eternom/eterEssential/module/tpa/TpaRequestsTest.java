package fr.eternom.eterEssential.module.tpa;

import fr.eternom.eterEssential.module.tpa.TpaRequests.Request;
import fr.eternom.eterLib.helper.cache.RedisCache;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Les demandes de tpa : envoi, remplacement, plafond, anti-spam, retour après un départ raté, format. */
class TpaRequestsTest {

    /** Un Redis en mémoire (sans expiration réelle : les demandes portent leur propre heure de fin). */
    private static final class MemoryRedis extends RedisCache {

        private final Map<String, String> values = new HashMap<>();
        private final Map<String, Map<String, String>> hashes = new HashMap<>();

        MemoryRedis() {
            super(null);
        }

        @Override
        public Optional<String> get(String key) {
            return Optional.ofNullable(values.get(key));
        }

        @Override
        public void set(String key, String value) {
            values.put(key, value);
        }

        @Override
        public void set(String key, String value, Duration ttl) {
            values.put(key, value);
        }

        @Override
        public boolean setIfAbsent(String key, String value, Duration ttl) {
            return values.putIfAbsent(key, value) == null;
        }

        @Override
        public void delete(String key) {
            values.remove(key);
        }

        @Override
        public void expire(String key, Duration ttl) {
        }

        @Override
        public Map<String, String> getHash(String key) {
            return new HashMap<>(hashes.getOrDefault(key, Map.of()));
        }

        @Override
        public Optional<String> getHashField(String key, String field) {
            return Optional.ofNullable(hashes.getOrDefault(key, Map.of()).get(field));
        }

        @Override
        public void setHashField(String key, String field, String value) {
            hashes.computeIfAbsent(key, k -> new HashMap<>()).put(field, value);
        }

        @Override
        public void deleteHashField(String key, String field) {
            Map<String, String> hash = hashes.get(key);
            if (hash != null) {
                hash.remove(field);
            }
        }
    }

    private static TpaRequests requests(int max) {
        return new TpaRequests(new MemoryRedis(), Duration.ofSeconds(60), Duration.ofSeconds(30), max, Duration.ofSeconds(10));
    }

    @Test
    void sendAndReceive() {
        TpaRequests requests = requests(5);
        UUID target = UUID.randomUUID();
        UUID requester = UUID.randomUUID();
        Request request = requests.add(target, requester, "Steve", false, "survival");
        assertEquals(List.of(request), requests.received(target));
        assertEquals(Optional.of(target), requests.sentTo(requester));
        requests.remove(target, requester);
        assertTrue(requests.received(target).isEmpty());
        assertTrue(requests.sentTo(requester).isEmpty());
    }

    @Test
    void newRequestReplacesThePreviousOne() {
        TpaRequests requests = requests(5);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID requester = UUID.randomUUID();
        requests.add(first, requester, "Steve", false, "survival");
        requests.add(second, requester, "Steve", false, "survival");
        assertTrue(requests.received(first).isEmpty());
        assertEquals(1, requests.received(second).size());
    }

    @Test
    void clanRequestsKeepEachOther() {
        TpaRequests requests = requests(5);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID requester = UUID.randomUUID();
        requests.addKeeping(first, requester, "Steve", true, "survival");
        requests.addKeeping(second, requester, "Steve", true, "survival");
        assertEquals(1, requests.received(first).size());
        assertEquals(1, requests.received(second).size());
    }

    @Test
    void oldestLeavesWhenFull() throws InterruptedException {
        TpaRequests requests = requests(2);
        UUID target = UUID.randomUUID();
        UUID oldest = UUID.randomUUID();
        requests.add(target, oldest, "A", false, "s");
        Thread.sleep(5);
        requests.add(target, UUID.randomUUID(), "B", false, "s");
        Thread.sleep(5);
        requests.add(target, UUID.randomUUID(), "C", false, "s");
        List<Request> received = requests.received(target);
        assertEquals(2, received.size());
        assertTrue(received.stream().noneMatch(request -> request.requester().equals(oldest)));
        assertEquals("C", received.getFirst().requesterName());
    }

    @Test
    void throttle() {
        TpaRequests requests = requests(5);
        UUID requester = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        assertTrue(requests.tryThrottle(requester, target));
        assertFalse(requests.tryThrottle(requester, target));
        assertTrue(requests.tryThrottle(requester, UUID.randomUUID()));
    }

    @Test
    void failedDepartureGivesTheRequestBack() {
        TpaRequests requests = requests(5);
        UUID target = UUID.randomUUID();
        Request request = requests.add(target, UUID.randomUUID(), "Steve", false, "s");
        requests.remove(target, request.requester());
        requests.restore(target, request);
        assertEquals(List.of(request), requests.received(target));
        // Une demande finie ne revient pas
        Request over = new Request(UUID.randomUUID(), "Old", false, System.currentTimeMillis() - 1, "s");
        requests.restore(target, over);
        assertEquals(1, requests.received(target).size());
    }

    @Test
    void hereRequestsHaveTheirOwnLength() {
        TpaRequests requests = requests(5);
        assertEquals(60, requests.ttlSeconds(false));
        assertEquals(30, requests.ttlSeconds(true));
    }

    @Test
    void format() {
        Request request = new Request(UUID.randomUUID(), "Steve", true, 123L, "survival");
        assertEquals(request, TpaRequests.decode(TpaRequests.encode(request)));
        // Ancien format (avant 1.8.0) : sans serveur
        Request old = TpaRequests.decode("false;456;Alex;" + request.requester());
        assertNotNull(old);
        assertEquals("", old.server());
        assertNull(TpaRequests.decode("abîmé"));
    }

    @Test
    void settingsCycle() {
        assertEquals(TpaSettings.Mode.FRIENDS, TpaSettings.Mode.ALL.next());
        assertEquals(TpaSettings.Mode.NONE, TpaSettings.Mode.FRIENDS.next());
        assertEquals(TpaSettings.Mode.ALL, TpaSettings.Mode.NONE.next());
        TpaSettings.Settings settings = new TpaSettings.Settings(TpaSettings.Mode.FRIENDS, true, true);
        assertEquals(settings, TpaSettings.Settings.decode(settings.encode()));
    }
}
