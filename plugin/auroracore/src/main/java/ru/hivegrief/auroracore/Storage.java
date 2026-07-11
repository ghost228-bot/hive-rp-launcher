package ru.hivegrief.auroracore;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class Storage {

    private final File dir;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public Storage(AuroraCore pl) {
        dir = new File(pl.getDataFolder(), "players");
        if (!dir.exists()) dir.mkdirs();
    }

    private File file(UUID id) {
        return new File(dir, id.toString() + ".json");
    }

    public CharacterData load(UUID id) {
        File f = file(id);
        if (!f.exists()) return null;
        try (Reader r = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
            return gson.fromJson(r, CharacterData.class);
        } catch (Exception e) {
            return null;
        }
    }

    public void save(UUID id, CharacterData d) {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file(id)), StandardCharsets.UTF_8)) {
            gson.toJson(d, w);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean delete(UUID id) {
        return file(id).delete();
    }
}
