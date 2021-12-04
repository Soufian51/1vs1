package com.sero583.onevsonerm.command;

import cn.nukkit.Player;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.level.Level;
import cn.nukkit.level.Location;
import cn.nukkit.utils.Config;
import cn.nukkit.utils.TextFormat;
import com.sero583.onevsonerm.Main;
import com.sero583.onevsonerm.utils.SetupData;
import org.w3c.dom.Text;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Serhat G. (sero583)
 */
public class SetupCommand extends Command {
    protected static final String PREFIX = TextFormat.BOLD + "[" + TextFormat.RESET + TextFormat.GREEN + "1" + TextFormat.DARK_RED + "vs" + TextFormat.GREEN + "1" + TextFormat.WHITE + TextFormat.BOLD + "] " + TextFormat.RESET;
    protected Main plugin;

    protected HashMap<String, SetupData> setupData = new HashMap<>();

    public SetupCommand(Main plugin, String name, String description, String usageMessage, String[] aliases, String permission) {
        super(name, description, usageMessage, aliases);

        this.plugin = plugin;
        this.setPermission(permission);
    }

    @Override
    public boolean execute(CommandSender commandSender, String label, String[] args) {
        if(!(commandSender instanceof Player)) {
            commandSender.sendMessage(TextFormat.RED + "This command can be only used in-game.");
            return true;
        }

        if(this.testPermission(commandSender)==true) {
            Player player = (Player) commandSender;
            String name = player.getName();
            Level playerLevel = player.getLevel();

            int argsLen = args.length;

            if(argsLen<=0) {
                this.sendHelpMessage(commandSender, label);
                return true;
            }

            String subCmd = args[0];

            switch(subCmd.toLowerCase()) {
                case "help":
                    this.sendHelpMessage(commandSender, label);
                return true;
                case "exit":
                    if(this.setupData.containsKey(name)==true) {
                        SetupData data = this.setupData.get(name);
                        player.sendMessage(PREFIX + TextFormat.GREEN + "Exiting map \"" + data.getLevel().getName() + "\" without saving.");

                        this.teleportToSpawn(player);
                        playerLevel.unload(true);
                        this.setupData.remove(name);
                    } else player.sendMessage(PREFIX + TextFormat.RED + "You aren't in setup mode.");
                return true;
                case "finish":
                    if(this.setupData.containsKey(name)==true) {
                        SetupData data = this.setupData.get(name);

                        if(data.getLocationOne()!=null&&data.getLocationTwo()!=null) {
                            // save on disk
                            Config config = this.plugin.getArenasConfig();
                            config.set(data.getLevel().getFolderName(), data.generateSaveData());
                            config.save();
                            // hot-load into selection
                            this.plugin.getSpawnpointsData().put(data.getLevel().getFolderName(), new Location[] { data.getLocationOne(), data.getLocationTwo() });

                            player.sendMessage(PREFIX + TextFormat.GREEN + "Successfully setup arena \"" + data.getLevel().getName() + "\".");
                            this.teleportToSpawn(player);
                        } else {
                            player.sendMessage(PREFIX + TextFormat.DARK_RED + "Before finishing, please set all locations.");
                        }
                    } else player.sendMessage(PREFIX + TextFormat.RED + "You aren't in setup mode.");
                return true;
                case "remove":
                    String targetName = argsLen >= 2 ? args[1] : null;

                    if(targetName==null) {
                        player.sendMessage(PREFIX + TextFormat.RED + "Please specify which levels setup you would like to remove.");
                        return true;
                    }

                    Config config = this.plugin.getArenasConfig();

                    if(config.get(targetName, null)!=null) {
                        config.remove(targetName);
                        config.save();

                        player.sendMessage(PREFIX + TextFormat.RED + "Levels \"" + targetName + "\" setup has been removed.");
                    } else {
                        player.sendMessage(PREFIX + TextFormat.RED + "Level \"" + targetName + "\" has no existing setup, so nothing has been removed.");
                    }
                return true;
                case "setpoint":
                    if(this.setupData.containsKey(name)==true) {
                        SetupData data = this.setupData.get(name);

                        Level there = data.getLevel();

                        if(playerLevel.equals(there)==false) {
                            player.sendMessage(PREFIX + TextFormat.RED + "Cannot set point in another world. Use " + label + " teleport to go back to setup world.");
                            return true;
                        }

                        String rawInput = argsLen >= 2 ? args[1] : null;
                        Integer num = null;

                        if(rawInput!=null) {
                            try {
                                num = Integer.parseInt(rawInput);
                            } catch(NumberFormatException e) {
                                player.sendMessage(PREFIX + TextFormat.RED + "Entered value \""+rawInput+"\" is not a valid number. Either use 1 or 2, or leave field blank.");
                                return true;
                            }
                        }

                        if(num==null) {
                            if(data.getLocationOne()!=null && data.getLocationTwo()!=null) {
                                player.sendMessage(PREFIX + TextFormat.DARK_RED + "Both positions have been already set. If you want to override one specify 1 or 2 with number argument or finish setup.");
                                return true;
                            }

                            num = data.getLocationOne() == null ? 1 : 2;
                        } else if(num!=1||num!=2) {
                            player.sendMessage(PREFIX + TextFormat.RED + "Number must be 1 or 2 and cannot be " + num);
                        }

                        if(num==1) {
                            player.sendMessage("Setting loc 1");

                            data.setLocationOne(player.getLocation());
                        } else if(num==2) {
                            player.sendMessage("Setting loc 2");
                            data.setLocationTwo(player.getLocation());
                        }

                        player.sendMessage(PREFIX + TextFormat.GREEN + "Set location " + num + " to current position.");
                    } else player.sendMessage(PREFIX + TextFormat.RED + "You aren't in setup mode.");
                return true;
                case "setup":
                    if(this.setupData.containsKey(name)==true) {
                        player.sendMessage(PREFIX + TextFormat.RED + "You are already in setup mode.");
                        return true;
                    }

                    String levelName = argsLen >= 2 ? args[1] : null;

                    if(levelName==null) {
                        player.sendMessage(PREFIX + TextFormat.RED + "Please specify which level you would like to setup as an arena.");
                        return true;
                    }

                    if(this.plugin.getSpawnpoints(levelName)!=null) {
                        player.sendMessage(PREFIX + TextFormat.RED + "Level \"" + levelName + "\" is already setup. If you want to make changes use ");
                        return true;
                    }

                    if(this.plugin.getServer().loadLevel(levelName)==true) {
                        Level level = this.plugin.getServer().getLevelByName(levelName);

                        for(Map.Entry<String, SetupData> entry : this.setupData.entrySet()) {
                            if(level.equals(entry.getValue().getLevel())==true) {
                                player.sendMessage(PREFIX + TextFormat.DARK_RED + "Player \"" + entry.getKey() + "\" is currently setting that level up.");
                                return true;
                            }
                        }

                        SetupData data = new SetupData(level);

                        player.sendMessage(PREFIX + TextFormat.GREEN + "Teleporting to level...");
                        player.teleport(level.getSafeSpawn());
                        player.sendMessage(PREFIX + TextFormat.DARK_GREEN + "Teleported. Now you can setup the level. For help use " + label + " help.");

                        this.setupData.put(name, data);
                    } else {
                        player.sendMessage(PREFIX + TextFormat.RED + "Level \"" + levelName + "\" does not exists.");
                    }
                return true;
                case "teleport":
                    if(this.setupData.containsKey(name)==true) {
                        player.sendMessage(PREFIX + TextFormat.GREEN + "Teleporting back to setup world spawn...");
                        player.teleport(this.setupData.get(name).getLevel().getSafeSpawn());
                        player.sendMessage(PREFIX + TextFormat.DARK_GREEN + "Teleported!");
                    } else player.sendMessage(PREFIX + TextFormat.RED + "You aren't in setup mode.");
                break;
                default:
                    player.sendMessage(PREFIX + TextFormat.RED + "Command \"" + subCmd + "\" does not exists. Use /" + label + " help for ");
                return false;
            }
        }
        return false;
    }

