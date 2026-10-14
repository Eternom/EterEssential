package fr.eternom.eterEssential.module.death;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Warden;

import java.util.Map;

/**
 * La cause d'une mort, réduite à une famille (lava, fall, creeper, player...) : c'est elle qui choisit le message public,
 * le conseil et le son (lang/ > death.public.&lt;cause&gt;, death.tips.&lt;cause&gt; ; config.yml > death.sounds.&lt;cause&gt;).
 * killer : le joueur ou la créature responsable (le tireur pour une flèche), null sinon.
 */
record DeathCause(String key, Entity killer) {

    /** Type de dégâts Minecraft (minecraft:lava...) vers sa famille ; les autres dépendent du responsable. */
    private static final Map<String, String> BY_TYPE = Map.ofEntries(
            Map.entry("lava", "lava"),
            Map.entry("in_fire", "fire"), Map.entry("on_fire", "fire"), Map.entry("campfire", "fire"), Map.entry("hot_floor", "fire"),
            Map.entry("fall", "fall"), Map.entry("fly_into_wall", "fall"), Map.entry("ender_pearl", "fall"),
            Map.entry("out_of_world", "void"), Map.entry("outside_border", "void"),
            Map.entry("drown", "drown"),
            Map.entry("in_wall", "suffocation"), Map.entry("cramming", "suffocation"),
            Map.entry("starve", "starvation"),
            Map.entry("lightning_bolt", "lightning"),
            Map.entry("magic", "magic"), Map.entry("indirect_magic", "magic"),
            Map.entry("wither", "wither"), Map.entry("wither_skull", "wither"),
            Map.entry("freeze", "freeze"),
            Map.entry("cactus", "contact"), Map.entry("sweet_berry_bush", "contact"), Map.entry("stalagmite", "contact"),
            Map.entry("falling_anvil", "falling"), Map.entry("falling_block", "falling"), Map.entry("falling_stalactite", "falling"),
            Map.entry("generic_kill", "kill"),
            Map.entry("dragon_breath", "dragon"),
            Map.entry("explosion", "explosion"), Map.entry("player_explosion", "explosion"),
            Map.entry("bad_respawn_point", "explosion"), Map.entry("fireworks", "explosion"));

    static DeathCause of(DamageSource source) {
        Entity killer = source.getCausingEntity();
        String type = RegistryAccess.registryAccess().getRegistry(RegistryKey.DAMAGE_TYPE).getKeyOrThrow(source.getDamageType()).value();
        String family = BY_TYPE.get(type);
        if (killer instanceof Creeper) {
            return new DeathCause("creeper", killer);
        }
        if (family != null) {
            return new DeathCause(family, killer);
        }
        return switch (killer) {
            case Player player -> new DeathCause("player", player);
            case Warden warden -> new DeathCause("warden", warden);
            case EnderDragon dragon -> new DeathCause("dragon", dragon);
            case null -> new DeathCause("generic", null);
            default -> new DeathCause("mob", killer);
        };
    }
}
