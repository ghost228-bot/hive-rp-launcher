package ru.hivegrief.auroracore;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

public final class Util {

    public static final String ORANGE = net.md_5.bungee.api.ChatColor.of("#FF7A00").toString();
    public static final String ORANGE_B = ORANGE + org.bukkit.ChatColor.BOLD;
    public static final String GRAY = "\u00A77";
    public static final String DARK = "\u00A78";
    public static final String WHITE = "\u00A7f";

    private Util() {}

    public static void title(Player p, String t, String s, int in, int stay, int out) {
        p.sendTitle(t == null ? "" : t, s == null ? "" : s, in, stay, out);
    }

    public static void action(Player p, String msg) {
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(msg));
    }

    public static void sound(Player p, Sound s, float pitch) {
        p.playSound(p.getLocation(), s, 1f, pitch);
    }

    public static String genderRu(String g) {
        return "female".equals(g) ? "Женский" : "Мужской";
    }

    public static String date(long ts) {
        return new SimpleDateFormat("dd.MM.yyyy").format(new Date(ts));
    }

    public static void givePassport(AuroraCore pl, Player p, CharacterData d) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(ORANGE_B + "\u2726 Паспорт гражданина");
        m.setLore(Arrays.asList(
                DARK + "\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500",
                GRAY + "Имя: " + WHITE + d.rpName(),
                GRAY + "Пол: " + WHITE + genderRu(d.gender),
                GRAY + "Возраст: " + WHITE + d.age,
                GRAY + "Выдан: " + WHITE + date(d.created),
                DARK + "\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500",
                DARK + "HIVE RP \u2022 AURORA"
        ));
        m.getPersistentDataContainer().set(new NamespacedKey(pl, "passport"), PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        p.getInventory().addItem(it);
    }

    public static void passportCard(Player p, CharacterData d) {
        p.sendMessage("");
        p.sendMessage(DARK + "\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550 " + ORANGE_B + "ПАСПОРТ " + DARK + "\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550");
        p.sendMessage(GRAY + " Имя: " + WHITE + d.rpName());
        p.sendMessage(GRAY + " Пол: " + WHITE + genderRu(d.gender));
        p.sendMessage(GRAY + " Возраст: " + WHITE + d.age);
        p.sendMessage(GRAY + " Аккаунт: " + WHITE + d.nick);
        p.sendMessage(GRAY + " Выдан: " + WHITE + date(d.created) + GRAY + " \u2022 HIVE RP");
        p.sendMessage(DARK + "\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550");
        p.sendMessage("");
    }
}
