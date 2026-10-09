package fr.eternom.eterEssential.module.tpa;

import com.google.gson.JsonObject;
import fr.eternom.eterLib.helper.cache.NetworkBus;
import fr.eternom.eterEssential.module.network.PlayerLookup;
import fr.eternom.eterEssential.module.network.PlayerLookup.OnlinePlayer;
import fr.eternom.eterEssential.module.tpa.TpaRequests.Request;
import fr.eternom.eterLib.helper.message.Messages;
import fr.eternom.eterLib.helper.task.Tasks;
import fr.eternom.eterLib.module.teleport.Destination;
import fr.eternom.eterLib.module.teleport.TeleportService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Téléportation entre joueurs (/tpa, /tpahere), sur tout le réseau.
 * Le départ passe toujours par EterLib (combat, délai, attente), avec les règles de celui qui voyage.
 *
 * La demande est gardée côté destinataire ; il l'accepte depuis son serveur. S'il faut faire partir le demandeur
 * depuis un autre serveur, le message "tpa-teleport" demande à ce serveur de lancer sa téléportation.
 */
public class TpaService {

    public static final String BYPASS_TOGGLE = "eteressential.bypass.tptoggle";

    private static final String REQUEST = "tpa-request";
    private static final String TELEPORT = "tpa-teleport";

    /** Demande prise par le destinataire, avec le serveur actuel du demandeur (vide s'il est parti). */
    private record Taken(Request request, Optional<String> requesterServer) {
    }

    private final JavaPlugin plugin;
    private final TeleportService teleports;
    private final NetworkBus bus;
    private final PlayerLookup lookup;
    private final TpaRequests requests;
    private final TpaSettings settings;
    private final Messages messages;

    public TpaService(JavaPlugin plugin, TeleportService teleports, NetworkBus bus, PlayerLookup lookup, TpaRequests requests,
                      TpaSettings settings, Messages messages) {
        this.plugin = plugin;
        this.teleports = teleports;
        this.bus = bus;
        this.lookup = lookup;
        this.requests = requests;
        this.settings = settings;
        this.messages = messages;

        bus.on(REQUEST, data -> {
            Player target = Bukkit.getPlayer(UUID.fromString(data.get("target").getAsString()));
            if (target != null) {
                showRequest(target, data.get("requester_name").getAsString(), data.get("here").getAsBoolean());
            }
        });
        bus.on(TELEPORT, data -> {
            Player traveller = Bukkit.getPlayer(UUID.fromString(data.get("traveller").getAsString()));
            if (traveller != null) {
                teleports.teleport(traveller, Destination.toPlayer(UUID.fromString(data.get("target").getAsString()),
                        data.get("target_server").getAsString(), data.get("label").getAsString()));
            }
        });
    }

    /** /tpa (here = false) ou /tpahere (here = true). */
    public void request(Player requester, String targetName, boolean here) {
        lookup.findOnline(requester, targetName, found -> {
            if (found.isEmpty()) {
                messages.send(requester, "player.offline", "player", targetName);
                return;
            }
            OnlinePlayer target = found.get();
            if (target.uuid().equals(requester.getUniqueId())) {
                messages.send(requester, "tpa.self");
                return;
            }
            boolean bypass = requester.hasPermission(BYPASS_TOGGLE);
            UUID requesterId = requester.getUniqueId();
            String requesterName = requester.getName();
            Tasks.async(plugin, requester, () -> {
                if (!bypass && settings.isDisabled(target.uuid())) {
                    return false;
                }
                requests.add(target.uuid(), requesterId, requesterName, here);
                return true;
            }, accepted -> {
                if (!accepted) {
                    messages.send(requester, "tpa.disabled", "player", target.name());
                    return;
                }
                messages.send(requester, here ? "tpa.sent-here" : "tpa.sent", "player", target.name(),
                        "seconds", String.valueOf(requests.ttlSeconds()));
                deliverRequest(requester, target, here);
            }, () -> messages.send(requester, "error.generic"));
        });
    }

