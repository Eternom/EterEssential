package fr.eternom.eterEssential.module.tpa;

import com.google.gson.JsonObject;
import fr.eternom.eterEconomy.api.EconomyApi;
import fr.eternom.eterEssential.module.network.PlayerLookup;
import fr.eternom.eterEssential.module.network.PlayerLookup.OnlinePlayer;
import fr.eternom.eterEssential.module.tpa.TpaLists.Kind;
import fr.eternom.eterEssential.module.tpa.TpaRequests.Request;
import fr.eternom.eterEssential.module.tpa.TpaSettings.Mode;
import fr.eternom.eterEssential.module.tpa.TpaSettings.Settings;
import fr.eternom.eterLib.EterLib;
import fr.eternom.eterLib.helper.cache.Cooldowns;
import fr.eternom.eterLib.helper.cache.NetworkBus;
import fr.eternom.eterLib.helper.message.Messages;
import fr.eternom.eterLib.helper.task.Tasks;
import fr.eternom.eterLib.module.combat.CombatTracker;
import fr.eternom.eterLib.module.player.PlayerDirectory.NetworkPlayer;
import fr.eternom.eterLib.module.teleport.Destination;
import fr.eternom.eterLib.module.teleport.TeleportOptions;
import fr.eternom.eterLib.module.teleport.TeleportOptions.Failure;
import fr.eternom.eterLib.module.teleport.TeleportService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Téléportation entre joueurs (/tpa, /tpahere), sur tout le réseau.
 * Le départ passe toujours par EterLib (combat, cooldown, attente), avec les règles de celui qui voyage ; mais bouger
 * pendant l'attente ne l'annule pas : le voyageur n'a pas forcément lancé le départ (un tpa accepté par un autre).
 *
 * Avant d'envoyer : le voyageur peut-il partir (combat, cooldown, argent) ; le destinataire accepte-t-il (/tptoggle,
 * /tpaignore, mode amis), n'est-il pas en combat ; pas deux demandes au même joueur trop vite. À l'acceptation : la
 * demande n'est retirée qu'une fois les règles vérifiées, et elle revient si le départ échoue (combat, mouvement,
 * cible partie), avec un bouton pour réessayer. Les amis (/tpaauto) et le clan (option) sont acceptés tout seuls ;
 * entre membres d'un clan, pas d'attente (tpa.clan-no-warmup).
 *
 * La demande est gardée côté destinataire ; il l'accepte depuis son serveur. S'il faut faire partir le demandeur
 * depuis un autre serveur, le message "tpa-teleport" demande à ce serveur de lancer sa téléportation (et de rendre
 * la demande si elle échoue).
 */
public class TpaService {

    public static final String BYPASS_TOGGLE = "eteressential.bypass.tptoggle";
    public static final String FREE = "eteressential.tpa.free";
    public static final String ALERTS = "eter.mod.alerts";

    private static final String REQUEST = "tpa-request";
    private static final String TELEPORT = "tpa-teleport";
    private static final String ALERT = "tpa-alert";

    /** Ce qu'a donné une demande. */
    private enum Kind2 { SENT, COOLDOWN, NO_MONEY, DISABLED, FRIENDS_ONLY, HERE_DISABLED, TARGET_COMBAT, SPAM }

    private record Outcome(Kind2 kind, long seconds, Request request, boolean auto) {
    }

    /** Ce que l'acceptation a trouvé : le serveur du demandeur, son clan, un cooldown à attendre. */
    private record Checked(Optional<String> requesterServer, boolean sameClan, long cooldown) {
    }

    private final JavaPlugin plugin;
    private final TeleportService teleports;
    private final CombatTracker combat;
    private final NetworkBus bus;
    private final PlayerLookup lookup;
    private final TpaRequests requests;
    private final TpaSettings settings;
    private final TpaLists lists;
    private final TpaLog log;
    private final TpaConfig config;
    private final Messages messages;
    private final Cooldowns clanCooldown;
    private TpaReminders reminders;
    /** Anti-piège : à qui chaque joueur d'ici a envoyé des demandes récemment, et quand le staff a été alerté. */
    private final Map<UUID, Deque<Map.Entry<UUID, Long>>> recentTargets = new ConcurrentHashMap<>();
    private final Map<UUID, Long> alertedAt = new ConcurrentHashMap<>();