    protected void sendHelpMessage(CommandSender commandSender) {
        this.sendHelpMessage(commandSender, null);
    }

    protected void sendHelpMessage(CommandSender commandSender, String label) {
        if(label==null) {
            label = this.getName();
        }

        label = "/" + label;

        commandSender.sendMessage(TextFormat.GREEN + "--- 1vs1 command help ---\n" +
                TextFormat.DARK_GREEN + label + TextFormat.AQUA + " exit" + TextFormat.WHITE + " - Exit current setup without saving.\n" +
                TextFormat.DARK_GREEN + label + TextFormat.AQUA + " finish" + TextFormat.WHITE + " - When being in setup mode, with this command you can end setup with saving.\n" +
                TextFormat.DARK_GREEN + label + TextFormat.AQUA + " remove <level>" + TextFormat.WHITE + " - Remove an existing setup.\n" +
                TextFormat.DARK_GREEN + label + TextFormat.AQUA + " setpoint <optional: num>" + TextFormat.WHITE + " - Set spawnpoint. If you wanna replace already set spawnpoint, use num to specify which should be changed.\n" +
                TextFormat.DARK_GREEN + label + TextFormat.AQUA + " setup <level>" + TextFormat.WHITE + " - With this you can setup an arena and configure it's spawnpoints.\n" +
                TextFormat.DARK_GREEN + label + TextFormat.AQUA + " teleport" + TextFormat.WHITE + " - In case you went into setup mode and got somehow teleported out of world, you can use this to go back."
        );
    }

    protected void teleportToSpawn(Player player) {
        player.teleport(this.plugin.getServer().getDefaultLevel().getSafeSpawn());
    }
}
