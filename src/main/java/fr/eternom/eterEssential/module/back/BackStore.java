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

    /** death : la position est celle d'une mort (sinon le départ d'une téléportation), pour le dire au retour. */
    public void save(UUID player, Destination position, boolean death) {
        redis.set(kindKey(player), death ? "death" : "teleport", TTL);
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

    /** true si la dernière position est celle d'une mort. Bloquant. */
    public boolean isDeath(UUID player) {
        return redis.get(kindKey(player)).map("death"::equals).orElse(false);
    }

    private static String kindKey(UUID player) {
        return "back:kind:" + player;
    }

    private static String key(UUID player) {
        return "back:" + player;
    }
}
