package fr.eternom.eterEssential.module.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.eternom.eterLib.helper.cache.RedisMessenger;
import fr.eternom.eterLib.helper.message.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Messages d'EterEssential entre les serveurs (Redis pub/sub, canal "eteressential") : demande de tpa, départ d'un
 * joueur vers un autre, message à un joueur, annonce... Chaque message a un type ; le serveur d'origine ignore le sien.
 * Les gestionnaires sont appelés sur le thread principal. Sans Redis (ou Redis en panne), rien ne part : les
 * fonctionnalités restent limitées au serveur où l'on est.
 */
public class NetworkBus {

    private static final String CHANNEL = "eteressential";
    private static final String NOTIFY = "notify";
    private static final long WARNING_INTERVAL_MILLIS = 60_000;

    private final JavaPlugin plugin;
    private final RedisMessenger messenger; // null sans Redis
    private final Messages messages;
    private final String serverName;
    private final Map<String, Consumer<JsonObject>> handlers = new ConcurrentHashMap<>();
    private volatile long lastWarning;

    public NetworkBus(JavaPlugin plugin, RedisMessenger messenger, Messages messages, String serverName) {
        this.plugin = plugin;
        this.messenger = messenger;
        this.messages = messages;
        this.serverName = serverName;
        on(NOTIFY, data -> {
            Player player = Bukkit.getPlayer(UUID.fromString(data.get("player").getAsString()));
            if (player != null) {
                notifyLocal(player, data.get("key").getAsString(), data.get("sound").getAsBoolean(), strings(data.getAsJsonArray("values")));
            }
        });
    }

    public void start() {
        if (messenger != null) {
            messenger.subscribe(CHANNEL, this::receive);
        }
    }

    /** true si les messages traversent les serveurs (Redis actif). */
    public boolean isNetworked() {
        return messenger != null;
    }

    /** handler (thread principal) pour les messages de ce type venant des autres serveurs. */
    public void on(String type, Consumer<JsonObject> handler) {
        handlers.put(type, handler);
    }

    /** Thread principal : envoie aux autres serveurs ; onFailure (thread principal) si Redis n'a pas pu transmettre. */
    public void publish(String type, JsonObject data, Runnable onFailure) {
        if (messenger == null) {
            onFailure.run();
            return;
        }
        data.addProperty("type", type);
        data.addProperty("origin", serverName);
        String json = data.toString();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                messenger.publish(CHANNEL, json);
            } catch (RuntimeException e) {
                warn(e);
                Bukkit.getScheduler().runTask(plugin, onFailure);
            }
        });
    }

    /**
     * Thread principal : message de langue (avec préfixe) à un joueur, où qu'il soit sur le réseau.
     * Ignoré s'il n'est connecté nulle part. sound : petit son de notification.
     */
    public void notify(UUID player, String key, boolean sound, String... placeholders) {
        Player local = Bukkit.getPlayer(player);
        if (local != null) {
            notifyLocal(local, key, sound, placeholders);
            return;
        }
        JsonObject data = new JsonObject();
        data.addProperty("player", player.toString());
        data.addProperty("key", key);
        data.addProperty("sound", sound);
        JsonArray values = new JsonArray();
        for (String value : placeholders) {
            values.add(value);
        }
        data.add("values", values);
        publish(NOTIFY, data, () -> {
        });
    }

    private void notifyLocal(Player player, String key, boolean sound, String... placeholders) {
        messages.send(player, key, placeholders);
        if (sound) {
            player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BELL, 0.6f, 1.4f);
        }
    }

    /** Fil d'écoute Redis. */
    private void receive(String json) {
        JsonObject data = JsonParser.parseString(json).getAsJsonObject();
        if (serverName.equals(data.get("origin").getAsString()) || !plugin.isEnabled()) {
            return;
        }
        Consumer<JsonObject> handler = handlers.get(data.get("type").getAsString());
        if (handler != null) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    handler.accept(data);
                } catch (RuntimeException e) {
                    plugin.getLogger().log(Level.SEVERE, "Message réseau mal traité : " + json, e);
                }
            });
        }
    }

    private static String[] strings(JsonArray array) {
        String[] values = new String[array.size()];
        int i = 0;
        for (JsonElement element : array) {
            values[i++] = element.getAsString();
        }
        return values;
    }

    /** Redis en panne : un avertissement par minute au plus. */
    private void warn(RuntimeException e) {
        long now = System.currentTimeMillis();
        if (now - lastWarning > WARNING_INTERVAL_MILLIS) {
            lastWarning = now;
            plugin.getLogger().warning("Redis injoignable, message vers les autres serveurs perdu : " + e.getMessage());
        }
    }
}
