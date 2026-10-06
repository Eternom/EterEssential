package fr.eternom.eterEssential.module.rtp;

import fr.eternom.eterLib.helper.cache.RedisCache;
import fr.eternom.eterLib.helper.sql.Column;
import fr.eternom.eterLib.helper.sql.Database;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * Délai propre à /rtp (en plus du délai commun des téléportations), valable sur tout le réseau : Redis avec
 * expiration, sinon la table eteressential_rtp (jamais en mémoire, sinon il suffirait de changer de serveur).
 * Appels bloquants : hors du thread principal.
 */
public class RtpCooldown {

    private static final String TABLE = "rtp";

    private final Database database;
    private final RedisCache redis; // null sans Redis
    private final Duration cooldown;

    public RtpCooldown(Database database, RedisCache redis, Duration cooldown) {
        this.database = database;
        this.redis = redis;
        this.cooldown = cooldown;
        if (redis == null) {
            database.createTable(TABLE,
                    Column.of("uuid", Column.Type.UUID).primaryKey(),
                    Column.of("until", Column.Type.LONG).notNull());
            database.execute("DELETE FROM " + database.table(TABLE) + " WHERE until < ?", System.currentTimeMillis());
        }
    }

    /** Secondes restantes avant le prochain /rtp, 0 s'il est possible. */
    public long remainingSeconds(UUID player) {
        long until = redis != null
                ? redis.get(key(player)).map(Long::parseLong).orElse(0L)
                : database.getFirst(TABLE, Map.of("uuid", player)).map(row -> row.getLong("until")).orElse(0L);
        long remaining = until - System.currentTimeMillis();
        return remaining <= 0 ? 0 : (remaining + 999) / 1000;
    }

    public void start(UUID player) {
        if (cooldown.isZero()) {
            return;
        }
        long until = System.currentTimeMillis() + cooldown.toMillis();
        if (redis != null) {
            redis.set(key(player), String.valueOf(until), cooldown);
        } else {
            database.set(TABLE, Map.of("uuid", player, "until", until), "uuid");
        }
    }

    private static String key(UUID player) {
        return "rtp:cooldown:" + player;
    }
}
