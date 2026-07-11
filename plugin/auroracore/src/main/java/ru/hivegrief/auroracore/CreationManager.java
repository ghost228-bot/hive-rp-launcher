package ru.hivegrief.auroracore;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CreationManager {

    public enum Stage { INTRO, GENDER, NAME, AGE, SKIN, CONFIRM }

    public static class Session {
        public Stage stage = Stage.INTRO;
        public CharacterData draft = new CharacterData();
        public int page = 0;
    }

    private final AuroraCore pl;
    private final Map<UUID, Session> sessions = new HashMap<>();

    public CreationManager(AuroraCore pl) {
        this.pl = pl;
    }

    public boolean in(Player p) {
        return sessions.containsKey(p.getUniqueId());
    }

    public Session get(Player p) {
        return sessions.get(p.getUniqueId());
    }

    public void abort(Player p) {
        sessions.remove(p.getUniqueId());
        if (pl.preview() != null) pl.preview().hide(p);
    }

    /* ---------- старт: чёрный экран -> (камера) -> студия ---------- */

    public void start(Player p) {
        Session s = new Session();
        s.draft.nick = p.getName();
        sessions.put(p.getUniqueId(), s);

        p.getInventory().clear();
        p.setGameMode(GameMode.ADVENTURE);
        p.setInvulnerable(true);
        p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 80, 0, false, false));
        Util.sound(p, Sound.BLOCK_PORTAL_AMBIENT, 0.6f);

        Location cam = pl.loc("camera");
        Location podium = pl.loc("podium");
        if (podium == null) pl.getLogger().warning("Точка podium не задана! /aurora setpodium в студии.");

        long toStudio = 20L;
        if (cam != null) {
            // короткий кинематографичный кадр города
            p.setAllowFlight(true);
            p.setFlying(true);
            p.teleport(cam);
            toStudio = 80L;
        }

        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (!p.isOnline() || !in(p)) return;
            Util.title(p, Util.ORANGE_B + "HIVE RP", Util.GRAY + "Добро пожаловать в город", 15, 50, 15);
            Util.sound(p, Sound.ENTITY_PLAYER_LEVELUP, 1.2f);
        }, 20L);

        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (!p.isOnline() || !in(p)) return;
            p.setFlying(false);
            p.setAllowFlight(false);
            if (podium != null) p.teleport(podium);
            Util.title(p, Util.WHITE + "Создание персонажа", Util.GRAY + "Ответьте на несколько вопросов", 10, 40, 10);
            Util.sound(p, Sound.BLOCK_NOTE_BLOCK_PLING, 1.4f);
        }, toStudio);

        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (!p.isOnline() || !in(p)) return;
            Session ss = get(p);
            if (ss == null) return;
            ss.stage = Stage.GENDER;
            pl.menus().openGender(p);
        }, toStudio + 50L);
    }

    /* ---------- клики по меню ---------- */

    public void click(Player p, Menus.Holder h, int slot) {
        Session s = get(p);
        if (s == null) return;

        if ("gender".equals(h.id) && s.stage == Stage.GENDER) {
            if (slot == 11) s.draft.gender = "male";
            else if (slot == 15) s.draft.gender = "female";
            else return;
            Util.sound(p, Sound.UI_BUTTON_CLICK, 1f);
            s.stage = Stage.NAME;
            p.closeInventory();
            promptName(p);
            return;
        }

        if ("skins".equals(h.id) && s.stage == Stage.SKIN) {
            if (slot == Menus.SLOT_PREV) {
                s.page = Math.max(0, h.page - 1);
                pl.menus().openSkins(pl, p, s.draft, s.page);
                Util.sound(p, Sound.ITEM_BOOK_PAGE_TURN, 1f);
                return;
            }
            if (slot == Menus.SLOT_NEXT) {
                s.page = h.page + 1;
                pl.menus().openSkins(pl, p, s.draft, s.page);
                Util.sound(p, Sound.ITEM_BOOK_PAGE_TURN, 1f);
                return;
            }
            if (slot == Menus.SLOT_DONE) {
                List<String[]> list = pl.menus().skins(pl, s.draft.gender);
                if (s.draft.skin == null && !list.isEmpty()) s.draft.skin = list.get(0)[1];
                Util.sound(p, Sound.UI_BUTTON_CLICK, 1.2f);
                s.stage = Stage.CONFIRM;
                pl.menus().openConfirm(pl, p, s.draft);
                return;
            }
            int idxInPage = -1;
            for (int i = 0; i < Menus.SKIN_SLOTS.length; i++) {
                if (Menus.SKIN_SLOTS[i] == slot) { idxInPage = i; break; }
            }
            if (idxInPage < 0) return;
            List<String[]> list = pl.menus().skins(pl, s.draft.gender);
            int idx = h.page * Menus.SKIN_SLOTS.length + idxInPage;
            if (idx >= list.size()) return;

            String[] e = list.get(idx);
            s.draft.skin = e[1];
            if (pl.preview() != null) pl.preview().show(p, e[1]); // манекен переодевается
            Util.sound(p, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.3f);
            Util.action(p, Util.ORANGE + "Внешность: " + Util.WHITE + e[0]);
            pl.menus().openSkins(pl, p, s.draft, h.page); // перерисовать с галочкой
            return;
        }

        if ("confirm".equals(h.id) && s.stage == Stage.CONFIRM) {
            if (slot == 11) {
                confirm(p, s);
            } else if (slot == 15) {
                Util.sound(p, Sound.UI_BUTTON_CLICK, 0.8f);
                s.draft = new CharacterData();
                s.draft.nick = p.getName();
                s.page = 0;
                s.stage = Stage.GENDER;
                pl.menus().openGender(p);
            }
        }
    }

    /* ---------- ввод в чат ---------- */

    public void chat(Player p, String msg) {
        Session s = get(p);
        if (s == null) return;

        if (s.stage == Stage.NAME) {
            String[] parts = msg.trim().split("\\s+");
            if (parts.length != 2 || !parts[0].matches("[A-Za-z]{2,16}") || !parts[1].matches("[A-Za-z]{2,16}")) {
                Util.sound(p, Sound.ENTITY_VILLAGER_NO, 1f);
                Util.action(p, "\u00A7cНеверный формат. Пример: \u00A7fIvan Petrov \u00A7c(латиницей)");
                return;
            }
            s.draft.firstName = cap(parts[0]);
            s.draft.lastName = cap(parts[1]);
            Util.sound(p, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f);
            s.stage = Stage.AGE;
            promptAge(p);
            return;
        }

        if (s.stage == Stage.AGE) {
            int min = pl.getConfig().getInt("age-min", 16);
            int max = pl.getConfig().getInt("age-max", 80);
            int age;
            try {
                age = Integer.parseInt(msg.trim());
            } catch (NumberFormatException e) {
                Util.sound(p, Sound.ENTITY_VILLAGER_NO, 1f);
                Util.action(p, "\u00A7cНапишите число от " + min + " до " + max);
                return;
            }
            if (age < min || age > max) {
                Util.sound(p, Sound.ENTITY_VILLAGER_NO, 1f);
                Util.action(p, "\u00A7cВозраст должен быть от " + min + " до " + max);
                return;
            }
            s.draft.age = age;
            Util.sound(p, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f);
            s.stage = Stage.SKIN;
            Util.title(p, Util.WHITE + "Внешность", Util.GRAY + "Выберите внешность персонажа", 10, 40, 10);
            // манекен появляется сразу с первой внешностью каталога
            List<String[]> list = pl.menus().skins(pl, s.draft.gender);
            if (!list.isEmpty()) {
                s.draft.skin = list.get(0)[1];
                if (pl.preview() != null) pl.preview().show(p, s.draft.skin);
            }
            pl.menus().openSkins(pl, p, s.draft, 0);
        }
    }

    private void promptName(Player p) {
        Util.title(p, Util.WHITE + "Имя и фамилия", Util.GRAY + "Напишите в чат: " + Util.ORANGE + "Имя Фамилия", 10, 100, 10);
        p.sendMessage(Util.DARK + "\u258E " + Util.GRAY + "Напишите в чат имя и фамилию персонажа латиницей.");
        p.sendMessage(Util.DARK + "\u258E " + Util.GRAY + "Пример: " + Util.ORANGE + "Ivan Petrov");
        Util.sound(p, Sound.BLOCK_NOTE_BLOCK_PLING, 1.2f);
    }

    private void promptAge(Player p) {
        int min = pl.getConfig().getInt("age-min", 16);
        int max = pl.getConfig().getInt("age-max", 80);
        Util.title(p, Util.WHITE + "Возраст", Util.GRAY + "Напишите возраст в чат (" + min + "\u2013" + max + ")", 10, 100, 10);
        p.sendMessage(Util.DARK + "\u258E " + Util.GRAY + "Напишите в чат возраст персонажа. Пример: " + Util.ORANGE + "21");
    }

    /* ---------- завершение ---------- */

    private void confirm(Player p, Session s) {
        s.draft.created = System.currentTimeMillis();
        pl.storage().save(p.getUniqueId(), s.draft);
        CharacterData d = s.draft;
        sessions.remove(p.getUniqueId());
        if (pl.preview() != null) pl.preview().hide(p);

        p.closeInventory();
        p.setInvulnerable(false);
        p.removePotionEffect(PotionEffectType.BLINDNESS);

        if (d.skin != null) applySkin(p, d.skin); // теперь скин надевается на самого игрока

        Location spawn = pl.loc("spawn");
        if (spawn != null) p.teleport(spawn);

        Util.givePassport(pl, p, d);
        pl.rpName().apply(p, d);

        Util.title(p, Util.ORANGE_B + "HIVE RP", Util.GRAY + "Добро пожаловать, " + Util.WHITE + d.rpName(), 15, 60, 20);
        Util.sound(p, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f);
        p.sendMessage(Util.DARK + "\u258E " + Util.GRAY + "Персонаж создан. Паспорт выдан \u2014 команда " + Util.ORANGE + "/passport");
    }

    private void applySkin(Player p, String skin) {
        // SkinsRestorer (softdepend). Разные версии: пробуем оба варианта команды.
        if (Bukkit.getPluginManager().getPlugin("SkinsRestorer") != null) {
            boolean ok = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "skin set " + skin + " " + p.getName());
            if (!ok) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "sr set " + p.getName() + " " + skin);
        } else {
            pl.getLogger().warning("SkinsRestorer не установлен — скин не применён (" + skin + " для " + p.getName() + ")");
        }
    }

    private static String cap(String s) {
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
    }
}