    /** /tpaccept [joueur] : sans pseudo, la demande la plus récente. */
    public void accept(Player target, String requesterName) {
        take(target, requesterName, taken -> {
            Request request = taken.request();
            if (taken.requesterServer().isEmpty()) {
                messages.send(target, "tpa.requester-gone", "player", request.requesterName());
                return;
            }
            String requesterServer = taken.requesterServer().get();
            messages.send(target, "tpa.accepted-target", "player", request.requesterName());
            bus.notify(request.requester(), "tpa.accepted", true, "player", target.getName());
            if (request.here()) {
                // /tpahere : c'est le destinataire (ici) qui part chez le demandeur
                teleports.teleport(target, Destination.toPlayer(request.requester(), requesterServer, request.requesterName()));
                return;
            }
            Player requester = Bukkit.getPlayer(request.requester());
            Destination destination = Destination.toPlayer(target.getUniqueId(), lookup.serverName(), target.getName());
            if (requester != null) {
                teleports.teleport(requester, destination);
            } else {
                JsonObject data = new JsonObject();
                data.addProperty("traveller", request.requester().toString());
                data.addProperty("target", target.getUniqueId().toString());
                data.addProperty("target_server", lookup.serverName());
                data.addProperty("label", target.getName());
                bus.publish(TELEPORT, data, () -> messages.send(target, "network.failed"));
            }
        });
    }

    /** /tpdeny [joueur]. */
    public void deny(Player target, String requesterName) {
        take(target, requesterName, taken -> {
            messages.send(target, "tpa.denied-target", "player", taken.request().requesterName());
            bus.notify(taken.request().requester(), "tpa.denied", true, "player", target.getName());
        });
    }

    /** /tpacancel : annule la demande envoyée. */
    public void cancel(Player requester) {
        UUID uuid = requester.getUniqueId();
        Tasks.async(plugin, requester, () -> {
            Optional<UUID> target = requests.sentTo(uuid);
            target.ifPresent(found -> requests.remove(found, uuid));
            return target;
        }, target -> {
            if (target.isEmpty()) {
                messages.send(requester, "tpa.nothing-sent");
                return;
            }
            messages.send(requester, "tpa.cancelled");
            bus.notify(target.get(), "tpa.cancelled-target", false, "player", requester.getName());
        }, () -> messages.send(requester, "error.generic"));
    }

    /** /tptoggle. */
    public void toggle(Player player) {
        UUID uuid = player.getUniqueId();
        Tasks.async(plugin, player, () -> settings.toggle(uuid),
                disabled -> messages.send(player, disabled ? "tpa.toggle.disabled" : "tpa.toggle.enabled"),
                () -> messages.send(player, "error.generic"));
    }

    /** Retire la demande choisie (la plus récente si requesterName est null) et trouve où est le demandeur. */
    private void take(Player target, String requesterName, Consumer<Taken> then) {
        UUID uuid = target.getUniqueId();
        Tasks.async(plugin, target, () -> requests.received(uuid).stream()
                        .filter(request -> requesterName == null
                                || request.requesterName().toLowerCase(Locale.ROOT).equals(requesterName.toLowerCase(Locale.ROOT)))
                        .findFirst()
                        .map(request -> {
                            requests.remove(uuid, request.requester());
                            return new Taken(request, lookup.serverOf(request.requester()));
                        }),
                taken -> taken.ifPresentOrElse(then,
                        () -> messages.send(target, requesterName == null ? "tpa.none" : "tpa.none-from", "player",
                                requesterName == null ? "" : requesterName)),
                () -> messages.send(target, "error.generic"));
    }

    private void deliverRequest(Player requester, OnlinePlayer target, boolean here) {
        Player local = Bukkit.getPlayer(target.uuid());
        if (local != null) {
            showRequest(local, requester.getName(), here);
            return;
        }
        JsonObject data = new JsonObject();
        data.addProperty("target", target.uuid().toString());
        data.addProperty("requester_name", requester.getName());
        data.addProperty("here", here);
        bus.publish(REQUEST, data, () -> messages.send(requester, "network.failed"));
    }

    /** La demande, avec des boutons [Accepter] [Refuser] cliquables. */
    private void showRequest(Player target, String requesterName, boolean here) {
        messages.send(target, here ? "tpa.received-here" : "tpa.received", "player", requesterName,
                "seconds", String.valueOf(requests.ttlSeconds()));
        Component accept = messages.get(target, "tpa.button.accept")
                .clickEvent(ClickEvent.runCommand("/tpaccept " + requesterName))
                .hoverEvent(HoverEvent.showText(messages.get(target, "tpa.button.accept-hover", "player", requesterName)));
        Component deny = messages.get(target, "tpa.button.deny")
                .clickEvent(ClickEvent.runCommand("/tpdeny " + requesterName))
                .hoverEvent(HoverEvent.showText(messages.get(target, "tpa.button.deny-hover", "player", requesterName)));
        target.sendMessage(messages.prefix().append(accept).append(Component.space()).append(deny));
        target.playSound(target, Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 1.6f);
    }
}
