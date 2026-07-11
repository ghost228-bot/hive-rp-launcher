package ru.hivegrief.auroracore;

import org.bukkit.Bukkit;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RpName implements Listener {

    private final AuroraCore pl;
    private final Map<UUID, UUID> stands = new HashMap<>();

    public RpName(AuroraCore pl) {
        this.pl = pl;
    }

    private String mode() {
        return pl.getConfig().getString("nametag-mode", "hologram");
    }

    public void apply(Player p, CharacterData d) {
        String rp = d.rpName();
        p.setDisplayName(rp);
        p.setPlayerListName(Util.ORANGE + rp);

        String mode = mode();
        if ("off".equalsIgnoreCase(mode)) return;

        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        String tn = teamName(p);
        Team t = sb.getTeam(tn);
        if (t == null) t = sb.registerNewTeam(tn);
        if (!t.hasEntry(p.getName())) t.addEntry(p.getName());

        if ("prefix".equalsIgnoreCase(mode)) {
            String pref = Util.ORANGE + rp + " " + Util.DARK + "| " + Util.GRAY;
            if (pref.length() > 64) pref = pref.substring(0, 64);
            t.setPrefix(pref);
        } else {
            t.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
            mount(p, rp);
        }
    }

    private void mount(Player p, String rp) {
        unmount(p);
        ArmorStand as = (ArmorStand) p.getWorld().spawnEntity(p.getLocation(), EntityType.ARMOR_STAND);
        as.setVisible(false);
        as.setMarker(true);
        as.setSmall(true);
        as.setGravity(false);
        as.setInvulnerable(true);
        as.setCustomName(Util.ORANGE + rp);
        as.setCustomNameVisible(true);
        p.addPassenger(as);
        stands.put(p.getUniqueId(), as.getUniqueId());
    }

    private void unmount(Player p) {
        UUID id = stands.remove(p.getUniqueId());
        if (id == null) return;
        Entity e = Bukkit.getEntity(id);
        if (e != null) e.remove();
    }

    public void remove(Player p) {
        unmount(p);
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        Team t = sb.getTeam(teamName(p));
        if (t != null) t.unregister();
    }

    public void removeAll() {
        for (Player p : Bukkit.getOnlinePlayers()) unmount(p);
    }

    private void remountLater(Player p) {
        if (!"hologram".equalsIgnoreCase(mode())) return;
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (!p.isOnline()) return;
            CharacterData d = pl.storage().load(p.getUniqueId());
            if (d != null && !pl.creation().in(p)) mount(p, d.rpName());
        }, 5L);
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent e) {
        if (stands.containsKey(e.getPlayer().getUniqueId())) remountLater(e.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        remountLater(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent e) {
        if (!pl.getConfig().getBoolean("chat-format", true)) return;
        CharacterData d = pl.storage().load(e.getPlayer().getUniqueId());
        if (d == null) return;
        e.setFormat(Util.ORANGE + d.rpName() + " " + Util.DARK + "\u00BB " + Util.WHITE + "%2$s");
    }

    private static String teamName(Player p) {
        String n = "ac_" + p.getName();
        return n.length() > 16 ? n.substring(0, 16) : n;
    }
}
