package de.centi.centitpa;

import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

public class TpaRequest {

    public enum Type {
        TPA,
        TPA_HERE
    }

    private final UUID id = UUID.randomUUID();
    private final UUID sender;
    private final UUID target;
    private final Type type;
    private final long createdAt;
    BukkitTask timeoutTask;

    public TpaRequest(UUID sender, UUID target, Type type) {
        this.sender = sender;
        this.target = target;
        this.type = type;
        this.createdAt = System.currentTimeMillis();
    }

    public UUID getId()       { return id; }
    public UUID getSender()   { return sender; }
    public UUID getTarget()   { return target; }
    public Type getType()     { return type; }
    public long getCreatedAt(){ return createdAt; }
}
