package ru.hivegrief.auroracore;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.trait.SkinTrait;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Манекен-предпросмотр внешности (как модель персонажа в GTA5RP).
 * Класс инстанцируется ТОЛЬКО если Citizens установлен (см. AuroraCore#onEnable),
 * иначе классы Citizens не загружаются и плагин работает без предпросмотра.
 */
public class PreviewNpc {

    private final AuroraCore pl;
    private final Map<UUID, Integer> npcs = new HashMap<>();

    public PreviewNpc(AuroraCore pl) {
        this.pl = pl;
    }

    public void show(Player p, String skin) {
        Location loc = pl.loc("preview");
        if (loc == null) return;

        // манекен смотрит на игрока (точку подиума игрока)
        Location pod = pl.loc("podium");
        if (pod != null) {
            Vector dir = pod.toVector().subtract(loc.toVector());
            if (dir.lengthSquared() > 0.01) loc.setDirection(dir.setY(0));
        }

        NPC npc = null;
        Integer id = npcs.get(p.getUniqueId());
        if (id != null) npc = CitizensAPI.getNPCRegistry().getById(id);
        if (npc == null) {
            npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, " ");
            npc.setProtected(true);
            npcs.put(p.getUniqueId(), npc.getId());
        }
        if (!npc.isSpawned()) npc.spawn(loc);
        else npc.teleport(loc, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);

        try {
            npc.getOrAddTrait(SkinTrait.class).setSkinName(skin, true);
        } catch (Throwable t) {
            npc.getOrAddTrait(SkinTrait.class).setSkinName(skin);
        }
    }

    public void hide(Player p) {
        Integer id = npcs.remove(p.getUniqueId());
        if (id == null) return;
        NPC npc = CitizensAPI.getNPCRegistry().getById(id);
        if (npc != null) npc.destroy();
    }

    public void hideAll() {
        for (Integer id : npcs.values()) {
            NPC npc = CitizensAPI.getNPCRegistry().getById(id);
            if (npc != null) npc.destroy();
        }
        npcs.clear();
    }
}
