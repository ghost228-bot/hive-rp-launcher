package ru.hivegrief.launcherguard;

import com.sun.net.httpserver.HttpServer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LauncherGuard — пускает на сервер только игроков, зашедших через лаунчер.
 * Лаунчер перед запуском игры дергает http://IP:PORT/allow?nick=...&secret=...
 * и ник получает "пропуск" на window-seconds секунд.
 */
public final class LauncherGuard extends JavaPlugin implements Listener {

    private final Map<String, Long> allowed = new ConcurrentHashMap<>();
    private HttpServer http;
    private String secret;
    private long windowMs;
    private String kickMessage;
    private boolean enabled;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        enabled = getConfig().getBoolean("enabled", true);
        secret = getConfig().getString("secret", "");
        windowMs = getConfig().getInt("window-seconds", 60) * 1000L;
        kickMessage = getConfig().getString("kick-message",
                "&6HIVE RP &7» &fЗаходи через наш лаунчер: &6hivegrief.ru").replace('&', '§');
        int port = getConfig().getInt("port", 8777);

        getServer().getPluginManager().registerEvents(this, this);

        if (!guardActive()) {
            getLogger().warning("secret не задан в config.yml — защита ОТКЛЮЧЕНА, заходят все!");
        }

        try {
            http = HttpServer.create(new InetSocketAddress(port), 0);
            http.createContext("/allow", exchange -> {
                Map<String, String> q = parseQuery(exchange.getRequestURI().getRawQuery());
                String nick = q.get("nick");
                String s = q.get("secret");
                boolean ok = guardActive() && nick != null && s != null && constantTimeEquals(s, secret);
                if (ok) {
                    allowed.put(nick.toLowerCase(), System.currentTimeMillis() + windowMs);
                    getLogger().info("Пропуск выдан: " + nick);
                }
                byte[] body = (ok ? "ok" : "denied").getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(ok ? 200 : 403, body.length);
                try (OutputStream os = exchange.getResponseBody()) { os.write(body); }
            });
            http.setExecutor(null);
            http.start();
            getLogger().info("HTTP-API запущен на порту " + port);
        } catch (IOException e) {
            getLogger().severe("Не удалось открыть порт " + port + ": " + e.getMessage());
        }
    }

    @Override
    public void onDisable() {
        if (http != null) http.stop(0);
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (!enabled || !guardActive()) return;
        Long until = allowed.get(event.getName().toLowerCase());
        if (until == null || until < System.currentTimeMillis()) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, kickMessage);
        }
    }

    private boolean guardActive() {
        return secret != null && !secret.isEmpty() && !"CHANGE_ME".equals(secret);
    }

    private static Map<String, String> parseQuery(String raw) {
        Map<String, String> map = new HashMap<>();
        if (raw == null) return map;
        for (String pair : raw.split("&")) {
            int i = pair.indexOf('=');
            if (i > 0) {
                try {
                    map.put(java.net.URLDecoder.decode(pair.substring(0, i), "UTF-8"),
                            java.net.URLDecoder.decode(pair.substring(i + 1), "UTF-8"));
                } catch (Exception ignored) { }
            }
        }
        return map;
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int r = 0;
        for (int i = 0; i < a.length(); i++) r |= a.charAt(i) ^ b.charAt(i);
        return r == 0;
    }
}
