package com.skriptcommand;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class SkriptCommand extends JavaPlugin {

    private static SkriptCommand instance;
    private CommandManager commandManager;
    private boolean isFolia;

    @Override
    public void onEnable() {
        instance = this;
        
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            isFolia = true;
            getLogger().info("Folia Sunucusu Algılandı Eklenti Aktif Durumda Hata yok.");
        } catch (ClassNotFoundException e) {
            isFolia = false;
        }

        saveDefaultConfig();
        
        this.commandManager = new CommandManager(this);
        
        reloadPlugin();

        getLogger().info("SkriptCommand Aktif");
    }

    @Override
    public void onDisable() {
        if (commandManager != null) {
            commandManager.unregisterAll();
        }
        getLogger().info("SkriptCommand de.aktif");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("skriptcommand.yönetici")) {
                sender.sendMessage(ChatColor.RED + "Malesef Yetkin Bulunmuyor");
                return true;
            }
            
            sender.sendMessage(ChatColor.GOLD + "Config & Komutlar Yeniden Reload ediliyor lütfen bekleyiniz.");
            reloadPlugin();
            sender.sendMessage(ChatColor.GREEN + getConfig().getString("messages-tr.reload", "başarılı"));
            return true;
        }
        return false;
    }

    public void reloadPlugin() {
        runAsync(() -> {
            reloadConfig();
            runSync(() -> {
                commandManager.reloadCommands();
            });
        });
    }

    public void runAsync(Runnable runnable) {
        if (isFolia) {
            Bukkit.getAsyncScheduler().runNow(this, task -> runnable.run());
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(this, runnable);
        }
    }

    public void runSync(Runnable runnable) {
        if (isFolia) {
            Bukkit.getGlobalRegionScheduler().run(this, task -> runnable.run());
        } else {
            Bukkit.getScheduler().runTask(this, runnable);
        }
    }
    
    public void runSyncLater(Runnable runnable, long delayTicks) {
         if (isFolia) {
            Bukkit.getGlobalRegionScheduler().runDelayed(this, task -> runnable.run(), delayTicks);
        } else {
            Bukkit.getScheduler().runTaskLater(this, runnable, delayTicks);
        }
    }

    public static SkriptCommand getInstance() {
        return instance;
    }
    
    public CommandManager getCommandManager() {
        return commandManager;
    }
}
