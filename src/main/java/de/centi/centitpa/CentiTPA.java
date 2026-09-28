package de.centi.centitpa;

import org.bukkit.plugin.java.JavaPlugin;

public class CentiTPA extends JavaPlugin {

    private TpaManager tpaManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        tpaManager = new TpaManager(this);

        TpaCommand handler = new TpaCommand(this, tpaManager);
        TpaListener listener = new TpaListener(this, tpaManager);

        // Register commands
        String[] cmds = {"tpa", "tpahere", "tpaccept", "tpdeny", "tpcancel", "tptoggle"};
        for (String cmd : cmds) {
            if (getCommand(cmd) != null) {
                getCommand(cmd).setExecutor(handler);
                getCommand(cmd).setTabCompleter(handler);
            }
        }

        // Register events
        getServer().getPluginManager().registerEvents(listener, this);

        getLogger().info("CentiTPA v" + getDescription().getVersion() + " aktiviert.");
    }

    @Override
    public void onDisable() {
        if (tpaManager != null) {
            tpaManager.shutdown();
        }
        getLogger().info("CentiTPA deaktiviert.");
    }

    public TpaManager getTpaManager() {
        return tpaManager;
    }
}
