package fr.eternom.eterEssential.module.death;

import com.google.gson.JsonObject;
import fr.eternom.eterLib.EterLib;
import fr.eternom.eterLib.helper.cache.NetworkBus;
import fr.eternom.eterLib.helper.message.Messages;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Chaque mort, à la place du message de Minecraft :
 * - un message public tiré au hasard parmi les variantes de sa cause (lang/ > death.public.&lt;cause&gt;), sur ce serveur ou
 *   sur tout le réseau (death.announce). Chaque serveur l'écrit dans la langue de ses joueurs ; un même tirage (seed)
 *   choisit la variante partout ;
 * - pour le mort seulement : un son propre à la cause, une pique suivie d'une vraie astuce (death.tips.&lt;cause&gt;), et
 *   l'endroit de sa mort (avec /back s'il peut y retourner).
 * Joueur invisible (vanish) : rien de public.
 */
public class DeathAnnouncer implements Listener {

    private static final String BUS_TYPE = "death";
    private static final String BACK_DEATH = "eteressential.back.death";

    private enum Scope { NETWORK, SERVER, NONE }

    private record Tone(Sound sound, float volume, float pitch) {
    }

    /** Son de chaque cause, pour le mort seulement ; config.yml > death.sounds les remplace un par un. */
    private static final Map<String, String> DEFAULT_SOUNDS = Map.ofEntries(
            Map.entry("lava", "block.lava.extinguish 1 0.8"),
            Map.entry("fire", "entity.generic.burn 1 1"),
            Map.entry("fall", "entity.player.big_fall 1 0.7"),
            Map.entry("void", "entity.enderman.teleport 1 0.5"),
            Map.entry("drown", "entity.drowned.death 1 1"),
            Map.entry("suffocation", "block.gravel.break 1 0.6"),
            Map.entry("starvation", "entity.player.burp 1 0.6"),
            Map.entry("lightning", "entity.lightning_bolt.thunder 0.5 1.2"),
            Map.entry("magic", "entity.witch.celebrate 1 1"),
            Map.entry("wither", "entity.wither.ambient 0.6 1.2"),
            Map.entry("freeze", "block.glass.break 1 0.7"),
            Map.entry("contact", "entity.player.hurt_sweet_berry_bush 1 0.8"),
            Map.entry("falling", "block.anvil.land 0.8 0.8"),
            Map.entry("kill", "block.note_block.didgeridoo 1 0.5"),
            Map.entry("explosion", "entity.generic.explode 0.6 1.2"),
            Map.entry("creeper", "entity.creeper.primed 1 0.8"),
            Map.entry("player", "entity.player.attack.knockback 1 0.6"),
            Map.entry("warden", "entity.warden.roar 0.6 1"),
            Map.entry("dragon", "entity.ender_dragon.growl 0.6 1"),
            Map.entry("mob", "entity.villager.no 1 0.7"),
            Map.entry("generic", "block.note_block.bass 1 0.5"));

    private final JavaPlugin plugin;
    private final Messages messages;
    private final NetworkBus bus;
    private final Scope scope;
    private final Map<String, Tone> sounds = new HashMap<>();
    /** Morts pour s'être déconnecté en combat : cause « combat-log », l'adversaire fui à la place du tueur. */
    private final CombatLog combatLog;

