package ru.hivegrief.auroracore;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class Commands implements CommandExecutor {

    private final AuroraCore pl;

    public Commands(AuroraCore pl) {
        this.pl = pl;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("passport")) {
            if (!(sender instanceof Player)) return true;
            Player p = (Player) sender;
            CharacterData d = pl.storage().load(p.getUniqueId());
            if (d == null) {
                p.sendMessage("\u00A7cПерсонаж ещё не создан.");
            } else {
                Util.passportCard(p, d);
            }
            return true;
        }

        // /aurora (алиас /char)
        if (!sender.hasPermission("aurora.admin")) {
            sender.sendMessage("\u00A7cНедостаточно прав.");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(Util.ORANGE + "AuroraCore" + Util.GRAY + " \u2014 /aurora setpodium | setpreview | setspawn | setcamera | reset <ник> | reload");
            return true;
        }
        String sub = args[0].toLowerCase();

        switch (sub) {
            case "setpodium":
            case "setpreview":
            case "setcamera":
            case "setspawn": {
                if (!(sender instanceof Player)) {
                    sender.sendMessage("\u00A7cТолько в игре.");
                    return true;
                }
                Player p = (Player) sender;
                String key = sub.substring(3); // setpodium -> podium и т.д.
                pl.setLoc(key, p.getLocation());
                p.sendMessage(Util.ORANGE + "AuroraCore " + Util.GRAY + "Точка \u00A7f" + key + Util.GRAY + " сохранена: "
                        + Util.WHITE + p.getLocation().getBlockX() + " " + p.getLocation().getBlockY() + " " + p.getLocation().getBlockZ());
                return true;
            }
            case "reload": {
                pl.reloadConfig();
                sender.sendMessage(Util.ORANGE + "AuroraCore " + Util.GRAY + "Конфиг перезагружен.");
                return true;
            }
            case "reset": {
                if (args.length < 2) {
                    sender.sendMessage("\u00A7cИспользование: /aurora reset <ник>");
                    return true;
                }
                OfflinePlayer op = Bukkit.getOfflinePlayer(args[1]);
                boolean deleted = pl.storage().delete(op.getUniqueId());
                Player online = Bukkit.getPlayerExact(args[1]);
                if (online != null) {
                    pl.rpName().remove(online);
                    pl.creation().abort(online);
                    pl.creation().start(online);
                }
                sender.sendMessage(Util.ORANGE + "AuroraCore " + Util.GRAY + "Персонаж \u00A7f" + args[1] + Util.GRAY
                        + (deleted ? " сброшен." : " не найден (файл отсутствовал)." )
                        + (online != null ? " Создание запущено заново." : ""));
                return true;
            }
            default:
                sender.sendMessage("\u00A7cНеизвестная подкоманда.");
                return true;
        }
    }
}
