package ru.hivegrief.auroracore;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Menus {

    public static final int[] SKIN_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
    public static final int SLOT_PREV = 47;
    public static final int SLOT_DONE = 49;
    public static final int SLOT_NEXT = 51;

    public static class Holder implements InventoryHolder {
        public final String id;
        public final int page;
        private Inventory inv;

        public Holder(String id, int page) {
            this.id = id;
            this.page = page;
        }

        public void setInv(Inventory inv) { this.inv = inv; }

        @Override
        public Inventory getInventory() { return inv; }
    }

    private static ItemStack item(Material m, String name, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(name);
        if (lore.length > 0) meta.setLore(Arrays.asList(lore));
        it.setItemMeta(meta);
        return it;
    }

    private static ItemStack skull(String owner, String name, String... lore) {
        ItemStack it = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) it.getItemMeta();
        try {
            meta.setOwner(owner);
        } catch (Exception ignored) {}
        meta.setDisplayName(name);
        if (lore.length > 0) meta.setLore(Arrays.asList(lore));
        it.setItemMeta(meta);
        return it;
    }

    private static void frame(Inventory inv) {
        ItemStack glass = item(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) inv.setItem(i, glass);
        }
    }

    public void openGender(Player p) {
        Holder h = new Holder("gender", 0);
        Inventory inv = Bukkit.createInventory(h, 27, Util.ORANGE_B + "Создание персонажа " + Util.DARK + "\u2022 " + Util.GRAY + "Пол");
        h.setInv(inv);
        inv.setItem(11, item(Material.LIGHT_BLUE_WOOL, "\u00A7b\u00A7lМужской", Util.GRAY + "Нажмите, чтобы выбрать"));
        inv.setItem(15, item(Material.PINK_WOOL, "\u00A7d\u00A7lЖенский", Util.GRAY + "Нажмите, чтобы выбрать"));
        frame(inv);
        p.openInventory(inv);
    }

    /** Список скинов из конфига: "Название|НикИсточник" */
    public List<String[]> skins(AuroraCore pl, String gender) {
        List<String[]> out = new ArrayList<>();
        for (String s : pl.getConfig().getStringList("skins." + ("female".equals(gender) ? "female" : "male"))) {
            String[] a = s.split("\\|", 2);
            if (a.length == 2) out.add(new String[]{a[0].trim(), a[1].trim()});
        }
        return out;
    }

    public void openSkins(AuroraCore pl, Player p, CharacterData d, int page) {
        List<String[]> list = skins(pl, d.gender);
        int perPage = SKIN_SLOTS.length;
        int pages = Math.max(1, (int) Math.ceil(list.size() / (double) perPage));
        if (page < 0) page = 0;
        if (page >= pages) page = pages - 1;

        Holder h = new Holder("skins", page);
        Inventory inv = Bukkit.createInventory(h, 54, Util.ORANGE_B + "Внешность " + Util.DARK + "\u2022 " + Util.GRAY + (page + 1) + "/" + pages);
        h.setInv(inv);

        int start = page * perPage;
        for (int i = 0; i < perPage; i++) {
            int idx = start + i;
            if (idx >= list.size()) break;
            String[] e = list.get(idx);
            boolean sel = e[1].equals(d.skin);
            ItemStack it = skull(e[1], (sel ? "\u00A7a\u2714 " : "") + Util.ORANGE + e[0],
                    sel ? "\u00A7aВыбрано \u2014 смотри на манекен" : Util.GRAY + "Нажмите \u2014 манекен переоденется");
            if (sel) {
                ItemMeta m = it.getItemMeta();
                m.addEnchant(org.bukkit.enchantments.Enchantment.DURABILITY, 1, true);
                m.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
                it.setItemMeta(m);
            }
            inv.setItem(SKIN_SLOTS[i], it);
        }
        if (page > 0) inv.setItem(SLOT_PREV, item(Material.ARROW, Util.GRAY + "\u2190 Назад"));
        inv.setItem(SLOT_DONE, item(Material.LIME_WOOL, "\u00A7a\u00A7lГотово", Util.GRAY + "Подтвердить выбранную внешность"));
        if (page < pages - 1) inv.setItem(SLOT_NEXT, item(Material.ARROW, Util.GRAY + "Вперёд \u2192"));
        frame(inv);
        p.openInventory(inv);
    }

    public void openConfirm(AuroraCore pl, Player p, CharacterData d) {
        Holder h = new Holder("confirm", 0);
        Inventory inv = Bukkit.createInventory(h, 27, Util.ORANGE_B + "Анкета " + Util.DARK + "\u2022 " + Util.GRAY + "Подтверждение");
        h.setInv(inv);
        inv.setItem(13, item(Material.PAPER, Util.ORANGE_B + "\u2726 Анкета персонажа",
                Util.DARK + "\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500",
                Util.GRAY + "Имя: " + Util.WHITE + d.rpName(),
                Util.GRAY + "Пол: " + Util.WHITE + Util.genderRu(d.gender),
                Util.GRAY + "Возраст: " + Util.WHITE + d.age,
                Util.GRAY + "Внешность: " + Util.WHITE + (d.skin == null ? "-" : d.skin),
                Util.DARK + "\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500"));
        inv.setItem(11, item(Material.LIME_WOOL, "\u00A7a\u00A7lПодтвердить", Util.GRAY + "Начать жизнь в городе"));
        inv.setItem(15, item(Material.RED_WOOL, "\u00A7c\u00A7lЗаполнить заново", Util.GRAY + "Вернуться к выбору пола"));
        frame(inv);
        p.openInventory(inv);
    }
}
