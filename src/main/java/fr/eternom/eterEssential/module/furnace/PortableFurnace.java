package fr.eternom.eterEssential.module.furnace;

import fr.eternom.eterLib.helper.cache.RedisCache;
import fr.eternom.eterLib.helper.message.Messages;
import fr.eternom.eterLib.helper.task.Tasks;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MenuType;
import org.bukkit.inventory.view.FurnaceView;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.time.Duration;

/**
 * Le four portatif (/furnace) : un vrai four, propre à chaque joueur et le même sur tout le réseau, qui continue de
 * cuire une fois fermé. Le menu est celui du four, sans bloc : tant qu'il est ouvert, ce code le fait cuire tick par
 * tick ; à la fermeture, le contenu part en base avec l'heure ; à la prochaine ouverture, le temps écoulé est rattrapé.
 */
public class PortableFurnace implements Listener {

    /** Rattrapage maximal du temps passé fermé : 7 jours, au-delà la cuisson était de toute façon finie. */
    private static final long MAX_CATCH_UP_TICKS = 7L * 24 * 60 * 60 * 20;
    /**
     * Enregistrement pendant que le four est ouvert, toutes les 5 s : si le serveur plante, la base ne garde pas un
     * contenu que le joueur a déjà sorti (sinon il le retrouverait en double).
     */
    private static final int AUTOSAVE_TICKS = 100;
    /**
     * Verrou Redis du four (furnace:lock:<uuid>) : pris à l'ouverture, rendu une fois le dernier enregistrement écrit.
     * Un joueur qui change de serveur four ouvert ne peut pas le rouvrir ailleurs avant que la base soit à jour (sinon il
     * relirait l'ancien contenu et retrouverait en double ce qu'il venait de sortir). Expire seul si le serveur plante.
     */
    private static final Duration LOCK_TTL = Duration.ofSeconds(30);

