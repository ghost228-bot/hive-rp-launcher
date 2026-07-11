package ru.hivegrief.auroracore;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class AuroraCore extends JavaPlugin {

    private Storage storage;
    private Menus menus;
    private CreationManager creation;
    private RpName rpName;
    private PreviewNpc preview; // null, если Citizens не установлен

    @Override
    public void onEnable() {
        saveDefaultConfig();
        storage = new Storage(this);
        menus = new Menus();
        rpName = new RpName(this);
        creation = new CreationManager(this);
        if (Bukkit.getPluginManager().getPlugin("Citizens") != null) {
            preview = new PreviewNpc(this);
            getLogger().info("Citizens найден — манекен предпросмотра включён.");
        } else {
            getLogger().warning("Citizens не найден — предпросмотр внешности отключён (поставь Citizens 2.0.28 для 1.16.5).");
        }

        Bukkit.getPluginManager().registerEvents(new Listeners(this), this);
        Bukkit.getPluginManager().registerEvents(rpName, this);

        Commands cmds = new Commands(this);
        getCommand("aurora").setExecutor(cmds);
        getCommand("passport").setExecutor(cmds);

        // на случай /reload — применяем RP-имена онлайн-игрокам
        for (Player p : Bukkit.getOnlinePlayers()) {
            CharacterData d = storage.load(p.getUniqueId());
            if (d != null) rpName.apply(p, d);
        }
        getLogger().info("AuroraCore включён. podium=" + (loc("podium") != null) + " preview=" + (loc("preview") != null)
                + " spawn=" + (loc("spawn") != null) + " camera=" + (loc("camera") != null));
    }

    @Override
    public void onDisable() {
        if (rpName != null) rpName.removeAll();
        if (preview != null) preview.hideAll();
    }

    public PreviewNpc preview() { return preview; }
    public Storage storage() { return storage; }
    public Menus menus() { return menus; }
    public CreationManager creation() { return creation; }
    public RpName rpName() { return rpName; }

    public Location loc(String key) {
        String s = getConfig().getString(key, "");
        if (s == null || s.isEmpty()) return null;
        try {
            String[] a = s.split(";");
            World w = Bukkit.getWorld(a[0]);
            if (w == null) return null;
            return new Location(w, Double.parseDouble(a[1]), Double.parseDouble(a[2]),
                    Double.parseDouble(a[3]), Float.parseFloat(a[4]), Float.parseFloat(a[5]));
        } catch (Exception e) {
            return null;
        }
    }

    public void setLoc(String key, Location l) {
        getConfig().set(key, l.getWorld().getName() + ";" + l.getX() + ";" + l.getY() + ";" + l.getZ()
                + ";" + l.getYaw() + ";" + l.getPitch());
        saveConfig();
    }
}
