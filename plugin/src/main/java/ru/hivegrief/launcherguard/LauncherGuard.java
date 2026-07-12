package ru.hivegrief.launcherguard;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class LauncherGuard extends JavaPlugin implements Listener {

    public static class Account {
        String nick;      // с оригинальным регистром
        String hash;
        String salt;
        long created;
    }

    private static final Pattern NICK = Pattern.compile("^[A-Za-z0-9_]{3,16}$");
    private static final long PASS_TTL = 60_000L;

    private HttpServer http;
    private final Map<String, Long> passes = new ConcurrentHashMap<>();      // lowernick -> expire
    private Map<String, Account> accounts = new ConcurrentHashMap<>();       // lowernick -> account
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private File accFile;
    private String secret;
    private boolean requirePassword;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        secret = getConfig().getString("secret", "");
        requirePassword = getConfig().getBoolean("require-password", true);
        accFile = new File(getDataFolder(), "accounts.json");
        loadAccounts();

        int port = getConfig().getInt("port", 25671);
        try {
            http = HttpServer.create(new InetSocketAddress(port), 0);
            http.createContext("/allow", this::handleAllow);
            http.createContext("/register", this::handleRegister);
            http.createContext("/login", this::handleLogin);
            http.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(2, r -> new Thread(r, "HTTP-Dispatcher")));
            http.start();
            getLogger().info("HTTP-API запущен на порту " + port + " (аккаунты: " + accounts.size() + ")");
        } catch (IOException e) {
            getLogger().severe("Не удалось запустить HTTP-API: " + e.getMessage());
        }
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        if (http != null) http.stop(0);
        saveAccounts();
    }

    /* ================= HTTP ================= */

    private Map<String, String> query(HttpExchange ex) throws IOException {
        Map<String, String> out = new HashMap<>();
        String q = ex.getRequestURI().getRawQuery();
        if (q == null) return out;
        for (String p : q.split("&")) {
            int i = p.indexOf('=');
            if (i > 0) out.put(URLDecoder.decode(p.substring(0, i), "UTF-8"),
                    URLDecoder.decode(p.substring(i + 1), "UTF-8"));
        }
        return out;
    }

    private void respond(HttpExchange ex, String json) throws IOException {
        byte[] b = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(200, b.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(b); }
    }

    private void handleRegister(HttpExchange ex) throws IOException {
        Map<String, String> q = query(ex);
        String nick = q.getOrDefault("nick", "").trim();
        String pass = q.getOrDefault("pass", "");
        if (!checkSecret(q)) { respond(ex, err("bad_secret")); return; }
        if (!NICK.matcher(nick).matches()) { respond(ex, err("bad_nick")); return; }
        if (pass.length() < 4 || pass.length() > 64) { respond(ex, err("bad_password")); return; }
        String key = nick.toLowerCase();
        synchronized (this) {
            if (accounts.containsKey(key)) { respond(ex, err("nick_taken")); return; }
            Account a = new Account();
            a.nick = nick;
            a.salt = randomSalt();
            a.hash = sha256(a.salt + pass);
            a.created = System.currentTimeMillis();
            accounts.put(key, a);
            saveAccounts();
        }
        getLogger().info("Регистрация: " + nick);
        respond(ex, "{\"ok\":true}");
    }

    private void handleLogin(HttpExchange ex) throws IOException {
        Map<String, String> q = query(ex);
        String nick = q.getOrDefault("nick", "").trim();
        String pass = q.getOrDefault("pass", "");
        if (!checkSecret(q)) { respond(ex, err("bad_secret")); return; }
        Account a = accounts.get(nick.toLowerCase());
        if (a == null) { respond(ex, err("not_found")); return; }
        if (!a.hash.equals(sha256(a.salt + pass))) { respond(ex, err("wrong_password")); return; }
        respond(ex, "{\"ok\":true,\"nick\":\"" + a.nick + "\"}");
    }

    private void handleAllow(HttpExchange ex) throws IOException {
        Map<String, String> q = query(ex);
        String nick = q.getOrDefault("nick", "").trim();
        String pass = q.get("pass");
        if (!checkSecret(q)) { respond(ex, err("bad_secret")); return; }
        if (!NICK.matcher(nick).matches()) { respond(ex, err("bad_nick")); return; }
        String key = nick.toLowerCase();

        if (requirePassword) {
            if (pass == null) { respond(ex, err("password_required")); return; }
            Account a = accounts.get(key);
            if (a == null) {
                // миграция: первый вход со старого лаунчера закрепляет ник
                synchronized (this) {
                    if (!accounts.containsKey(key)) {
                        Account na = new Account();
                        na.nick = nick;
                        na.salt = randomSalt();
                        na.hash = sha256(na.salt + pass);
                        na.created = System.currentTimeMillis();
                        accounts.put(key, na);
                        saveAccounts();
                        getLogger().info("Автозакрепление ника: " + nick);
                    }
                }
                a = accounts.get(key);
            }
            if (!a.hash.equals(sha256(a.salt + pass))) { respond(ex, err("wrong_password")); return; }
            if (!a.nick.equals(nick)) { respond(ex, err("bad_nick_case")); return; } // регистр должен совпадать
        }

        passes.put(key, System.currentTimeMillis() + PASS_TTL);
        getLogger().info("Пропуск выдан: " + nick);
        respond(ex, "{\"ok\":true}");
    }

    private boolean checkSecret(Map<String, String> q) {
        return secret != null && !secret.isEmpty() && secret.equals(q.get("secret"));
    }

    private static String err(String code) {
        return "{\"ok\":false,\"error\":\"" + code + "\"}";
    }

    /* ================= вход на сервер ================= */

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        String key = e.getName().toLowerCase();
        Long exp = passes.get(key);
        if (exp == null || exp < System.currentTimeMillis()) {
            e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    "\u00A76HIVE RP\n\u00A7fВход только через лаунчер!\n\u00A77hivegrief.ru");
            return;
        }
        Account a = accounts.get(key);
        if (a != null && !a.nick.equals(e.getName())) {
            e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    "\u00A7cНеверный регистр ника. Ваш ник: \u00A7f" + a.nick);
        }
    }

    /* ================= хранилище ================= */

    private void loadAccounts() {
        if (!accFile.exists()) return;
        try (Reader r = new InputStreamReader(new FileInputStream(accFile), StandardCharsets.UTF_8)) {
            Map<String, Account> m = gson.fromJson(r, new TypeToken<Map<String, Account>>(){}.getType());
            if (m != null) accounts = new ConcurrentHashMap<>(m);
        } catch (Exception e) {
            getLogger().severe("Не удалось прочитать accounts.json: " + e.getMessage());
        }
    }

    private synchronized void saveAccounts() {
        try {
            if (!getDataFolder().exists()) getDataFolder().mkdirs();
            try (Writer w = new OutputStreamWriter(new FileOutputStream(accFile), StandardCharsets.UTF_8)) {
                gson.toJson(accounts, w);
            }
        } catch (Exception e) {
            getLogger().severe("Не удалось сохранить accounts.json: " + e.getMessage());
        }
    }

    /* ================= утилиты ================= */

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String randomSalt() {
        byte[] b = new byte[16];
        new SecureRandom().nextBytes(b);
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }
}
