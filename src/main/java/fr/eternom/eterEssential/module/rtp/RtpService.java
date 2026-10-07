package fr.eternom.eterEssential.module.rtp;

import fr.eternom.eterLib.EterLib;
import fr.eternom.eterLib.helper.gui.BackButton;
import fr.eternom.eterLib.helper.message.Messages;
import fr.eternom.eterLib.helper.task.Tasks;
import fr.eternom.eterLib.module.teleport.Destination;
import fr.eternom.eterLib.module.teleport.TeleportService;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Téléportation aléatoire (/rtp) dans un monde de CE serveur, choisi dans le menu.
 *
 * Le délai propre au rtp est vérifié d'abord, puis un endroit sûr est cherché : chunks chargés en tâche de fond par
 * Paper (pas de lag), plusieurs essais au hasard dans l'anneau minRadius-maxRadius. Le départ passe ensuite par
 * EterLib (combat, délai commun, attente) ; le délai du rtp ne démarre que si le joueur part vraiment.
 */
public class RtpService {

    public static final String BYPASS_COOLDOWN = "eteressential.bypass.rtp";

    /** Sol sur lequel on ne fait jamais arriver un joueur. */
    private static final Set<Material> UNSAFE_GROUND = Set.of(Material.LAVA, Material.MAGMA_BLOCK, Material.CACTUS,
            Material.CAMPFIRE, Material.SOUL_CAMPFIRE, Material.FIRE, Material.SOUL_FIRE, Material.POWDER_SNOW,
            Material.SWEET_BERRY_BUSH, Material.POINTED_DRIPSTONE, Material.BEDROCK, Material.WATER);

    private final JavaPlugin plugin;
    private final RtpCooldown cooldown;
    private final TeleportService teleports;
    private final Messages messages;
    private final String serverName;
    private final List<RtpWorld> worlds;
    private final int attempts;
    private final BackButton backButton;
    /** Joueurs dont la recherche est en cours : un seul /rtp à la fois. */
    private final Set<UUID> searching = ConcurrentHashMap.newKeySet();

    public RtpService(JavaPlugin plugin, RtpCooldown cooldown, TeleportService teleports, Messages messages, String serverName,
                      List<RtpWorld> worlds, int attempts, BackButton backButton) {
        this.plugin = plugin;
        this.cooldown = cooldown;
        this.teleports = teleports;
        this.messages = messages;
        this.serverName = serverName;
        this.worlds = worlds;
        this.attempts = Math.max(1, attempts);
        this.backButton = backButton;
    }

    JavaPlugin plugin() {
        return plugin;
    }

    BackButton backButton() {
        return backButton;
    }

    public List<RtpWorld> worlds() {
        return worlds;
    }

    /** Ouvre le menu, avec le délai restant du joueur. */
    public void openMenu(Player player) {
        if (worlds.isEmpty()) {
            messages.send(player, "rtp.no-world");
            return;
        }
        Tasks.async(plugin, player, () -> remainingSeconds(player),
                remaining -> player.openInventory(new RtpMenu(this, messages, player, remaining).getInventory()),
                () -> messages.send(player, "error.generic"));
    }

    /** Thread principal : clic dans le menu. */
    void start(Player player, RtpWorld choice) {
        World world = Bukkit.getWorld(choice.world());
        if (world == null) {
            messages.send(player, "rtp.world-missing", "world", choice.world());
            return;
        }
        if (!searching.add(player.getUniqueId())) {
            messages.send(player, "rtp.already-searching");
            return;
        }
        Tasks.async(plugin, player, () -> remainingSeconds(player), remaining -> {
            if (remaining > 0) {
                searching.remove(player.getUniqueId());
                messages.send(player, "rtp.cooldown", "time", EterLib.get().formatDuration(player, remaining));
                return;
            }
            messages.actionBar(player, "rtp.searching");
            search(player, world, choice, 1);
        }, () -> {
            searching.remove(player.getUniqueId());
            messages.send(player, "error.generic");
        });
    }

    /** Bloquant. 0 si le joueur est dispensé. */
    long remainingSeconds(Player player) {
        return player.hasPermission(BYPASS_COOLDOWN) ? 0 : cooldown.remainingSeconds(player.getUniqueId());
    }

    /** Un essai : charge le chunk en tâche de fond, puis (thread principal) vérifie l'endroit. */
    private void search(Player player, World world, RtpWorld choice, int attempt) {
        Location center = world.getSpawnLocation();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double angle = random.nextDouble(Math.PI * 2);
        double distance = random.nextDouble(choice.minRadius(), choice.maxRadius());
        int x = center.getBlockX() + (int) (Math.cos(angle) * distance);
        int z = center.getBlockZ() + (int) (Math.sin(angle) * distance);

        world.getChunkAtAsync(x >> 4, z >> 4).thenAccept(chunk -> {
            if (!player.isOnline()) {
                searching.remove(player.getUniqueId());
                return;
            }
            Location found = safeLocation(world, x, z);
            if (found != null) {
                searching.remove(player.getUniqueId());
                found.setYaw(player.getLocation().getYaw());
                UUID uuid = player.getUniqueId();
                teleports.teleport(player, Destination.at(serverName, world.getName(), found.getX(), found.getY(), found.getZ(),
                                found.getYaw(), 0, messages.plain(player, "rtp.label")),
                        () -> Tasks.async(plugin, () -> cooldown.start(uuid), "Délai de /rtp non enregistré pour " + player.getName()));
            } else if (attempt < attempts) {
                search(player, world, choice, attempt + 1);
            } else {
                searching.remove(player.getUniqueId());
                messages.send(player, "rtp.failed");
            }
        });
    }

    /** Position où l'on peut se tenir (pieds et tête libres, sol solide et sans danger), ou null. Thread principal. */
    private static Location safeLocation(World world, int x, int z) {
        if (!world.getWorldBorder().isInside(new Location(world, x, 0, z))) {
            return null;
        }
        if (world.getEnvironment() == World.Environment.NETHER) {
            // Le plus haut bloc du Nether est le plafond de bedrock : on cherche un sol sous le plafond
            for (int y = 32; y < 120; y++) {
                Block ground = world.getBlockAt(x, y, z);
                if (isSafe(ground)) {
                    return ground.getLocation().add(0.5, 1, 0.5);
                }
            }
            return null;
        }
        Block ground = world.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        return ground.getY() > world.getMinHeight() && isSafe(ground) ? ground.getLocation().add(0.5, 1, 0.5) : null;
    }

    private static boolean isSafe(Block ground) {
        Block feet = ground.getRelative(0, 1, 0);
        Block head = ground.getRelative(0, 2, 0);
        return ground.getType().isSolid() && !UNSAFE_GROUND.contains(ground.getType())
                && feet.isPassable() && !feet.isLiquid() && feet.getType() != Material.FIRE
                && head.isPassable() && !head.isLiquid();
    }
}
