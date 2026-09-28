package de.centi.centitpa;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TpaManager {

    private final CentiTPA plugin;

    // sender -> list of outgoing requests (one per target)
    private final Map<UUID, Map<UUID, TpaRequest>> outgoing = new ConcurrentHashMap<>();
    // target -> list of incoming requests (one per sender)
    private final Map<UUID, Map<UUID, TpaRequest>> incoming = new ConcurrentHashMap<>();

    // players with TPA toggled off
    private final Set<UUID> toggled = Collections.synchronizedSet(new HashSet<>());

    // sender cooldowns
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    // active warmups: moving player -> warmup data
    private final Map<UUID, WarmupData> warmups = new ConcurrentHashMap<>();

    // post-teleport protection: uuid -> expiry timestamp
    private final Map<UUID, Long> protection = new ConcurrentHashMap<>();

    // --- Config values ---
    private long requestTimeoutMs;
    private long cooldownMs;
    private int warmupSeconds;
    private long protectionMs;

    public TpaManager(CentiTPA plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        requestTimeoutMs = plugin.getConfig().getLong("timing.request-timeout", 60) * 1000L;
        cooldownMs       = plugin.getConfig().getLong("timing.send-cooldown", 10) * 1000L;
        warmupSeconds    = plugin.getConfig().getInt("timing.warmup", 5);
        protectionMs     = plugin.getConfig().getLong("timing.protection", 5) * 1000L;
    }

    // ----------------------------- PUBLIC API ---------------------------------

    public boolean isToggled(Player p) {
        return toggled.contains(p.getUniqueId());
    }

    public void toggle(Player p) {
        if (toggled.remove(p.getUniqueId())) {
            msg(p, "messages.toggle-on");
        } else {
            toggled.add(p.getUniqueId());
            msg(p, "messages.toggle-off");
        }
    }

    public boolean isInWarmup(Player p) {
        return warmups.containsKey(p.getUniqueId());
    }

    public boolean hasProtection(Player p) {
        Long exp = protection.get(p.getUniqueId());
        if (exp == null) return false;
        if (System.currentTimeMillis() >= exp) {
            protection.remove(p.getUniqueId());
            return false;
        }
        return true;
    }

    public void removeProtection(Player p) {
        protection.remove(p.getUniqueId());
    }

    /**
     * Send a TPA or TPA_HERE request from sender to target.
     */
    public void sendRequest(Player sender, Player target, TpaRequest.Type type) {
        // Toggle check
        if (toggled.contains(target.getUniqueId())) {
            msg(sender, "messages.target-toggle-off",
                    "target", target.getName());
            return;
        }

        // Cooldown check
        if (!sender.hasPermission("centitpa.bypass.cooldown")) {
            long now = System.currentTimeMillis();
            long last = cooldowns.getOrDefault(sender.getUniqueId(), 0L);
            long diff = now - last;
            if (diff < cooldownMs) {
                long secsLeft = (cooldownMs - diff + 999) / 1000;
                msg(sender, "messages.cooldown", "seconds", String.valueOf(secsLeft));
                return;
            }
            cooldowns.put(sender.getUniqueId(), now);
        }

        // Remove any previous request from this sender to this target
        cancelExistingRequest(sender.getUniqueId(), target.getUniqueId());

        // Create request
        TpaRequest req = new TpaRequest(sender.getUniqueId(), target.getUniqueId(), type);

        // Timeout task
        BukkitTask timeout = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            boolean removed = removeRequest(req);
            if (!removed) return;
            Player s = Bukkit.getPlayer(req.getSender());
            Player t = Bukkit.getPlayer(req.getTarget());
            if (s != null) msg(s, "messages.request-expired-sender", "target", target.getName());
            if (t != null) msg(t, "messages.request-expired-target", "sender", sender.getName());
        }, requestTimeoutMs / 50L);

        req.timeoutTask = timeout;

        // Store
        outgoing.computeIfAbsent(sender.getUniqueId(), k -> new ConcurrentHashMap<>())
                .put(target.getUniqueId(), req);
        incoming.computeIfAbsent(target.getUniqueId(), k -> new ConcurrentHashMap<>())
                .put(sender.getUniqueId(), req);

        // Notify sender
        String worldName = target.getWorld().getName();
        if (type == TpaRequest.Type.TPA) {
            msg(sender, "messages.request-sent-tpa",
                    "target", target.getName(), "world", worldName);
        } else {
            msg(sender, "messages.request-sent-tpahere",
                    "target", target.getName(), "world", worldName);
        }

        // Notify target with clickable buttons
        String senderWorld = sender.getWorld().getName();
        String incomingKey = (type == TpaRequest.Type.TPA)
                ? "messages.request-received-tpa"
                : "messages.request-received-tpahere";

        Component infoLine = LegacyComponentSerializer.legacyAmpersand().deserialize(
                resolveMsg(incomingKey, "sender", sender.getName(), "world", senderWorld));

        // Clickable accept/deny buttons
        Component acceptBtn = LegacyComponentSerializer.legacyAmpersand()
                .deserialize("&8[&a&lANNEHMEN&8]")
                .clickEvent(ClickEvent.runCommand("/tpaccept " + sender.getName()))
                .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                        Component.text("Klicke um die Anfrage anzunehmen")));

        Component denyBtn = LegacyComponentSerializer.legacyAmpersand()
                .deserialize("&8[&c&lABLEHNEN&8]")
                .clickEvent(ClickEvent.runCommand("/tpdeny " + sender.getName()))
                .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                        Component.text("Klicke um die Anfrage abzulehnen")));

        Component buttons = Component.text(" ")
                .append(acceptBtn)
                .append(Component.text(" "))
                .append(denyBtn);

        target.sendMessage(prefix().append(infoLine));
        target.sendMessage(prefix().append(buttons));

        // Sound
        playSound(target, "sounds.request-received");
    }

    /**
     * Accept: target accepts a request from a specific sender (or the oldest if senderName is null).
     */
    public void acceptRequest(Player target, String senderName) {
        Map<UUID, TpaRequest> map = incoming.get(target.getUniqueId());
        if (map == null || map.isEmpty()) {
            msg(target, "messages.no-incoming-request");
            return;
        }

        TpaRequest req = findRequest(map, senderName);
        if (req == null) {
            msg(target, "messages.no-incoming-request");
            return;
        }

        Player sender = Bukkit.getPlayer(req.getSender());
        if (sender == null) {
            removeRequest(req);
            msg(target, "messages.requester-offline");
            return;
        }

        removeRequest(req);

        // Notify both
        msg(target,  "messages.accept-message", "sender", sender.getName());
        msg(sender, "messages.accept-notify",  "target", target.getName());

        // Determine who moves where
        Player moving      = (req.getType() == TpaRequest.Type.TPA) ? sender : target;
        Player destination = (req.getType() == TpaRequest.Type.TPA) ? target : sender;

        startWarmup(moving, destination);
    }

    /**
     * Deny: target denies a request from a specific sender (or the oldest if null).
     */
    public void denyRequest(Player target, String senderName) {
        Map<UUID, TpaRequest> map = incoming.get(target.getUniqueId());
        if (map == null || map.isEmpty()) {
            msg(target, "messages.no-incoming-request");
            return;
        }

        TpaRequest req = findRequest(map, senderName);
        if (req == null) {
            msg(target, "messages.no-incoming-request");
            return;
        }

        Player sender = Bukkit.getPlayer(req.getSender());
        removeRequest(req);

        msg(target, "messages.request-denied-target", "sender",
                sender != null ? sender.getName() : "?");
        if (sender != null) {
            msg(sender, "messages.request-denied-sender", "target", target.getName());
        }
    }

    /**
     * Cancel: sender withdraws their most recent outgoing request.
     */
    public void cancelRequest(Player sender) {
        Map<UUID, TpaRequest> map = outgoing.get(sender.getUniqueId());
        if (map == null || map.isEmpty()) {
            msg(sender, "messages.no-outgoing-request");
            return;
        }

        // Pick the most recent outgoing request
        TpaRequest req = map.values().stream()
                .max(Comparator.comparingLong(TpaRequest::getCreatedAt))
                .orElse(null);

        if (req == null) {
            msg(sender, "messages.no-outgoing-request");
            return;
        }

        Player target = Bukkit.getPlayer(req.getTarget());
        removeRequest(req);

        msg(sender, "messages.request-cancelled-sender");
        if (target != null) {
            msg(target, "messages.request-cancelled-target", "sender", sender.getName());
        }
    }

    /**
     * Called when a player in warmup moves.
     */
    public void handleMove(Player player) {
        if (!warmups.containsKey(player.getUniqueId())) return;
        if (player.hasPermission("centitpa.bypass.movecancel")) return;
        cancelWarmup(player, "messages.warmup-cancelled-move");
    }

    /**
     * Called on disconnect/quit.
     */
    public void handleQuit(Player player) {
        cancelWarmup(player, null);
        clearAllRequests(player.getUniqueId());
        protection.remove(player.getUniqueId());
    }

    /**
     * Full shutdown cleanup.
     */
    public void shutdown() {
        for (WarmupData wd : warmups.values()) {
            if (wd.task != null) wd.task.cancel();
        }
        warmups.clear();

        for (Map<UUID, TpaRequest> map : outgoing.values()) {
            for (TpaRequest req : map.values()) {
                if (req.timeoutTask != null) req.timeoutTask.cancel();
            }
        }
        outgoing.clear();
        incoming.clear();
        protection.clear();
    }

    // ----------------------------- WARMUP ------------------------------------

    private void startWarmup(Player moving, Player destination) {
        // Cancel any existing warmup
        cancelWarmup(moving, null);

        Location startLoc = moving.getLocation().clone();
        String warmupMsg = resolveMsg("messages.warmup-start",
                "seconds", String.valueOf(warmupSeconds));
        moving.sendMessage(prefix().append(LegacyComponentSerializer.legacyAmpersand()
                .deserialize(warmupMsg)));

        final int[] countdown = {warmupSeconds};
        final Player[] dest = {destination}; // capture for lambda

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!moving.isOnline()) {
                cancelWarmup(moving, null);
                return;
            }

            // Check if destination is still online
            if (!dest[0].isOnline()) {
                cancelWarmup(moving, "messages.warmup-cancelled-offline");
                return;
            }

            if (countdown[0] <= 0) {
                // Execute teleport
                WarmupData wd = warmups.remove(moving.getUniqueId());
                if (wd != null && wd.task != null) wd.task.cancel();

                // Always teleport to live location of destination
                moving.teleport(dest[0].getLocation());
                playSound(moving, "sounds.teleport-success");

                long expiry = System.currentTimeMillis() + protectionMs;
                protection.put(moving.getUniqueId(), expiry);

                msg(moving, "messages.teleport-success",
                        "seconds", String.valueOf(protectionMs / 1000));

                // Schedule protection-expiry message
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    Long exp = protection.get(moving.getUniqueId());
                    if (exp != null && System.currentTimeMillis() >= exp) {
                        protection.remove(moving.getUniqueId());
                        if (moving.isOnline()) {
                            msg(moving, "messages.protection-expired");
                        }
                    }
                }, (protectionMs / 50L) + 2L);

                return;
            }

            // Countdown ticks (last 3 seconds)
            if (countdown[0] <= 3) {
                msg(moving, "messages.warmup-tick", "seconds", String.valueOf(countdown[0]));
                playSound(moving, "sounds.warmup-tick");
            }
            countdown[0]--;
        }, 0L, 20L);

        warmups.put(moving.getUniqueId(), new WarmupData(moving.getUniqueId(), startLoc, task));
    }

    private void cancelWarmup(Player player, String msgKey) {
        WarmupData wd = warmups.remove(player.getUniqueId());
        if (wd != null && wd.task != null) wd.task.cancel();
        if (msgKey != null && player.isOnline()) {
            msg(player, msgKey);
        }
    }

    // ----------------------------- HELPERS -----------------------------------

    private boolean removeRequest(TpaRequest req) {
        Map<UUID, TpaRequest> out = outgoing.get(req.getSender());
        Map<UUID, TpaRequest> in  = incoming.get(req.getTarget());
        boolean removed = false;
        if (out != null && out.remove(req.getTarget(), req)) removed = true;
        if (in  != null && in.remove(req.getSender(), req))  removed = true;
        if (req.timeoutTask != null) req.timeoutTask.cancel();
        return removed;
    }

    private void cancelExistingRequest(UUID sender, UUID target) {
        Map<UUID, TpaRequest> map = outgoing.get(sender);
        if (map == null) return;
        TpaRequest old = map.get(target);
        if (old != null) removeRequest(old);
    }

    private void clearAllRequests(UUID uuid) {
        // Cancel all outgoing requests from this player
        Map<UUID, TpaRequest> out = outgoing.remove(uuid);
        if (out != null) {
            for (TpaRequest req : out.values()) {
                if (req.timeoutTask != null) req.timeoutTask.cancel();
                Map<UUID, TpaRequest> targetMap = incoming.get(req.getTarget());
                if (targetMap != null) targetMap.remove(uuid);
            }
        }

        // Cancel all incoming requests to this player
        Map<UUID, TpaRequest> in = incoming.remove(uuid);
        if (in != null) {
            for (TpaRequest req : in.values()) {
                if (req.timeoutTask != null) req.timeoutTask.cancel();
                Map<UUID, TpaRequest> senderMap = outgoing.get(req.getSender());
                if (senderMap != null) senderMap.remove(uuid);
            }
        }
    }

    private TpaRequest findRequest(Map<UUID, TpaRequest> map, String senderName) {
        if (senderName != null) {
            Player p = Bukkit.getPlayerExact(senderName);
            if (p != null) return map.get(p.getUniqueId());
            return null;
        }
        // No name given: return the most recently received request
        return map.values().stream()
                .max(Comparator.comparingLong(TpaRequest::getCreatedAt))
                .orElse(null);
    }

    // ----------------------------- MESSAGING ---------------------------------

    private Component prefix() {
        String raw = plugin.getConfig().getString("messages.prefix", "&8[&5TPA&8] ");
        return LegacyComponentSerializer.legacyAmpersand().deserialize(raw);
    }

    private String resolveMsg(String key, String... replacements) {
        String raw = plugin.getConfig().getString(key, key);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            raw = raw.replace("{" + replacements[i] + "}", replacements[i + 1]);
        }
        return raw;
    }

    public void msg(Player player, String key, String... replacements) {
        String raw = resolveMsg(key, replacements);
        if (raw.isEmpty()) return;
        player.sendMessage(prefix().append(
                LegacyComponentSerializer.legacyAmpersand().deserialize(raw)));
    }

    private void playSound(Player player, String configKey) {
        String soundName = plugin.getConfig().getString(configKey, "");
        if (soundName.isEmpty()) return;
        try {
            Sound sound = Sound.valueOf(soundName.toUpperCase());
            player.playSound(player.getLocation(), sound, 1f, 1f);
        } catch (IllegalArgumentException ignored) {}
    }
}
