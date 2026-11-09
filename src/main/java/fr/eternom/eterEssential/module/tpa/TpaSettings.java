package fr.eternom.eterEssential.module.tpa;

import fr.eternom.eterLib.helper.cache.RedisCache;
import fr.eternom.eterLib.helper.sql.Column;
import fr.eternom.eterLib.helper.sql.Database;
import fr.eternom.eterLib.helper.sql.Row;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Qui peut envoyer une demande de tpa à un joueur (/tptoggle), sur tout le réseau. Table eteressential_players
 * (source), recopiée dans Redis (tpa:settings, uuid -> « mode;tpahere refusés;auto clan ») : lue à chaque demande sans
 * passer par la base. Appels bloquants.
 * - mode : ALL (tout le monde), FRIENDS (seulement sa liste /tpaauto et les membres de son clan), NONE (personne) ;
 * - tpahere refusés : personne ne peut le faire venir (/tptoggle here) ;
 * - auto clan : les demandes des membres de son clan sont acceptées toutes seules (/tpaauto clan).
 */
public class TpaSettings {

    public enum Mode { ALL, FRIENDS, NONE;

        public Mode next() {
            return values()[(ordinal() + 1) % values().length];
        }

        static Mode parse(String value) {
            try {
                return value == null ? ALL : valueOf(value.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return ALL;
            }
        }
    }

    /** Les réglages d'un joueur. */
    public record Settings(Mode mode, boolean hereDisabled, boolean autoClan) {

        public static final Settings DEFAULT = new Settings(Mode.ALL, false, false);

        String encode() {
            return mode.name() + ";" + hereDisabled + ";" + autoClan;
        }

        static Settings decode(String value) {
            String[] parts = value.split(";", 3);
            return new Settings(Mode.parse(parts[0]), parts.length > 1 && Boolean.parseBoolean(parts[1]),
                    parts.length > 2 && Boolean.parseBoolean(parts[2]));
        }
    }

    private static final String TABLE = "players";
    private static final String KEY = "tpa:settings";

    private final Database database;
    private final RedisCache redis;

    public TpaSettings(Database database, RedisCache redis) {
        this.database = database;
        this.redis = redis;
        database.createTable(TABLE,
                Column.of("uuid", Column.Type.UUID).primaryKey(),
                Column.of("tpa_disabled", Column.Type.BOOLEAN).notNull());
        database.addColumn(TABLE, Column.of("tpa_mode", Column.Type.STRING).length(8));
        database.addColumn(TABLE, Column.of("tpahere_disabled", Column.Type.BOOLEAN));
        database.addColumn(TABLE, Column.of("tpa_auto_clan", Column.Type.BOOLEAN));
    }

    /** Ses réglages : Redis, sinon la base (puis recopiés dans Redis). */
    public Settings get(UUID player) {
        Optional<String> cached = redis.getHashField(KEY, player.toString());
        if (cached.isPresent()) {
            return Settings.decode(cached.get());
        }
        Settings settings = database.getFirst(TABLE, Map.of("uuid", player)).map(TpaSettings::fromRow).orElse(Settings.DEFAULT);
        redis.setHashField(KEY, player.toString(), settings.encode());
        return settings;
    }

    public void set(UUID player, Settings settings) {
        Map<String, Object> values = new HashMap<>();
        values.put("uuid", player);
        values.put("tpa_disabled", settings.mode() == Mode.NONE);
        values.put("tpa_mode", settings.mode().name());
        values.put("tpahere_disabled", settings.hereDisabled());
        values.put("tpa_auto_clan", settings.autoClan());
        database.set(TABLE, values, "uuid");
        redis.setHashField(KEY, player.toString(), settings.encode());
    }

    /** Personne ne peut lui envoyer de demande (API, ancien /tptoggle). */
    public boolean isDisabled(UUID player) {
        return get(player).mode() == Mode.NONE;
    }

    private static Settings fromRow(Row row) {
        String mode = row.getString("tpa_mode");
        Mode parsed = mode == null ? (row.getBoolean("tpa_disabled") ? Mode.NONE : Mode.ALL) : Mode.parse(mode);
        return new Settings(parsed, row.getBoolean("tpahere_disabled"), row.getBoolean("tpa_auto_clan"));
    }
}
