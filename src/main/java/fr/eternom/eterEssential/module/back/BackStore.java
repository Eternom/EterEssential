package fr.eternom.eterEssential.module.back;

import fr.eternom.eterLib.helper.cache.RedisCache;
import fr.eternom.eterLib.module.teleport.Destination;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Dernière position quittée par chaque joueur (/back), commune à tout le réseau : dans Redis, qui l'oublie après
 * {@link #TTL}. Jamais en mémoire : chaque serveur aurait sa copie, et on pourrait faire plusieurs /back en changeant
 * de serveur. Appels bloquants : hors du thread principal.
 */
public class BackStore {

    private static final Duration TTL = Duration.ofDays(1);

    private final RedisCache redis;

    public BackStore(RedisCache redis) {
        this.redis = redis;
    }

    public void save(UUID player, Destination position) {
        redis.set(key(player), position.yaw() + ";" + position.pitch() + ";" + position.x() + ";" + position.y() + ";"
                + position.z() + ";" + position.server() + ";" + position.world(), TTL);
    }

    /** label : texte affiché pendant l'attente (bossbar). */
    public Optional<Destination> get(UUID player, String label) {
        return redis.get(key(player)).map(value -> {
            // yaw;pitch;x;y;z;serveur;monde (le monde en dernier : il peut contenir un ';')
            String[] parts = value.split(";", 7);
            return Destination.at(parts[5], parts[6], Double.parseDouble(parts[2]), Double.parseDouble(parts[3]),
                    Double.parseDouble(parts[4]), Float.parseFloat(parts[0]), Float.parseFloat(parts[1]), label);
        });
    }

    private static String key(UUID player) {
        return "back:" + player;
    }
}
