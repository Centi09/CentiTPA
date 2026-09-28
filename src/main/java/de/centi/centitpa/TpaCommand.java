package de.centi.centitpa;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class TpaCommand implements CommandExecutor, TabCompleter {

    private final CentiTPA plugin;
    private final TpaManager manager;

    public TpaCommand(CentiTPA plugin, TpaManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);

        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getConfig().getString("messages.only-player", "Nur Spieler."));
            return true;
        }

        switch (cmd) {
            case "tpa"      -> handleTpa(player, args);
            case "tpahere"  -> handleTpaHere(player, args);
            case "tpaccept" -> handleAccept(player, args);
            case "tpdeny"   -> handleDeny(player, args);
            case "tpcancel" -> handleCancel(player);
            case "tptoggle" -> handleToggle(player);
        }
        return true;
    }

    // ----------------------------- HANDLERS ----------------------------------

    private void handleTpa(Player player, String[] args) {
        if (!player.hasPermission("centitpa.use")) {
            manager.msg(player, "messages.no-permission");
            return;
        }
        if (args.length < 1) {
            player.sendMessage(plugin.getConfig().getString("messages.prefix", "&8[&5TPA&8] ")
                    .replace("&", "\u00a7") + "\u00a77Nutze: /tpa <spieler>");
            return;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            manager.msg(player, "messages.player-not-found");
            return;
        }
        if (target.equals(player)) {
            manager.msg(player, "messages.self-request");
            return;
        }

        manager.sendRequest(player, target, TpaRequest.Type.TPA);
    }

    private void handleTpaHere(Player player, String[] args) {
        if (!player.hasPermission("centitpa.use")) {
            manager.msg(player, "messages.no-permission");
            return;
        }
        if (args.length < 1) {
            player.sendMessage(plugin.getConfig().getString("messages.prefix", "&8[&5TPA&8] ")
                    .replace("&", "\u00a7") + "\u00a77Nutze: /tpahere <spieler>");
            return;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            manager.msg(player, "messages.player-not-found");
            return;
        }
        if (target.equals(player)) {
            manager.msg(player, "messages.self-request");
            return;
        }

        manager.sendRequest(player, target, TpaRequest.Type.TPA_HERE);
    }

    private void handleAccept(Player player, String[] args) {
        if (!player.hasPermission("centitpa.use")) {
            manager.msg(player, "messages.no-permission");
            return;
        }
        String senderName = args.length > 0 ? args[0] : null;
        manager.acceptRequest(player, senderName);
    }

    private void handleDeny(Player player, String[] args) {
        if (!player.hasPermission("centitpa.use")) {
            manager.msg(player, "messages.no-permission");
            return;
        }
        String senderName = args.length > 0 ? args[0] : null;
        manager.denyRequest(player, senderName);
    }

    private void handleCancel(Player player) {
        if (!player.hasPermission("centitpa.use")) {
            manager.msg(player, "messages.no-permission");
            return;
        }
        manager.cancelRequest(player);
    }

    private void handleToggle(Player player) {
        if (!player.hasPermission("centitpa.toggle")) {
            manager.msg(player, "messages.no-permission");
            return;
        }
        manager.toggle(player);
    }

    // ----------------------------- TAB COMPLETE ------------------------------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);

        if ((cmd.equals("tpa") || cmd.equals("tpahere")) && args.length == 1) {
            String input = args[0].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(input))
                    .sorted()
                    .collect(Collectors.toList());
        }

        if ((cmd.equals("tpaccept") || cmd.equals("tpdeny")) && args.length == 1) {
            // Tab complete with online players (optional player name)
            String input = args[0].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(input))
                    .sorted()
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}