    public DeathAnnouncer(JavaPlugin plugin, Messages messages, NetworkBus bus, ConfigurationSection config, CombatLog combatLog) {
        this.combatLog = combatLog;
        this.plugin = plugin;
        this.messages = messages;
        this.bus = bus;
        Scope read;
        try {
            read = Scope.valueOf(config.getString("announce", "network").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("death.announce invalide (network, server ou none) : network utilisé");
            read = Scope.NETWORK;
        }
        this.scope = read;
        DEFAULT_SOUNDS.forEach(this::loadSound);
        ConfigurationSection custom = config.getConfigurationSection("sounds");
        if (custom != null) {
            custom.getKeys(false).forEach(cause -> loadSound(cause, custom.getString(cause, "")));
        }
        bus.on(BUS_TYPE, this::announce);
    }

    /** "entity.generic.burn 0.8 1.2" : son Minecraft, volume, hauteur. Invalide : avertissement, le son d'avant reste. */
    private void loadSound(String cause, String text) {
        String[] parts = text.trim().split("\\s+");
        Sound sound = RegistryAccess.registryAccess().getRegistry(RegistryKey.SOUND_EVENT)
                .get(NamespacedKey.minecraft(parts[0].toLowerCase(Locale.ROOT)));
        if (sound == null) {
            plugin.getLogger().warning("death.sounds." + cause + " : son inconnu « " + parts[0] + " »");
            return;
        }
        try {
            float volume = parts.length > 1 ? Float.parseFloat(parts[1]) : 1f;
            float pitch = parts.length > 2 ? Float.parseFloat(parts[2]) : 1f;
            sounds.put(cause, new Tone(sound, volume, pitch));
        } catch (NumberFormatException e) {
            plugin.getLogger().warning("death.sounds." + cause + " : volume ou hauteur invalide");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        String fled = combatLog == null ? null : combatLog.fledFrom(player.getUniqueId());
        DeathCause cause = fled != null ? new DeathCause("combat-log", null) : DeathCause.of(event.getDamageSource());
        Entity killer = cause.killer() == player ? null : cause.killer(); // sa propre perle, son propre feu d'artifice

        if (scope != Scope.NONE) {
            event.deathMessage(null);
            if (!EterLib.get().getVanish().isVanished(player.getUniqueId())) {
                JsonObject data = new JsonObject();
                data.addProperty("player", player.getName());
                data.addProperty("cause", cause.key());
                data.addProperty("seed", ThreadLocalRandom.current().nextInt(Integer.MAX_VALUE));
                if (fled != null) {
                    data.addProperty("killer", fled);
                } else if (killer instanceof Player killerPlayer) {
                    data.addProperty("killer", killerPlayer.getName());
                } else if (killer != null && killer.customName() == null) {
                    data.addProperty("killer-key", killer.getType().translationKey());
                } else if (killer != null) {
                    data.addProperty("killer", killer.getName());
                }
                announce(data);
                if (scope == Scope.NETWORK) {
                    bus.publish(BUS_TYPE, data);
                }
            }
        }
        comfort(player, cause.key(), player.getLocation());
    }

    /** Message public, aux joueurs de ce serveur, chacun dans sa langue. */
    private void announce(JsonObject data) {
        String cause = data.get("cause").getAsString();
        int seed = data.get("seed").getAsInt();
        Component killer = data.has("killer-key") ? Component.translatable(data.get("killer-key").getAsString())
                : Component.text(data.has("killer") ? data.get("killer").getAsString() : "?");
        TagResolver tags = Placeholder.component("killer", killer);
        String player = data.get("player").getAsString();
        for (Player receiver : Bukkit.getOnlinePlayers()) {
            String variant = pick(receiver, "death.public." + cause, "death.public.generic", seed);
            if (variant != null) {
                receiver.sendMessage(messages.render(variant, tags, "player", player));
            }
        }
    }

    /** Pour le mort : son, pique et astuce, position. */
    private void comfort(Player player, String cause, Location where) {
        Tone tone = sounds.getOrDefault(cause, sounds.get("generic"));
        if (tone != null) {
            player.playSound(player, tone.sound(), tone.volume(), tone.pitch());
        }
        String tip = pick(player, "death.tips." + cause, "death.tips.generic", ThreadLocalRandom.current().nextInt(Integer.MAX_VALUE));
        if (tip != null) {
            player.sendMessage(messages.prefix().append(messages.render(tip, TagResolver.empty())));
        }
        messages.send(player, player.hasPermission(BACK_DEATH) ? "death.position-back" : "death.position",
                "x", String.valueOf(where.getBlockX()), "y", String.valueOf(where.getBlockY()), "z", String.valueOf(where.getBlockZ()),
                "world", where.getWorld().getName());
    }

    /** Une des variantes (liste en lang/), la même pour un même seed ; celles de fallback si la cause n'en a pas. */
    private String pick(Player receiver, String key, String fallback, int seed) {
        String raw = messages.raw(receiver, key);
        if (raw == null || raw.isBlank()) {
            raw = messages.raw(receiver, fallback);
        }
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] variants = raw.split("\n");
        return variants[Math.floorMod(seed, variants.length)];
    }
}
