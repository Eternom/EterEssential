package fr.eternom.eterEssential.module.help;

import fr.eternom.eterLib.helper.cache.RedisCache;
import fr.eternom.eterLib.helper.message.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.UUID;

/**
 * /news : les nouveautés de la dernière mise à jour (lang/ news.lines, à réécrire à chaque mise à jour). Quand
 * news.id (config.yml) change, chaque joueur en est prévenu une fois à sa connexion, avec un lien vers /news ; le
 * dernier id vu est gardé dans Redis (commun au réseau). id vide : pas d'annonce.
 */
public class News implements TabExecutor, Listener {

    private static final long JOIN_DELAY_TICKS = 100;

    private final JavaPlugin plugin;
    private final RedisCache redis;
    private final Messages messages;

    public News(JavaPlugin plugin, RedisCache redis, Messages messages) {
        this.plugin = plugin;
        this.redis = redis;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        messages.send(sender, "news.header");
        sender.sendMessage(messages.get(sender, "news.lines"));
        if (sender instanceof Player player) {
            markSeen(player.getUniqueId());
        }
        return true;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        String id = plugin.getConfig().getString("news.id", "");
        if (id.isBlank()) {
            return;
        }
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean seen;
            try {
                seen = redis.get(key(uuid)).map(id::equals).orElse(false);
            } catch (RuntimeException e) {
                return;
            }
            if (!seen) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) {
                        messages.send(player, "news.available");
                    }
                }, JOIN_DELAY_TICKS);
            }
        });
    }

    private void markSeen(UUID player) {
        String id = plugin.getConfig().getString("news.id", "");
        if (!id.isBlank()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    redis.set(key(player), id);
                } catch (RuntimeException ignored) {
                    // Redis indisponible : l'annonce reviendra, sans gravité
                }
            });
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return List.of();
    }

    private static String key(UUID player) {
        return "eteressential:news-seen:" + player;
    }
}