    public TpaService(JavaPlugin plugin, TeleportService teleports, CombatTracker combat, NetworkBus bus, PlayerLookup lookup,
                      TpaRequests requests, TpaSettings settings, TpaLists lists, TpaLog log, TpaConfig config, Messages messages) {
        this.plugin = plugin;
        this.teleports = teleports;
        this.combat = combat;
        this.bus = bus;
        this.lookup = lookup;
        this.requests = requests;
        this.settings = settings;
        this.lists = lists;
        this.log = log;
        this.config = config;
        this.messages = messages;
        this.clanCooldown = new Cooldowns(EterLib.get().getRedis(), "tpa:clan", config.clanCooldown());

        bus.on(REQUEST, data -> {
            Player target = Bukkit.getPlayer(UUID.fromString(data.get("target").getAsString()));
            Request request = TpaRequests.decode(data.get("request").getAsString());
            if (target != null && request != null) {
                showRequest(target, request, data.get("auto").getAsBoolean());
            }
        });
        bus.on(TELEPORT, data -> {
            Player traveller = Bukkit.getPlayer(UUID.fromString(data.get("traveller").getAsString()));
            Request request = TpaRequests.decode(data.get("request").getAsString());
            if (traveller == null || request == null) {
                return;
            }
            UUID target = UUID.fromString(data.get("target").getAsString());
            String targetName = data.get("label").getAsString();
            depart(traveller, Destination.toPlayer(target, data.get("target_server").getAsString(), targetName),
                    data.get("same_clan").getAsBoolean(), target, targetName, request);
        });
        bus.on(ALERT, data -> showAlert(data.get("player").getAsString(), data.get("count").getAsString()));
    }

    /** Les rappels (barre d'action, expiration) : branchés après la création. */
    public void setReminders(TpaReminders reminders) {
        this.reminders = reminders;
    }

    public TpaRequests requests() {
        return requests;
    }

    public PlayerLookup lookup() {
        return lookup;
    }

    // ---------- Envoyer ----------

    /** /tpa (here = false) ou /tpahere (here = true). */
    public void request(Player requester, String targetName, boolean here) {
        request(requester, targetName, here, true);
    }

    /** replace : la nouvelle demande remplace celle envoyée avant (false pour /tpaclan, qui en envoie plusieurs). */
    private void request(Player requester, String targetName, boolean here, boolean replace) {
        if (config.isBlocked(requester.getWorld().getName())) {
            messages.send(requester, "tpa.world-blocked");
            return;
        }
        // Avec /tpa, c'est le demandeur qui part : il doit pouvoir partir, sinon inutile de déranger l'autre
        if (!here) {
            long fighting = teleports.combatSeconds(requester);
            if (fighting > 0) {
                messages.send(requester, "tpa.self-combat", "time", String.valueOf(fighting));
                return;
            }
        }
        lookup.findOnline(requester, targetName, found -> {
            if (found.isEmpty()) {
                // Hors ligne (ou invisible), ou jamais venu : pas le même message
                lookup.findAny(requester, targetName, known -> messages.send(requester,
                        known.isPresent() ? "player.offline" : "player.unknown", "player", targetName));
                return;
            }
            OnlinePlayer target = found.get();
            if (target.uuid().equals(requester.getUniqueId())) {
                messages.send(requester, "tpa.self");
                return;
            }
            send(requester, target, here, replace);
        });
    }

