package com.skriptcommand;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.*;

public class CommandManager {

    private final SkriptCommand plugin;
    private CommandMap commandMap;
    private final List<String> registeredCommands = new ArrayList<>();

    public CommandManager(SkriptCommand plugin) {
        this.plugin = plugin;
        setupCommandMap();
    }

    private void setupCommandMap() {
        try {
            Field commandMapField = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            commandMap = (CommandMap) commandMapField.get(Bukkit.getServer());
        } catch (Exception e) {
            plugin.getLogger().severe("C.Mape erişim kapandı eklenti kendini kapatabilir.");
            e.printStackTrace();
        }
    }

    public void unregisterAll() {
        if (commandMap == null) return;

        Map<String, Command> knownCommands;
        try {
            Field knownCommandsField = commandMap.getClass().getDeclaredField("knownCommands");
            knownCommandsField.setAccessible(true);
            knownCommands = (Map<String, Command>) knownCommandsField.get(commandMap);
        } catch (Exception e) {
            try {
                knownCommands = commandMap.getKnownCommands();
            } catch (NoSuchMethodError ex) {
                plugin.getLogger().warning("reflection hatası mevcut komutlar silinmeyi red etti eklenti kendini durdurabilir");
                return;
            }
        }

        for (String cmdName : registeredCommands) {
            Command cmd = knownCommands.get(cmdName);
            if (cmd != null) {
                cmd.unregister(commandMap);
                knownCommands.remove(cmdName);
                knownCommands.remove(plugin.getName().toLowerCase() + ":" + cmdName);
            }
        }
        registeredCommands.clear();
    }

    public void reloadCommands() {
        unregisterAll();
        loadCommands();
        
        try {
            Bukkit.getServer().getClass().getMethod("syncCommands").invoke(Bukkit.getServer());
        } catch (Exception ignored) {
        }
    }

    private void loadCommands() {
        if (commandMap == null) return;
        
        ConfigurationSection commandsSection = plugin.getConfig().getConfigurationSection("commands-tr");
        if (commandsSection != null) {
            for (String key : commandsSection.getKeys(false)) {
                if (plugin.getConfig().isConfigurationSection("commands-tr." + key + ".trigger")) {
                     ConfigurationSection triggerSection = plugin.getConfig().getConfigurationSection("commands-tr." + key + ".trigger");
                     if (triggerSection != null) {
                        for (String triggerKey : triggerSection.getKeys(false)) {
                            if (triggerKey.toLowerCase().contains("execute player command")) {
                                String targetCmd = extractCommand(triggerKey);
                                if (targetCmd != null && !targetCmd.isEmpty()) {
                                    registerCustomCommand(key, targetCmd);
                                } else {
                                    plugin.getLogger().warning("Komut anlaşılmadı " + triggerKey);
                                }
                            }
                        }
                     }
                } else if (plugin.getConfig().isString("commands-tr." + key + ".trigger")) {
                     String triggerLine = plugin.getConfig().getString("commands-tr." + key + ".trigger");
                     if (triggerLine != null && triggerLine.toLowerCase().contains("execute player command")) {
                         String targetCmd = extractCommand(triggerLine);
                         if (targetCmd != null && !targetCmd.isEmpty()) {
                             registerCustomCommand(key, targetCmd);
                         }
                     }
                } else {
                    Object triggerObj = plugin.getConfig().get("commands-tr." + key + ".trigger");
                    if (triggerObj instanceof Map) {
                         Map<?, ?> triggerMap = (Map<?, ?>) triggerObj;
                         for (Object k : triggerMap.keySet()) {
                             String triggerKey = k.toString();
                             if (triggerKey.toLowerCase().contains("execute player command")) {
                                 String targetCmd = extractCommand(triggerKey);
                                 if (targetCmd != null && !targetCmd.isEmpty()) {
                                     registerCustomCommand(key, targetCmd);
                                 }
                             }
                         }
                    } else if (triggerObj instanceof List) {
                        List<?> triggerList = (List<?>) triggerObj;
                        for (Object line : triggerList) {
                            String triggerLine = line.toString();
                            if (triggerLine.toLowerCase().contains("execute player command")) {
                                 String targetCmd = extractCommand(triggerLine);
                                 if (targetCmd != null && !targetCmd.isEmpty()) {
                                     registerCustomCommand(key, targetCmd);
                                 }
                            }
                        }
                    }
                }
            }
        }

        ConfigurationSection aliasSection = plugin.getConfig().getConfigurationSection("command-plate");
        if (aliasSection != null) {
            for (String key : aliasSection.getKeys(false)) {
                String oldCommand = aliasSection.getString(key + ".oldcommand");
                if (oldCommand != null) {
                    registerAliasCommand(key, oldCommand);
                }
            }
        }
    }
    
