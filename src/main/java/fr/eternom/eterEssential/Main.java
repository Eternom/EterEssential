package fr.eternom.eterEssential;

import fr.eternom.eterEssential.listeners.Commands;
import fr.eternom.eterEssential.listeners.Events;
import fr.eternom.eterEssential.module.back.BackStore;
import fr.eternom.eterEssential.module.network.NetworkBus;
import fr.eternom.eterEssential.module.network.PlayerLookup;
import fr.eternom.eterEssential.module.rtp.RtpCooldown;
import fr.eternom.eterEssential.module.rtp.RtpService;
import fr.eternom.eterEssential.module.rtp.RtpWorld;
import fr.eternom.eterEssential.module.tpa.TpaRequests;
import fr.eternom.eterEssential.module.tpa.TpaService;
import fr.eternom.eterEssential.module.tpa.TpaSettings;
import fr.eternom.eterLib.EterLib;
import fr.eternom.eterLib.helper.cache.RedisCache;
import fr.eternom.eterLib.helper.message.Messages;
import fr.eternom.eterLib.helper.sql.Database;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;

public final class Main extends JavaPlugin {

    /** Version minimale d'EterLib : EterTeleportEvent, teleportNow et OnlineNames arrivent en 1.5.0. */
    private static final String REQUIRED_ETERLIB = "1.5.0";

    /** Préfixe des tables d'EterEssential dans la base commune : eteressential_players... */
    private static final String TABLE_PREFIX = "eteressential_";

    private Messages messages;
    private NetworkBus bus;
    private PlayerLookup lookup;
    private TpaService tpa;
    private BackStore back;
    private RtpService rtp;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        // En premier : vérifie la version d'EterLib (un EterLib < 1.3.0 n'a pas requireVersion, d'où le catch)
        try {
            if (!EterLib.requireVersion(this, REQUIRED_ETERLIB)) {
                return;
            }
        } catch (LinkageError tooOld) {
            getLogger().severe("EterLib " + REQUIRED_ETERLIB + " ou plus récent est nécessaire.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        EterLib lib = EterLib.get();
        messages = lib.messages(this, "en_us", "fr_fr");
        Database database = lib.database(TABLE_PREFIX);
        RedisCache redis = lib.getRedis();

        bus = new NetworkBus(this, lib.getMessenger(), messages, lib.getServerName());
        bus.start();
        lookup = new PlayerLookup(this, lib.getPlayers(), messages, lib.getServerName(), bus.isNetworked());

        TpaRequests requests = new TpaRequests(redis, Duration.ofSeconds(Math.max(10, getConfig().getInt("tpa.expire-seconds", 60))));
        tpa = new TpaService(this, lib.getTeleports(), bus, lookup, requests, new TpaSettings(database), messages);
        back = new BackStore(database, redis);
        RtpCooldown rtpCooldown = new RtpCooldown(database, redis, Duration.ofSeconds(Math.max(0, getConfig().getInt("rtp.cooldown", 1800))));
        rtp = new RtpService(this, rtpCooldown, lib.getTeleports(), messages, lib.getServerName(),
                RtpWorld.load(getConfig().getConfigurationSection("rtp.worlds"), getLogger()), getConfig().getInt("rtp.attempts", 10));

        new Commands(this);
        new Events(this);
        getLogger().info(bus.isNetworked()
                ? "Commandes reliées à tout le réseau (Redis)"
                : "Redis désactivé : tpa, /tp et messages limités à ce serveur");
    }

    public Messages getMessages() {
        return messages;
    }

    public NetworkBus getBus() {
        return bus;
    }

    public PlayerLookup getLookup() {
        return lookup;
    }

    public TpaService getTpa() {
        return tpa;
    }

    public BackStore getBack() {
        return back;
    }

    public RtpService getRtp() {
        return rtp;
    }
}
