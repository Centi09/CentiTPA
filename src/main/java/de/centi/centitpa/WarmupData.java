package de.centi.centitpa;

import org.bukkit.Location;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

public class WarmupData {

    private final UUID playerUuid;
    private final Location startLocation;
    BukkitTask task;

    public WarmupData(UUID playerUuid, Location startLocation, BukkitTask task) {
        this.playerUuid = playerUuid;
        this.startLocation = startLocation.clone();
        this.task = task;
    }

    public UUID getPlayerUuid()       { return playerUuid; }
    public Location getStartLocation(){ return startLocation.clone(); }
}