    private void send(Player requester, OnlinePlayer target, boolean here, boolean replace) {
        boolean bypass = requester.hasPermission(BYPASS_TOGGLE);
        double cost = cost(requester, here);
        UUID requesterId = requester.getUniqueId();
        String requesterName = requester.getName();
        String server = lookup.serverName();
        Tasks.async(plugin, requester, () -> {
            if (!here) {
                long cooldown = teleports.cooldownSeconds(requester);
                if (cooldown > 0) {
                    return new Outcome(Kind2.COOLDOWN, cooldown, null, false);
                }
            }
            if (cost > 0 && EconomyApi.get().map(economy -> !economy.has(requesterId, cost)).orElse(false)) {
                return new Outcome(Kind2.NO_MONEY, 0, null, false);
            }
            Settings targetSettings = settings.get(target.uuid());
            boolean ignored = lists.contains(target.uuid(), Kind.IGNORE, requesterId) || ChatIgnores.ignores(target.uuid(), requesterId);
            boolean friend = lists.contains(target.uuid(), Kind.AUTO, requesterId);
            boolean sameClan = (targetSettings.mode() == Mode.FRIENDS || targetSettings.autoClan()) && Clans.same(requesterId, target.uuid());
            if (!bypass) {
                if (ignored || targetSettings.mode() == Mode.NONE) {
                    return new Outcome(Kind2.DISABLED, 0, null, false);
                }
                if (targetSettings.mode() == Mode.FRIENDS && !friend && !sameClan) {
                    return new Outcome(Kind2.FRIENDS_ONLY, 0, null, false);
                }
                if (here && targetSettings.hereDisabled()) {
                    return new Outcome(Kind2.HERE_DISABLED, 0, null, false);
                }
            }
            if (config.refuseTargetInCombat() && combat.networkSeconds(target.uuid()) > 0) {
                return new Outcome(Kind2.TARGET_COMBAT, 0, null, false);
            }
            if (!requests.tryThrottle(requesterId, target.uuid())) {
                return new Outcome(Kind2.SPAM, requests.spamSeconds(), null, false);
            }
            Request request = replace ? requests.add(target.uuid(), requesterId, requesterName, here, server)
                    : requests.addKeeping(target.uuid(), requesterId, requesterName, here, server);
            watchForTraps(requesterId, requesterName, target.uuid());
            return new Outcome(Kind2.SENT, 0, request, friend || (targetSettings.autoClan() && sameClan));
        }, outcome -> {
            switch (outcome.kind()) {
                case COOLDOWN -> messages.send(requester, "tpa.self-cooldown", "time", String.valueOf(outcome.seconds()));
                case NO_MONEY -> messages.send(requester, "tpa.no-money", "amount", format(cost));
                case DISABLED -> messages.send(requester, "tpa.disabled", "player", target.name());
                case FRIENDS_ONLY -> messages.send(requester, "tpa.friends-only", "player", target.name());
                case HERE_DISABLED -> messages.send(requester, "tpa.here-disabled", "player", target.name());
                case TARGET_COMBAT -> messages.send(requester, "tpa.target-combat", "player", target.name());
                case SPAM -> messages.send(requester, "tpa.spam", "player", target.name(), "time", String.valueOf(outcome.seconds()));
                case SENT -> sent(requester, target, outcome.request(), outcome.auto());
            }
        }, () -> messages.send(requester, "error.generic"));
    }

    /** Envoyée : le demandeur voit [Annuler] ; le destinataire la reçoit où qu'il soit. */
    private void sent(Player requester, OnlinePlayer target, Request request, boolean auto) {
        String seconds = String.valueOf(requests.ttlSeconds(request.here()));
        Component line = messages.get(requester, request.here() ? "tpa.sent-here" : "tpa.sent", "player", target.name(), "seconds", seconds);
        Component cancel = messages.get(requester, "tpa.button.cancel").clickEvent(ClickEvent.runCommand("/tpacancel"))
                .hoverEvent(HoverEvent.showText(messages.get(requester, "tpa.button.cancel-hover")));
        requester.sendMessage(messages.prefix().append(line).append(Component.space()).append(cancel));
        if (reminders != null) {
            reminders.sent(requester.getUniqueId(), target.uuid(), target.name(), request.expiresAt());
        }
        Player local = Bukkit.getPlayer(target.uuid());
        if (local != null) {
            showRequest(local, request, auto);
            return;
        }
        JsonObject data = new JsonObject();
        data.addProperty("target", target.uuid().toString());
        data.addProperty("request", TpaRequests.encode(request));
        data.addProperty("auto", auto);
        bus.publish(REQUEST, data, () -> messages.send(requester, "network.failed"));
    }

    /** Thread principal : la demande, avec [Accepter] [Refuser] [Ignorer] ; un ami est accepté tout seul. */
    void showRequest(Player target, Request request, boolean auto) {
        if (reminders != null) {
            reminders.received(target.getUniqueId(), request);
        }
        if (auto) {
            messages.send(target, "tpa.auto-accepted", "player", request.requesterName());
            accept(target, request);
            return;
        }
        String seconds = String.valueOf(request.secondsLeft(System.currentTimeMillis()));
        messages.send(target, request.here() ? "tpa.received-here" : "tpa.received", "player", request.requesterName(), "seconds", seconds);
        target.sendMessage(buttons(target, request));
        target.playSound(target, Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 1.6f);
    }

