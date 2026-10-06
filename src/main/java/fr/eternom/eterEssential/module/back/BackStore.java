package fr.eternom.eterEssential.module.back;

import fr.eternom.eterLib.helper.cache.RedisCache;
import fr.eternom.eterLib.helper.sql.Column;
import fr.eternom.eterLib.helper.sql.Database;
import fr.eternom.eterLib.module.teleport.Destination;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Dernière position quittée par chaque joueur (/back), commune à tout le réseau : dans Redis (expire après
 * {@link #TTL}), sinon dans la table eteressential_back. Jamais en mémoire : chaque serveur aurait sa copie, et on
 * pourrait faire plusieurs /back en changeant de serveur. Appels bloquants : hors du thread principal.
 */
public class BackStore {

    private static final String TABLE = "back";
    private static final Duration TTL = Duration.ofDays(1);

    private final Database database;
    private final RedisCache redis; // null sans Redis

    public BackStore(Database database, RedisCache redis) {
        this.database = database;
        this.redis = redis;
        if (redis == null) {
            database.createTable(TABLE,
                    Column.of("uuid", Column.Type.UUID).primaryKey(),
                    Column.of("server", Column.Type.STRING).length(64).notNull(),
                    Column.of("world", Column.Type.STRING).length(64).notNull(),
                    Column.of("x", Column.Type.DOUBLE).notNull(),
                    Column.of("y", Column.Type.DOUBLE).notNull(),
                    Column.of("z", Column.Type.DOUBLE).notNull(),
                    Column.of("yaw", Column.Type.FLOAT).notNull(),
                    Column.of("pitch", Column.Type.FLOAT).notNull());
        }
    }

    public void save(UUID player, Destination position) {
        if (redis != null) {
            redis.set(key(player), position.yaw() + ";" + position.pitch() + ";" + position.x() + ";" + position.y() + ";"
                    + position.z() + ";" + position.server() + ";" + position.world(), TTL);
            return;
        }
        database.set(TABLE, Map.of("uuid", player, "server", position.server(), "world", position.world(), "x", position.x(),
                "y", position.y(), "z", position.z(), "yaw", position.yaw(), "pitch", position.pitch()), "uuid");
    }

    /** label : texte affiché pendant l'attente (bossbar). */
    public Optional<Destination> get(UUID player, String label) {
        if (redis != null) {
            return redis.get(key(player)).map(value -> {
                // yaw;pitch;x;y;z;serveur;monde (le monde en dernier : il peut contenir un ';')
                String[] parts = value.split(";", 7);
                return Destination.at(parts[5], parts[6], Double.parseDouble(parts[2]), Double.parseDouble(parts[3]),
                        Double.parseDouble(parts[4]), Float.parseFloat(parts[0]), Float.parseFloat(parts[1]), label);
            });
        }
        return database.getFirst(TABLE, Map.of("uuid", player)).map(row -> Destination.at(row.getString("server"),
                row.getString("world"), row.getDouble("x"), row.getDouble("y"), row.getDouble("z"), row.getFloat("yaw"),
                row.getFloat("pitch"), label));
    }

    private static String key(UUID player) {
        return "back:" + player;
    }
}
