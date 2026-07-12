package ru.hivegrief.auroracore;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.*;

public class Listeners implements Listener {

    private final AuroraCore pl;

    public Listeners(AuroraCore pl) {
        this.pl = pl;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        CharacterData d = pl.storage().load(p.getUniqueId());
        if (d == null) {
            // первый вход — кинематографичное создание персонажа
            Bukkit.getScheduler().runTaskLater(pl, () -> {
                if (p.isOnline()) pl.creation().start(p);
            }, 30L);
        } else {
            pl.rpName().apply(p, d);
            Bukkit.getScheduler().runTaskLater(pl, () -> {
                if (p.isOnline()) {
                    Util.title(p, Util.ORANGE_B + "HIVE RP", Util.GRAY + "С возвращением, " + Util.WHITE + d.rpName(), 10, 50, 15);
                }
            }, 20L);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        pl.creation().abort(e.getPlayer()); // внутри же убирается манекен
        pl.rpName().remove(e.getPlayer());
    }

    /* --- заморозка при создании: стоим на месте, крутить головой можно --- */
    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (!pl.creation().in(e.getPlayer())) return;
        Location from = e.getFrom();
        Location to = e.getTo();
        if (to == null) return;
        if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
            Location fix = from.clone();
            fix.setYaw(to.getYaw());
            fix.setPitch(to.getPitch());
            e.setTo(fix);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player && pl.creation().in((Player) e.getEntity())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player && pl.creation().in((Player) e.getEntity())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (pl.creation().in(e.getPlayer())) e.setCancelled(true);
    }

    /* --- перехват чата: ввод имени/возраста, никто в чате не видит --- */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        if (!pl.creation().in(p)) return;
        e.setCancelled(true);
        String msg = e.getMessage();
        Bukkit.getScheduler().runTask(pl, () -> {
            if (p.isOnline()) pl.creation().chat(p, msg);
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        if (pl.creation().in(e.getPlayer())) {
            e.setCancelled(true);
            Util.action(e.getPlayer(), "\u00A7cСначала завершите создание персонажа");
        }
    }

    /* --- хотбар выбора внешности --- */
    @EventHandler
    public void onInteract(org.bukkit.event.player.PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!pl.creation().in(p)) return;
        e.setCancelled(true);
        if (e.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_AIR
                || e.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            pl.creation().hotbarClick(p, p.getInventory().getHeldItemSlot());
        }
    }

    /* --- меню --- */
    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();

        Object holder = e.getView().getTopInventory().getHolder();
        if (holder instanceof Menus.Holder) {
            e.setCancelled(true);
            if (e.getClickedInventory() == e.getView().getTopInventory() && e.getCurrentItem() != null) {
                pl.creation().click(p, (Menus.Holder) holder, e.getRawSlot());
            }
            return;
        }
        if (pl.creation().in(p)) e.setCancelled(true);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getWhoClicked() instanceof Player && pl.creation().in((Player) e.getWhoClicked())) e.setCancelled(true);
    }

    /* --- нельзя сбежать из меню (Esc → снова открываем) --- */
    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player)) return;
        Player p = (Player) e.getPlayer();
        CreationManager.Session s = pl.creation().get(p);
        if (s == null) return;

        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (!p.isOnline()) return;
            CreationManager.Session ss = pl.creation().get(p);
            if (ss == null) return;
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof Menus.Holder) return;
            switch (ss.stage) {
                case GENDER:
                    pl.menus().openGender(p);
                    break;
                case SKIN:
                    break; // выбор внешности идёт хотбаром, меню нет
                case CONFIRM:
                    pl.menus().openConfirm(pl, p, ss.draft);
                    break;
                default:
                    break;
            }
        }, 3L);
    }
}