    /** [Accepter] [Refuser] [Ignorer], le serveur du demandeur au survol. */
    Component buttons(Player target, Request request) {
        String name = request.requesterName();
        String server = request.server() == null || request.server().isBlank() ? "?" : request.server();
        Component accept = messages.get(target, "tpa.button.accept")
                .clickEvent(ClickEvent.runCommand("/tpaccept " + name))
                .hoverEvent(HoverEvent.showText(messages.get(target, "tpa.button.accept-hover", "player", name, "server", server)));
        Component deny = messages.get(target, "tpa.button.deny")
                .clickEvent(ClickEvent.runCommand("/tpdeny " + name))
                .hoverEvent(HoverEvent.showText(messages.get(target, "tpa.button.deny-hover", "player", name)));
        Component ignore = messages.get(target, "tpa.button.ignore")
                .clickEvent(ClickEvent.runCommand("/tpaignore " + name))
                .hoverEvent(HoverEvent.showText(messages.get(target, "tpa.button.ignore-hover", "player", name)));
        return messages.prefix().append(accept).append(Component.space()).append(deny).append(Component.space()).append(ignore);
    }

    // ---------- Accepter, refuser ----------

    /** /tpaccept [joueur] : sans pseudo et avec plusieurs demandes, le menu pour choisir. */
    public void accept(Player target, String requesterName) {
        UUID uuid = target.getUniqueId();
        Tasks.async(plugin, target, () -> requests.received(uuid), received -> {
            List<Request> matching = received.stream().filter(request -> requesterName == null
                    || request.requesterName().equalsIgnoreCase(requesterName)).toList();
            if (matching.isEmpty()) {
                messages.send(target, requesterName == null ? "tpa.none" : "tpa.none-from", "player",
                        requesterName == null ? "" : requesterName);
            } else if (requesterName == null && matching.size() > 1) {
                openRequests(target, matching);
            } else {
                accept(target, matching.getFirst());
            }
        }, () -> messages.send(target, "error.generic"));
    }

    /** Le menu des demandes reçues (/tpa list, /tpaccept avec plusieurs demandes). */
    public void openRequests(Player target) {
        UUID uuid = target.getUniqueId();
        Tasks.async(plugin, target, () -> requests.received(uuid), received -> {
            if (received.isEmpty()) {
                messages.send(target, "tpa.none");
            } else {
                openRequests(target, received);
            }
        }, () -> messages.send(target, "error.generic"));
    }

    private void openRequests(Player target, List<Request> received) {
        target.openInventory(new TpaRequestsMenu(this, messages, target, received).getInventory());
    }

    /**
     * Thread principal. Accepter : les règles du voyageur d'ici sont vérifiées AVANT de retirer la demande (combat,
     * cooldown) ; elle revient si le départ échoue.
     */
    void accept(Player target, Request request) {
        boolean targetTravels = request.here();
        if (targetTravels) {
            long fighting = teleports.combatSeconds(target);
            if (fighting > 0) {
                messages.send(target, "tpa.self-combat", "time", String.valueOf(fighting));
                return;
            }
        }
        UUID uuid = target.getUniqueId();
        Tasks.async(plugin, target, () -> {
            long cooldown = targetTravels ? teleports.cooldownSeconds(target) : 0;
            if (cooldown > 0) {
                return new Checked(Optional.empty(), false, cooldown);
            }
            Optional<String> requesterServer = lookup.serverOf(request.requester());
            boolean sameClan = config.clanNoWarmup() && Clans.same(uuid, request.requester());
            requests.remove(uuid, request.requester());
            return new Checked(requesterServer, sameClan, 0);
        }, checked -> {
            if (checked.cooldown() > 0) {
                messages.send(target, "tpa.self-cooldown", "time", String.valueOf(checked.cooldown()));
                return;
            }
            if (reminders != null) {
                reminders.handled(uuid, request.requester());
            }
            if (checked.requesterServer().isEmpty()) {
                messages.send(target, "tpa.requester-gone", "player", request.requesterName());
                return;
            }
            String requesterServer = checked.requesterServer().get();
            if (targetTravels) {
                // /tpahere : c'est le destinataire (ici) qui part chez le demandeur
                Player requesterHere = Bukkit.getPlayer(request.requester());
                if (requesterHere != null && claimRefused(requesterHere.getLocation(), checked.sameClan())) {
                    messages.send(target, "tpa.claimed", "player", request.requesterName());
                    return;
                }
                messages.send(target, "tpa.accepted-target", "player", request.requesterName());
                bus.notify(request.requester(), "tpa.accepted-here", true, "player", target.getName(),
                        "seconds", String.valueOf(teleports.warmupSeconds(target)));
                depart(target, Destination.toPlayer(request.requester(), requesterServer, request.requesterName()),
                        checked.sameClan(), request.requester(), request.requesterName(), request);
                return;
            }
            if (claimRefused(target.getLocation(), checked.sameClan())) {
                messages.send(target, "tpa.claimed", "player", request.requesterName());
                bus.notify(request.requester(), "tpa.claimed-requester", true, "player", target.getName());
                return;
            }
            messages.send(target, "tpa.accepted-target", "player", request.requesterName());
            Player requester = Bukkit.getPlayer(request.requester());
            Destination destination = Destination.toPlayer(uuid, lookup.serverName(), target.getName());
            if (requester != null) {
                messages.send(requester, "tpa.accepted", "player", target.getName(), "seconds",
                        String.valueOf(checked.sameClan() ? 0 : teleports.warmupSeconds(requester)));
                requester.playSound(requester, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.2f);
                depart(requester, destination, checked.sameClan(), uuid, target.getName(), request);
            } else {
                JsonObject data = new JsonObject();
                data.addProperty("traveller", request.requester().toString());
                data.addProperty("target", uuid.toString());
                data.addProperty("target_server", lookup.serverName());
                data.addProperty("label", target.getName());
                data.addProperty("same_clan", checked.sameClan());
                data.addProperty("request", TpaRequests.encode(request));
                bus.publish(TELEPORT, data, () -> {
                    messages.send(target, "network.failed");
                    Tasks.async(plugin, () -> requests.restore(uuid, request), "Demande de tpa non rendue");
                });
            }
        }, () -> messages.send(target, "error.generic"));
    }