    private String extractCommand(String key) {
        int firstQuote = key.indexOf('"');
        int lastQuote = key.lastIndexOf('"');
        if (firstQuote != -1 && lastQuote != -1 && lastQuote > firstQuote) {
            return key.substring(firstQuote + 1, lastQuote);
        }
        
        String lowerKey = key.toLowerCase();
        int cmdIndex = lowerKey.indexOf("command");
        if (cmdIndex != -1) {
            String afterCommand = key.substring(cmdIndex + 7).trim();
            if (afterCommand.startsWith(":")) {
                afterCommand = afterCommand.substring(1).trim();
            }
            if (!afterCommand.isEmpty()) {
                return afterCommand.split(" ")[0]; 
            }
        }
        return null;
    }

    private void registerCustomCommand(String name, String targetCommand) {
        CustomCommand cmd = new CustomCommand(name, targetCommand);
        commandMap.register(plugin.getName(), cmd);
        registeredCommands.add(name);
    }

    private void registerAliasCommand(String name, String targetCommand) {
        AliasCommand cmd = new AliasCommand(name, targetCommand);
        commandMap.register(plugin.getName(), cmd);
        registeredCommands.add(name);
    }

    private class CustomCommand extends Command {
        private final String targetCommand;

        protected CustomCommand(String name, String targetCommand) {
            super(name);
            this.targetCommand = targetCommand;
            this.setPermission("");
        }

        @Override
        public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "komutları konsoldan kullanamazsın");
                return true;
            }

            Player player = (Player) sender;

            if (args.length > 0) {
                String prefix = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages-tr.prefix", ""));
                String unknownMsg = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages-tr.unknown-arg", "Bilinmeyen argüman kullandınız"));
                player.sendMessage(prefix + unknownMsg);
                return true;
            }

            player.performCommand(targetCommand);
            return true;
        }
    }

    private class AliasCommand extends Command {
        private final String targetCommandName;

        protected AliasCommand(String name, String targetCommandName) {
            super(name);
            this.targetCommandName = targetCommandName;
        }

        @Override
        public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
            Command target = commandMap.getCommand(targetCommandName);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "[hata] Hedef komut (" + targetCommandName + ") bulunamadı lütfen tekrar kontrol edin");
                return true;
            }

            StringBuilder fullCmd = new StringBuilder(targetCommandName);
            for (String arg : args) {
                fullCmd.append(" ").append(arg);
            }
            
            if (sender instanceof Player) {
                ((Player) sender).performCommand(fullCmd.toString());
            } else {
                Bukkit.dispatchCommand(sender, fullCmd.toString());
            }
            
            return true;
        }

        @Override
        public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) throws IllegalArgumentException {
            Command target = commandMap.getCommand(targetCommandName);
            if (target != null) {
                List<String> completions = target.tabComplete(sender, targetCommandName, args);
                if (completions != null) {
                    return completions;
                }
            }
            return super.tabComplete(sender, alias, args);
        }
    }
}