    private final JavaPlugin plugin;
    private final FurnaceStore store;
    private final FurnaceSimulation simulation;
    private final Messages messages;
    private final RedisCache redis;
    /** Valeur du verrou propre à ce serveur et à ce démarrage : on ne rend que son propre verrou. */
    private final String lockOwner = UUID.randomUUID().toString();
    private final Map<UUID, Session> open = new HashMap<>();
    /** Fours en cours de chargement : un double /furnace n'ouvre pas deux fois le même four. */
    private final Set<UUID> loading = new HashSet<>();
    /** Un seul fil pour écrire : les enregistrements d'un four arrivent en base dans l'ordre. */
    private final ExecutorService saver = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "EterEssential-furnace");
        thread.setDaemon(true);
        return thread;
    });

    private static final class Session {
        final FurnaceView view;
        final FurnaceState state;
        BukkitTask task;
        int ticks;

        Session(FurnaceView view, FurnaceState state) {
            this.view = view;
            this.state = state;
        }
    }

    public PortableFurnace(JavaPlugin plugin, FurnaceStore store, FurnaceSimulation simulation, Messages messages, RedisCache redis) {
        this.redis = redis;
        this.plugin = plugin;
        this.store = store;
        this.simulation = simulation;
        this.messages = messages;
    }

    public void open(Player player) {
        UUID uuid = player.getUniqueId();
        if (open.containsKey(uuid) || !loading.add(uuid)) {
            return;
        }
        Tasks.async(plugin, player, () -> {
            if (!redis.setIfAbsent(lockKey(uuid), lockOwner, LOCK_TTL)) {
                return null; // ouvert ou en cours d'enregistrement sur un autre serveur
            }
            return store.load(uuid).orElseGet(FurnaceState::empty);
        }, state -> {
            loading.remove(uuid);
            if (state == null) {
                messages.send(player, "furnace.busy");
                return;
            }
            long elapsed = Math.max(0, System.currentTimeMillis() - state.savedAt) / 50;
            simulation.advance(state, Math.min(elapsed, MAX_CATCH_UP_TICKS));
            show(player, state);
        }, () -> {
            loading.remove(uuid);
            messages.send(player, "error.generic");
        });
    }

    private void show(Player player, FurnaceState state) {
        FurnaceView view = MenuType.FURNACE.builder().title(messages.get(player, "furnace.title")).checkReachable(false).build(player);
        FurnaceInventory inventory = view.getTopInventory();
        inventory.setSmelting(state.input);
        inventory.setFuel(state.fuel);
        inventory.setResult(state.output);
        Session session = new Session(view, state);
        open.put(player.getUniqueId(), session);
        player.openInventory(view);
        player.playSound(player, Sound.BLOCK_FURNACE_FIRE_CRACKLE, 0.6f, 1f);
        session.task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> tick(player.getUniqueId(), session), 1L, 1L);
    }

    /** Un tick de cuisson : ce que le joueur a mis ou retiré compte, puis le four avance et le menu suit. */
    private void tick(UUID uuid, Session session) {
        FurnaceState state = session.state;
        FurnaceView view = session.view;
        readMenu(session);
        simulation.advance(state, 1);

        FurnaceInventory inventory = view.getTopInventory();
        if (!same(inventory.getSmelting(), state.input)) {
            inventory.setSmelting(state.input);
        }
        if (!same(inventory.getFuel(), state.fuel)) {
            inventory.setFuel(state.fuel);
        }
        if (!same(inventory.getResult(), state.output)) {
            inventory.setResult(state.output);
        }
        var recipe = simulation.recipe(state.input);
        view.setCookTime(state.cook, recipe == null ? 200 : recipe.getCookingTime());
        view.setBurnTime(state.lit, Math.max(1, state.litTotal));

        if (++session.ticks % AUTOSAVE_TICKS == 0) {
            save(uuid, state, false);
        }
    }

    /** Reprend dans l'état ce que le joueur a mis ou retiré du menu. Un autre objet à cuire : cuisson à zéro, comme dans le jeu. */
    private static void readMenu(Session session) {
        FurnaceState state = session.state;
        FurnaceInventory inventory = session.view.getTopInventory();
        ItemStack input = inventory.getSmelting();
        if (FurnaceSimulation.isEmpty(input) || FurnaceSimulation.isEmpty(state.input) || !input.isSimilar(state.input)) {
            state.cook = 0;
        }
        state.input = input;
        state.fuel = inventory.getFuel();
        state.output = inventory.getResult();
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        Session session = open.get(player.getUniqueId());
        // Un seul menu ouvert à la fois : un four qui se ferme pendant une session, c'est le nôtre (on ne compare pas
        // les objets « vue » : Paper peut en donner un autre à la fermeture, et le contenu ne serait jamais enregistré)
        if (session == null || event.getInventory().getType() != InventoryType.FURNACE) {
            return;
        }
        open.remove(player.getUniqueId());
        session.task.cancel();
        readMenu(session);
        // Le menu n'a pas de bloc : vidé ici, rien ne peut en être repris après l'enregistrement
        session.view.getTopInventory().clear();
        FurnaceState state = session.state;
        // L'expérience des recettes (points, pas niveaux), donnée à la fermeture ; la fraction reste pour la prochaine fois
        int points = (int) state.xp;
        if (points > 0) {
            player.giveExp(points);
            state.xp -= points;
        }
        save(player.getUniqueId(), state, true);
    }

    /** last : dernier enregistrement (four fermé), après lequel le verrou est rendu ; sinon, le verrou est prolongé. */
    private void save(UUID uuid, FurnaceState state, boolean last) {
        state.savedAt = System.currentTimeMillis();
        FurnaceState copy = state.copy();
        saver.execute(() -> {
            try {
                store.save(uuid, copy);
                if (last) {
                    redis.deleteIfValue(lockKey(uuid), lockOwner);
                } else {
                    redis.expire(lockKey(uuid), LOCK_TTL);
                }
            } catch (RuntimeException e) {
                // Verrou gardé : il expire seul, le temps qu'on voie l'erreur, plutôt que rouvrir un contenu périmé
                plugin.getLogger().severe("Four portatif de " + uuid + " non enregistré : " + e.getMessage());
            }
        });
    }

    private static String lockKey(UUID uuid) {
        return "furnace:lock:" + uuid;
    }

    /** Arrêt du serveur : chaque four ouvert est enregistré, puis on attend que tout soit écrit. */
    public void closeAll() {
        for (Map.Entry<UUID, Session> entry : Map.copyOf(open).entrySet()) {
            Session session = entry.getValue();
            open.remove(entry.getKey());
            session.task.cancel();
            readMenu(session);
            session.view.getTopInventory().clear();
            save(entry.getKey(), session.state, true);
        }
        saver.shutdown();
        try {
            if (!saver.awaitTermination(10, TimeUnit.SECONDS)) {
                plugin.getLogger().severe("Fours portatifs : enregistrement pas terminé à l'arrêt.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static boolean same(ItemStack a, ItemStack b) {
        boolean emptyA = FurnaceSimulation.isEmpty(a);
        boolean emptyB = FurnaceSimulation.isEmpty(b);
        if (emptyA || emptyB) {
            return emptyA == emptyB;
        }
        return a.isSimilar(b) && a.getAmount() == b.getAmount();
    }
}