    /**
     * Thread principal, sur le serveur du voyageur : le départ (sans annulation au mouvement), vers destinationPlayer.
     * Échec : la demande revient chez celui qui l'a reçue, et les deux joueurs sont prévenus (bouton Réessayer).
     */
    private void depart(Player traveller, Destination destination, boolean sameClan, UUID destinationPlayer, String destinationName,
                        Request request) {
        // Celui qui a reçu la demande : le destinataire du /tpa, ou le voyageur d'un /tpahere
        UUID receiver = request.here() ? traveller.getUniqueId() : destinationPlayer;
        String receiverName = request.here() ? traveller.getName() : destinationName;
        TeleportOptions options = TeleportOptions.defaults().withCancelOnMove(false)
                .onDeparture(() -> {
                    UUID travellerId = traveller.getUniqueId();
                    String travellerName = traveller.getName();
                    String server = lookup.serverName();
                    // Le demandeur paie (vérifié à l'envoi) ; dispensé s'il est ici avec eteressential.tpa.free
                    Player payer = Bukkit.getPlayer(request.requester());
                    double cost = payer != null && payer.hasPermission(FREE) ? 0
                            : request.here() ? config.costTpaHere() : config.costTpa();
                    Tasks.async(plugin, () -> {
                        log.add(travellerId, travellerName, destinationPlayer, destinationName, request.here(), server);
                        if (cost > 0) {
                            EconomyApi.get().ifPresent(economy -> economy.withdraw(request.requester(), cost, "tpa"));
                        }
                    }, "Tpa non noté");
                })
                .onFailed(failure -> {
                    // Le joueur visé est parti : rien à rendre
                    if (failure == Failure.UNREACHABLE) {
                        return;
                    }
                    Tasks.async(plugin, () -> requests.restore(receiver, request), "Demande de tpa non rendue");
                    String reason = failure.name().toLowerCase(Locale.ROOT);
                    if (traveller.getUniqueId().equals(receiver)) {
                        traveller.sendMessage(retry(traveller, "tpa.failed-self", request.requesterName(), reason));
                    } else {
                        bus.notify(receiver, "tpa.failed-other", true, "player", traveller.getName(),
                                "reason", messages.plain(traveller, "tpa.reason." + reason));
                        messages.send(traveller, "tpa.failed-traveller", "player", receiverName);
                    }
                });
        teleports.teleport(traveller, destination, sameClan ? options.withoutWarmup() : options);
    }

