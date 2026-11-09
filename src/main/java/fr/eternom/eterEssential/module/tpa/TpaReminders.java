package fr.eternom.eterEssential.module.tpa;

import fr.eternom.eterEssential.module.tpa.TpaRequests.Request;
import fr.eternom.eterLib.helper.message.Messages;
import fr.eternom.eterLib.helper.task.Tasks;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ce que les joueurs d'ici voient de leurs demandes, sans relire Redis à chaque seconde :
 * - qui a reçu des demandes : la barre d'action les rappelle (« Steve · 45 s · /tpaccept »), et 10 s avant la fin, un
 *   rappel avec les boutons ;
 * - qui en a envoyé une : prévenu quand elle expire sans réponse, ou quand le joueur visé quitte le réseau (elle est
 *   alors retirée).
 * Une copie locale, relue dans Redis toutes les 5 s (une demande acceptée ou annulée ailleurs disparaît).
 */
public class TpaReminders implements Listener {

    private static final long REMIND_BEFORE_MILLIS = 10_000;

    /** Une demande envoyée par un joueur d'ici. */
    private record Sent(UUID target, String targetName, long expiresAt) {
    }

    private final JavaPlugin plugin;
    private final TpaService tpa;
    private final Messages messages;
    private final Map<UUID, Map<UUID, Request>> received = new ConcurrentHashMap<>();
    private final Set<String> reminded = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Sent> sent = new ConcurrentHashMap<>();

    public TpaReminders(JavaPlugin plugin, TpaService tpa, Messages messages) {
        this.plugin = plugin;
        this.tpa = tpa;
        this.messages = messages;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20, 20);
        Bukkit.getScheduler().runTaskTimer(plugin, this::refresh, 100, 100);
    }

    /** Une demande reçue par un joueur d'ici. */
    public void received(UUID target, Request request) {
        received.computeIfAbsent(target, uuid -> new ConcurrentHashMap<>()).put(request.requester(), request);
    }

    /** Acceptée ou refusée ici. */
    public void handled(UUID target, UUID requester) {
        Map<UUID, Request> mine = received.get(target);
        if (mine != null) {
            mine.remove(requester);
        }
    }

    public void sent(UUID requester, UUID target, String targetName, long expiresAt) {
        sent.put(requester, new Sent(target, targetName, expiresAt));
    }

    public void forgetSent(UUID requester) {
        sent.remove(requester);
    }

    /** Chaque seconde : la barre d'action, et le rappel 10 s avant la fin. */
    private void tick() {
        long now = System.currentTimeMillis();
        received.forEach((uuid, requests) -> {
            Player target = Bukkit.getPlayer(uuid);
            requests.values().removeIf(request -> request.expiresAt() <= now);
            if (target == null || requests.isEmpty()) {
                return;
            }
            Request latest = requests.values().stream().max((a, b) -> Long.compare(a.expiresAt(), b.expiresAt())).orElseThrow();
            if (requests.size() == 1) {
                messages.actionBar(target, "tpa.bar.one", "player", latest.requesterName(),
                        "seconds", String.valueOf(latest.secondsLeft(now)));
            } else {
                messages.actionBar(target, "tpa.bar.many", "count", String.valueOf(requests.size()));
            }
            for (Request request : requests.values()) {
                String key = uuid + ":" + request.requester() + ":" + request.expiresAt();
                if (request.expiresAt() - now <= REMIND_BEFORE_MILLIS && reminded.add(key)) {
                    messages.send(target, "tpa.reminder", "player", request.requesterName(),
                            "seconds", String.valueOf(request.secondsLeft(now)));
                    target.sendMessage(tpa.buttons(target, request));
                }
            }
        });
        if (reminded.size() > 1000) {
            reminded.clear();
        }
    }

    /**
     * Toutes les 5 s, en tâche de fond : les demandes reçues relues (acceptées ou annulées ailleurs : retirées) ; pour
     * les demandes envoyées, fin sans réponse ou joueur visé parti du réseau.
     */
    private void refresh() {
        List<UUID> targets = new ArrayList<>(received.keySet());
        Map<UUID, Sent> sentNow = Map.copyOf(sent);
        if (targets.isEmpty() && sentNow.isEmpty()) {
            return;
        }
        Tasks.async(plugin, () -> {
            for (UUID target : targets) {
                Map<UUID, Request> fresh = new ConcurrentHashMap<>();
                tpa.requests().received(target).forEach(request -> fresh.put(request.requester(), request));
                if (fresh.isEmpty()) {
                    received.remove(target);
                } else {
                    received.put(target, fresh);
                }
            }
            long now = System.currentTimeMillis();
            sentNow.forEach((requester, request) -> {
                boolean pending = tpa.requests().sentTo(requester).filter(request.target()::equals).isPresent();
                if (!pending) {
                    sent.remove(requester);
                    if (now >= request.expiresAt() - 1500) {
                        notify(requester, "tpa.expired", request.targetName());
                    }
                } else if (tpa.lookup().serverOf(request.target()).isEmpty()) {
                    tpa.requests().remove(request.target(), requester);
                    sent.remove(requester);
                    notify(requester, "tpa.target-left", request.targetName());
                }
            });
        }, "Demandes de tpa non relues");
    }

    private void notify(UUID player, String key, String name) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player online = Bukkit.getPlayer(player);
            if (online != null) {
                messages.send(online, key, "player", name);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        received.remove(event.getPlayer().getUniqueId());
        sent.remove(event.getPlayer().getUniqueId());
    }
}