    /** « La téléportation a échoué (raison) » avec [Réessayer]. */
    private Component retry(Player player, String key, String requesterName, String reason) {
        Component line = messages.get(player, key, "player", requesterName, "reason", messages.plain(player, "tpa.reason." + reason));
        Component button = messages.get(player, "tpa.button.retry").clickEvent(ClickEvent.runCommand("/tpaccept " + requesterName))
                .hoverEvent(HoverEvent.showText(messages.get(player, "tpa.button.retry-hover")));
        return messages.prefix().append(line).append(Component.space()).append(button);
    }

    /** tpa.claims-clan-only : pas d'arrivée dans le terrain d'un clan si les deux joueurs ne sont pas du même clan. */
    private boolean claimRefused(Location location, boolean sameClan) {
        return config.claimsClanOnly() && !sameClan && Clans.isClaimed(location);
    }

    /** /tpdeny [joueur]. */
    public void deny(Player target, String requesterName) {
        UUID uuid = target.getUniqueId();
        Tasks.async(plugin, target, () -> requests.received(uuid).stream()
                .filter(request -> requesterName == null || request.requesterName().equalsIgnoreCase(requesterName))
                .findFirst()
                .map(request -> {
                    requests.remove(uuid, request.requester());
                    return request;
                }), found -> found.ifPresentOrElse(request -> {
                    if (reminders != null) {
                        reminders.handled(uuid, request.requester());
                    }
                    messages.send(target, "tpa.denied-target", "player", request.requesterName());
                    bus.notify(request.requester(), "tpa.denied", true, "player", target.getName());
                }, () -> messages.send(target, requesterName == null ? "tpa.none" : "tpa.none-from", "player",
                        requesterName == null ? "" : requesterName)),
                () -> messages.send(target, "error.generic"));
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
            if (reminders != null) {
                reminders.forgetSent(uuid);
            }
            messages.send(requester, "tpa.cancelled");
            bus.notify(target.get(), "tpa.cancelled-target", false, "player", requester.getName());
        }, () -> messages.send(requester, "error.generic"));
    }

    // ---------- Réglages et listes ----------

    /** /tptoggle : tout le monde, puis amis et clan seulement, puis personne. /tptoggle here : les /tpahere. */
    public void toggle(Player player, boolean here) {
        UUID uuid = player.getUniqueId();
        Tasks.async(plugin, player, () -> {
            Settings current = settings.get(uuid);
            Settings next = here ? new Settings(current.mode(), !current.hereDisabled(), current.autoClan())
                    : new Settings(current.mode().next(), current.hereDisabled(), current.autoClan());
            settings.set(uuid, next);
            return next;
        }, next -> messages.send(player, here ? (next.hereDisabled() ? "tpa.toggle.here-off" : "tpa.toggle.here-on")
                        : "tpa.toggle." + next.mode().name().toLowerCase(Locale.ROOT)),
                () -> messages.send(player, "error.generic"));
    }

    /** /tpaignore [joueur] : ignorer ses demandes (ou ne plus l'ignorer) ; seul : la liste. */
    public void toggleList(Player player, Kind kind, String name) {
        String prefix = kind == Kind.IGNORE ? "tpa.ignore." : "tpa.auto.";
        if (name == null) {
            UUID uuid = player.getUniqueId();
            Tasks.async(plugin, player, () -> lists.list(uuid, kind), list -> messages.send(player,
                    list.isEmpty() ? prefix + "empty" : prefix + "list", "players",
                    String.join(", ", list.stream().map(TpaLists.Entry::name).toList())), () -> messages.send(player, "error.generic"));
            return;
        }
        if (kind == Kind.AUTO && name.equalsIgnoreCase("clan")) {
            UUID uuid = player.getUniqueId();
            Tasks.async(plugin, player, () -> {
                Settings current = settings.get(uuid);
                Settings next = new Settings(current.mode(), current.hereDisabled(), !current.autoClan());
                settings.set(uuid, next);
                return next.autoClan();
            }, on -> messages.send(player, on ? "tpa.auto.clan-on" : "tpa.auto.clan-off"), () -> messages.send(player, "error.generic"));
            return;
        }
        lookup.findAny(player, name, found -> {
            if (found.isEmpty()) {
                messages.send(player, "player.unknown", "player", name);
                return;
            }
            NetworkPlayer other = found.get();
            if (other.uuid().equals(player.getUniqueId())) {
                messages.send(player, "tpa.self");
                return;
            }
            UUID uuid = player.getUniqueId();
            Tasks.async(plugin, player, () -> lists.toggle(uuid, kind, other.uuid(), other.name()),
                    added -> messages.send(player, prefix + (added ? "added" : "removed"), "player", other.name()),
                    () -> messages.send(player, "error.generic"));
        });
    }

    /** /tpaclan : un /tpahere à chaque membre de son clan connecté (tpa.clan-cooldown-minutes entre deux). */
    public void requestClan(Player requester) {
        UUID uuid = requester.getUniqueId();
        Tasks.async(plugin, requester, () -> {
            List<UUID> members = Clans.members(uuid);
            if (members.isEmpty()) {
                return Optional.<List<String>>empty();
            }
            if (!clanCooldown.tryStart(uuid)) {
                return Optional.of(List.<String>of());
            }
            Set<String> names = new HashSet<>();
            for (UUID member : members) {
                if (!member.equals(uuid)) {
                    EterLib.get().getPlayers().get(member).filter(NetworkPlayer::isOnline).ifPresent(found -> names.add(found.name()));
                }
            }
            return Optional.of(List.copyOf(names));
        }, result -> {
            if (result.isEmpty()) {
                messages.send(requester, "tpa.clan.none");
            } else if (result.get().isEmpty()) {
                messages.send(requester, "tpa.clan.cooldown-or-alone");
            } else {
                messages.send(requester, "tpa.clan.sent", "count", String.valueOf(result.get().size()));
                result.get().forEach(name -> request(requester, name, true, false));
            }
        }, () -> messages.send(requester, "error.generic"));
    }

    /** /tpalog <joueur> (staff) : ses 15 dernières téléportations. */
    public void showLog(Player staff, String name) {
        lookup.findAny(staff, name, found -> {
            if (found.isEmpty()) {
                messages.send(staff, "player.unknown", "player", name);
                return;
            }
            NetworkPlayer player = found.get();
            Tasks.async(plugin, staff, () -> log.of(player.uuid(), 15), entries -> {
                messages.send(staff, entries.isEmpty() ? "tpa.log.empty" : "tpa.log.header", "player", player.name());
                for (TpaLog.Entry entry : entries) {
                    messages.send(staff, entry.here() ? "tpa.log.line-here" : "tpa.log.line", "traveller", entry.traveller(),
                            "destination", entry.destination(), "server", entry.server(),
                            "date", java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm")
                                    .withZone(java.time.ZoneId.systemDefault()).format(java.time.Instant.ofEpochMilli(entry.createdAt())));
                }
            }, () -> messages.send(staff, "error.generic"));
        });
    }

    // ---------- Anti-abus ----------

    /** Un joueur qui envoie des demandes à beaucoup de joueurs différents en peu de temps (piège à tpa) : le staff. */
    private void watchForTraps(UUID requester, String requesterName, UUID target) {
        if (config.alertTargets() <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        long window = config.alertWindow().toMillis();
        Deque<Map.Entry<UUID, Long>> recent = recentTargets.computeIfAbsent(requester, uuid -> new ArrayDeque<>());
        int distinct;
        synchronized (recent) {
            recent.addLast(Map.entry(target, now));
            while (!recent.isEmpty() && now - recent.peekFirst().getValue() > window) {
                recent.pollFirst();
            }
            distinct = (int) recent.stream().map(Map.Entry::getKey).distinct().count();
        }
        Long last = alertedAt.get(requester);
        if (distinct >= config.alertTargets() && (last == null || now - last > window)) {
            alertedAt.put(requester, now);
            Bukkit.getScheduler().runTask(plugin, () -> {
                showAlert(requesterName, String.valueOf(distinct));
                JsonObject data = new JsonObject();
                data.addProperty("player", requesterName);
                data.addProperty("count", String.valueOf(distinct));
                bus.publish(ALERT, data);
            });
        }
    }

    private void showAlert(String player, String count) {
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission(ALERTS)) {
                messages.send(staff, "tpa.trap-alert", "player", player, "count", count,
                        "minutes", String.valueOf(config.alertWindow().toMinutes()));
            }
        }
    }

    // ---------- Coût ----------

    /** Le prix d'un tpa (tpa.cost), 0 avec eteressential.tpa.free. */
    private double cost(Player player, boolean here) {
        double cost = here ? config.costTpaHere() : config.costTpa();
        return cost <= 0 || player.hasPermission(FREE) ? 0 : cost;
    }

    private static String format(double amount) {
        return EconomyApi.get().map(economy -> economy.format(amount)).orElse(String.valueOf(amount));
    }
}
