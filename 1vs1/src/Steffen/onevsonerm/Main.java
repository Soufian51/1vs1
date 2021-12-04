package com.sero583.onevsonerm;


import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.projectile.EntityProjectile;
import cn.nukkit.event.Event;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.EventPriority;
import cn.nukkit.event.Listener;
import cn.nukkit.event.block.BlockBreakEvent;
import cn.nukkit.event.block.BlockPlaceEvent;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.entity.EntityExplodeEvent;
import cn.nukkit.event.level.LevelUnloadEvent;
import cn.nukkit.event.player.*;
import cn.nukkit.event.server.DataPacketReceiveEvent;
import cn.nukkit.form.response.FormResponseSimple;
import cn.nukkit.level.Level;
import cn.nukkit.level.Location;
import cn.nukkit.level.Position;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.LoginPacket;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.scheduler.Task;
import cn.nukkit.utils.Config;
import cn.nukkit.utils.ConfigSection;
import cn.nukkit.utils.TextFormat;
import com.google.gson.internal.LinkedTreeMap;
import com.sero583.onevsonerm.command.SetupCommand;
import com.sero583.onevsonerm.kit.Kit;
import com.sero583.onevsonerm.session.Arena;
import com.sero583.onevsonerm.session.Session;
import com.sero583.onevsonerm.task.CheckKitPickedTask;
import com.sero583.onevsonerm.utils.DuelPreparation;
import com.sero583.onevsonerm.utils.Random;
import com.sero583.onevsonerm.utils.SetupData;
import com.sero583.onevsonerm.utils.TimeHelper;

import java.io.File;
import java.util.*;

/**
 * @author Serhat G. (sero583)
 */
public class Main extends PluginBase implements Listener {
    protected static Main instance = null;

    public static Main getInstance() {
        return instance;
    }

    protected Location defaultSpawn = new Location(); /*= new Location() {{
        Position defaultSpawn = Server.getInstance().getDefaultLevel().getSafeSpawn();

        this.setComponents(defaultSpawn.x, defaultSpawn.y, defaultSpawn.z);
        this.setLevel(defaultSpawn.level);
    }};*/

    public Location getDefaultSpawn() {
        return this.defaultSpawn;
    }

    /**
     * Generates a unique key for a tandem
     * @param nameOne of player one
     * @param nameTwo of player two
     * @return tandemKey
     */
    public static String formKey(String nameOne, String nameTwo) {
        int compare = nameOne.compareTo(nameTwo);

        if(compare < 0) {
            //a is smaller
            return nameOne + ":" + nameTwo;
        } else if(compare > 0) {
            //a is larger
            return nameTwo + ":" + nameOne;
        }
        throw new RuntimeException("Tried to form a tandem key for the same player.");
    }

    public static final List<String> DEFAULT_RESOURCES = new ArrayList<String>() {
        {
            add("arenaSettings.yml");
            add("config.yml");
            add("kits.yml");
            add("messages.yml");
        }
    };

    protected Map<Integer, Session> sessions = new LinkedHashMap<Integer, Session>();

    // attacker -> Map: "victimName aka. challenged" -> timestamp
    protected Map<String, Map<String, Long>> requests = new HashMap<>();

    protected Set<DuelPreparation> preparations = new LinkedHashSet<>();

    // name -> kit
    protected Map<String, Kit> kit_selection = new HashMap<>();

    // integer of all forms sent by this plugin
    protected Map<String, Integer> formIds = new HashMap<>();

    // map -> locations array with two values
    protected Map<String, Location[]> spawnPoints = new LinkedHashMap<>();

    protected Config arenaConfig;

    protected Map<String, Boolean> challengeWorlds;

    protected Long requestDuration;

    public Config getArenasConfig() {
        return new Config(this.getDataFolder() + "/arenas.json", Config.JSON);
    }

    protected Config messages;

    protected SetupCommand command;

    protected Map<String, Long> damageCooldown = new LinkedHashMap<>();

    @Override
    public void onLoad() {
        this.getLogger().info(TextFormat.GREEN + "Loading...");

        if(instance!=null) {
            this.getLogger().critical("One instance of this plugin is already running on this server. You cannot run multiple instances of this plugin at the same time.");
            this.getPluginLoader().disablePlugin(this);
            return;
        }

        instance = this;

        File baseFile = new File(this.getDataFolder().getAbsolutePath());

        if(baseFile.exists()==false) {
            baseFile.mkdirs();
        }

        for(String resource : DEFAULT_RESOURCES) {
            this.saveResource(resource, false);
        }

        // load base config in cache by calling getConfig()
        // cache value instead of reading out the whole time (performance)
        Object worldsRaw = this.getConfig().get("challenge_worlds");

        if(worldsRaw instanceof Map) {
            Map<String, Boolean> worlds = (Map<String, Boolean>) worldsRaw;

            if(worlds.isEmpty()==false) {
                this.challengeWorlds = worlds;
            }
        } else if(worldsRaw instanceof ConfigSection) {
            ConfigSection section = (ConfigSection) worldsRaw;

            if(section.isEmpty()==false) {
                Map<String, Boolean> worlds = new HashMap<String, Boolean>();

                for(Map.Entry<String, Object> entry : section.entrySet()) {
                    String key = entry.getKey();
                    Object valueRaw = entry.getValue();

                    if(valueRaw==null) valueRaw = true;

                    if(valueRaw instanceof Boolean) {
                        worlds.put(key, (Boolean) valueRaw);
                    } else this.getLogger().alert("Couldn't load in field \"challenge_worlds\" the key \"" + key + "\", because value is type of class \"" + valueRaw.getClass().getSimpleName() + "\". Value must be boolean.");
                }

                if(worlds.isEmpty()==false) {
                    this.challengeWorlds = worlds;
                }
            }
        } else if(worldsRaw instanceof ArrayList) {
            ArrayList worlds = (ArrayList) worldsRaw;
            Map<String, Boolean> worldsLoaded = new HashMap<String, Boolean>();

            for(Object raw : worlds) {
                if(raw instanceof ConfigSection) {
                    ConfigSection section = (ConfigSection) raw;

                    for(Map.Entry<String, Object> entry : section.entrySet()) {
                        String key = entry.getKey();
                        Object rawVal = entry.getValue();

                        if(rawVal instanceof Boolean) {
                            worldsLoaded.put(key, (Boolean) rawVal);
                        } else this.getLogger().alert("Couldn't load in field \"challenge_worlds\" the key \"" + key + "\", because value is type of class \"" + rawVal.getClass().getSimpleName() + "\". Value must be boolean.");
                        break; // only care about first index
                    }
                } else this.getLogger().alert("Couldn't load in field \"challenge_worlds\" the value \"" + raw.toString() + "\". Value is invalid.");
            }

            if(worldsLoaded.isEmpty()==false) {
                this.challengeWorlds = worldsLoaded;
            }
        } else this.getLogger().alert("Couldn't load field \"challenge_worlds\", because it is type of class \"" + worldsRaw.getClass().getSimpleName() + "\".");

        for(Map.Entry<String, Boolean> entry : this.challengeWorlds.entrySet()) {
            this.getLogger().info("\"" + entry.getKey() + "\" is a challenge worlds and has damage " + (entry.getValue() == true ? "en" : "dis") + "abled.");
        }

        long reqDur = this.getConfig().getLong("requestDuration", 0);
        if(reqDur>0) {
            this.requestDuration = reqDur * 1000; // convert to ms
        }

        // load arenaConfig
        this.arenaConfig = new Config(this.getDataFolder() + "/arenaSettings.yml", Config.YAML);

        // load messages
        this.messages = new Config(this.getDataFolder() + "/messages.yml", Config.YAML);

        // load kits (statically into static cache)
        Kit.load(new Config(this.getDataFolder() + "/kits.yml", Config.YAML));

        // load arenas
        this.loadArenas();

        this.getLogger().info(TextFormat.GREEN + "Loaded!");
    }

    protected void loadArenas() {
        this.getLogger().info("Loading arenas...");

        Config config = this.getArenasConfig();

        for(Map.Entry<String, Object> entry : config.getAll().entrySet()) {
            String key = entry.getKey();

            if(this.spawnPoints.containsKey(key)==true) {
                this.getLogger().critical("Found during loading arenas multiple configuration data for map \"" + key + "\".");
                continue;
            }

            Object data = entry.getValue();

            // sometimes nukkit reads out configs weird, so just gurantee loading here
            if(data instanceof ConfigSection) {
                ConfigSection section = (ConfigSection) data;

                Location[] locations = new Location[2];

                try {
                    locations[0] = SetupData.string2pos(section.getString("spawnOne"));
                    locations[1] = SetupData.string2pos(section.getString("spawnTwo"));
                } catch(Exception e) {
                    this.getLogger().critical("Couldn't read data from arena \"" + key + "\". Exception details:");
                    e.printStackTrace();
                    continue;
                }
                this.spawnPoints.put(key, locations);
            } else if(data instanceof LinkedTreeMap) {
                LinkedTreeMap<String, Object> map = (LinkedTreeMap) data;

                Location[] locations = new Location[2];

                try {
                    locations[0] = SetupData.string2pos((String) map.get("spawnOne"));
                    locations[1] = SetupData.string2pos((String) map.get("spawnTwo"));
                } catch(Exception e) {
                    this.getLogger().critical("Couldn't read data from arena \"" + key + "\". Exception details:");
                    e.printStackTrace();
                    continue;
                }
                this.spawnPoints.put(key, locations);
            }
        }
        this.getLogger().info("Loaded arenas!");
    }

    @Override
    public void onEnable() {
        this.getLogger().info(TextFormat.DARK_GREEN + "Enabling...");

        // (Main plugin, String name, String description, String usageMessage, String[] aliases)
        this.command = new SetupCommand(this, "1vs1", "1vs1 setup command.", "/1vs1 help", new String[] { "ovosetup" }, "1vs1.command.setup");
        this.getServer().getCommandMap().register("1vs1", this.command);

        this.getServer().getPluginManager().registerEvents(this, this);


        Position defaultSpawn = Server.getInstance().getDefaultLevel().getSafeSpawn();

        this.defaultSpawn.setComponents(defaultSpawn.x, defaultSpawn.y, defaultSpawn.z);
        this.defaultSpawn.setLevel(defaultSpawn.level);

        this.getLogger().info(TextFormat.DARK_GREEN + "Enabled!");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDamage(EntityDamageEvent event) {
        if(this.spawnPoints.size()<=0) {
            // nothing setup -> no challenging possible
            return;
        }

        Entity victim = event.getEntity();


        if(victim instanceof Player) {
            //this.getLogger().info("victim is player");

            Player victimPlayer = (Player) victim;
            Level level = victim.level;

            if(event instanceof EntityDamageByEntityEvent) {
                //this.getLogger().info("entity vs entity");

                EntityDamageByEntityEvent castedEvent = (EntityDamageByEntityEvent) event;
                Entity attacker = castedEvent.getDamager();


                boolean attackedByProjectile = false;

                if(attacker instanceof EntityProjectile) {
                    EntityProjectile projectile = (EntityProjectile) attacker;
                    attacker = projectile.shootingEntity;

                    attackedByProjectile = true;
                }


                if(attacker instanceof Player) {
                    Player attackerPlayer = (Player) attacker;

                    // check for attack cooldown here
                    if(this.hasAttackCooldown(attackerPlayer)==true) {
                        event.setCancelled(true);
                        return;
                    }

                    if(this.getPreparation(attackerPlayer)!=null || this.getPreparation(victimPlayer)!=null) {
                        // prevent being able to challenge, damage or kill players who are in preparation
                        event.setCancelled(true);
                        return;
                    }

                    // search if they are in a match
                    for(Session session : this.sessions.values()) {
                        if(session.inSession(attackerPlayer)/* && session.inSession(victimPlayer) not needed cause one of them must be inside */) {
                            session.onDamage(event, attackerPlayer, victimPlayer);
                            // stop execution here, cause session will continue handling
                            return;
                        }
                    }

                    // if code continues, there must have something happened in lobby like e.g:
                    // challenging in lobby
                    // check if it is a challenge world
                    //this.getLogger().info("checking for challenge");
                    if(this.challengeWorlds==null||this.challengeWorlds.containsKey(level.getFolderName())==true) {
                        event.setCancelled(true);

                        if(attackedByProjectile==true) {
                            // tap to challenge. Disallow challenging by hitting with arrow, snowball, etc.
                            return;
                        }

                        String attackerName = attackerPlayer.getName();

                        String victimName = victim.getName();
                        Map<String, Long> challengedDataVictim = this.requests.get(victimName);
                        Map<String, Long> challengeDataAttacker = this.requests.get(attackerName);

                        if(challengeDataAttacker==null) {
                            challengeDataAttacker = new HashMap<String, Long>();
                        } else if(challengeDataAttacker.containsKey(victimName)==true) {
                            // anti spam

                            if(this.requestDuration!=null) {
                                Long time = challengeDataAttacker.get(victimName);

                                if(time!=null) {
                                    // search if challenge is still active
                                    if(TimeHelper.hasTimePassed(time, this.requestDuration)==true) {
                                        // remove and tell already that it was removed, so next if statement knows without wasting performance
                                        challengeDataAttacker.remove(victimName);
                                    } else {
                                        this.sendMessageByKey(attackerPlayer, "already_challenged", new HashMap<String, String>() {{
                                            put("{player}", victimName);
                                        }});
                                        return;
                                    }
                                }
                            } else {
                                this.sendMessageByKey(attackerPlayer, "already_challenged", new HashMap<String, String>() {{
                                    put("{player}", victimName);
                                }});
                                return;
                            }
                        }

                        boolean removed = false;

                        if(challengedDataVictim!=null) {
                            if(this.requestDuration!=null) {
                                Long time = challengedDataVictim.get(attackerName);

                                if(time!=null) {
                                    // search if challenge is still active
                                    if(TimeHelper.hasTimePassed(time, this.requestDuration)==true) {
                                        // remove and tell already that it was removed, so next if statement knows without wasting performance
                                        challengedDataVictim.remove(attackerName);
                                        challengedDataVictim.remove(attackerName);
                                        removed = true;
                                    }
                                }
                            }
                        }

                        if(challengedDataVictim==null || removed==true || challengedDataVictim.containsKey(attackerName)==false) {
                            // victim never challenged or request expired -> attacker challenging him
                            // attacker wants to duel against victim; send attacker confirmation
                            this.sendMessageByKey(attackerPlayer, "challenged_sender", new HashMap<String, String>() {{
                                put("{player}", victimName);
                            }});
                            // notify victim
                            this.sendMessageByKey(victimPlayer, "challenged_receiver", new HashMap<String, String>() {{
                                put("{player}", attackerName);
                            }});
                            // save value

                            challengeDataAttacker.put(victimName, TimeHelper.getTime());

                            this.requests.put(attackerName, challengeDataAttacker);
                        } else {
                            // time check has been done and all conditions are met -> start preparations
                            this.requests.remove(attackerName);
                            this.requests.remove(victimName);

                            this.prepareMatch(attackerPlayer, victimPlayer);
                        }
                    }
                }
            } else {
                // check if player was in match
                for(Session session : this.sessions.values()) {
                    if(session.inSession(victimPlayer)) {
                        session.onDamageNonPlayer(event, victimPlayer);
                        // stop execution here, cause session will continue handling and session is already found
                        //this.getLogger().info("damage received in another way -> redirecting to his session");
                        return;
                    }
                }

                // check if damage has been applied in challenge world
                if(this.challengeWorlds==null||this.challengeWorlds.getOrDefault(level.getFolderName(), true)==true) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        String name = player.getName();

        for(Session session : this.sessions.values()) {
            if(session.inSession(player)) {
                session.onQuit(event);
                break;
            }
        }

        // remove leftover data to prevent data leaks
        DuelPreparation preparation = this.getPreparation(name);

        if(preparation!=null) {
            preparation.getTask().cancel();

            Player opponent = preparation.getOpposite(name);
            this.checkKitPick(player, opponent, player);

            this.removePreparation(name);
        }
        this.formIds.remove(name);
        this.kit_selection.remove(name);
        this.requests.remove(name);
        this.damageCooldown.remove(name);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();

        for(Session session : this.sessions.values()) {
            if(session.inSession(player)) {
                session.onTeleport(event);
                break;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        for(Session session : this.sessions.values()) {
            if(session.inSession(player)) {
                session.onDeath(event);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onCommandPreProcess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();

        for(Session session : this.sessions.values()) {
            if(session.inSession(player)) {
                session.getArena().onCommandPreProcess(event);
                break;
            }
        }
    }

    protected void prepareMatch(Player playerOne, Player playerTwo) {
        // send both same message
        String pOneName = playerOne.getName();
        String pTwoName = playerTwo.getName();

        this.sendMessageByKey(playerOne, "select_kit", new HashMap<String, String>() {{
            put("{player}", pTwoName);
        }});
        this.sendMessageByKey(playerTwo, "select_kit", new HashMap<String, String>() {{
            put("{player}", pOneName);
        }});

        this.getServer().getScheduler().scheduleDelayedTask(new Task() {
            @Override
            public void onRun(int i) {
                Main.this.showKitPickForm(playerOne, playerTwo);
            }
        }, this.getConfig().getInt("delay_kit_pick", 0));
    }

    protected void showKitPickForm(Player playerOne, Player playerTwo) {
        String pOneName = playerOne.getName();
        String pTwoName = playerTwo.getName();


        this.formIds.put(pOneName, playerOne.showFormWindow(Kit.getKitPickForm()));
        this.formIds.put(pTwoName, playerTwo.showFormWindow(Kit.getKitPickForm()));

        DuelPreparation duelPreparation = new DuelPreparation(playerOne, playerTwo);

        CheckKitPickedTask checkTask = new CheckKitPickedTask(this, playerOne, playerTwo);
        duelPreparation.assignCheckTask(checkTask);

        this.getServer().getScheduler().scheduleDelayedTask(checkTask, this.getConfig().getInt("kit_pick_time", 160));

        this.preparations.add(duelPreparation);
    }

    public void checkKitPick(Player playerOne, Player playerTwo) {
        this.checkKitPick(playerOne, playerTwo, null);
    }

    /**
     * Checks the kit pick, if everything is right match starts
     * @param playerOne
     * @param playerTwo
     * @param declinedBy
     */
    public void checkKitPick(Player playerOne, Player playerTwo, Player declinedBy) {
        if(declinedBy instanceof Player) {
            if(declinedBy.equals(playerOne)==false && declinedBy.equals(playerTwo)==false) {
                throw new RuntimeException("Player argument declined by must be one of two challenging players.");
            }
        }

        String oneName = playerOne.getName();
        String twoName = playerTwo.getName();

        // if checkKitPick gets called, ignore other forms coming in by deleting key
        this.formIds.remove(oneName);
        this.formIds.remove(twoName);

        Kit kitOne = this.kit_selection.get(oneName);
        Kit kitTwo = this.kit_selection.get(twoName);

        // remove since we have already kits
        this.kit_selection.remove(oneName);
        this.kit_selection.remove(twoName);

        this.removeRequest(oneName, twoName);
        this.removeRequest(twoName, oneName);

        DuelPreparation preparation = this.getPreparation(oneName);

        if(preparation!=null) {
            preparation.getTask().cancel();
        }

        this.removePreparation(oneName, twoName);

        if(declinedBy!=null) {
            String declinedByName = declinedBy.getName();

            // declinedBy didn't  pick
            Player opposite = declinedBy.equals(playerOne) == true ? playerTwo : playerOne;

            this.sendMessageByKey(declinedBy, "you_aborted_selection", new HashMap<String, String>() {{
                put("{player}", opposite.getName());
            }});
            this.sendMessageByKey(opposite, "aborted_selection", new HashMap<String, String>() {{
                put("{player}", declinedByName);
            }});

            return;
        }

        if(kitOne==null && kitTwo==null) {
            // both didn't pick
            this.sendMessageByKey(playerOne, "both_abored_selection");
            this.sendMessageByKey(playerTwo, "both_abored_selection");

            return;
        }

        if(kitOne!=null) {
            if(kitTwo!=null) {
                // start match; send messages
                String map = this.getRandomMap();

                File server = new File(Server.getInstance().getFilePath());
                File worlds = new File(server + "/" + Arena.WORLDS_DIR_NAME);

                // read it out from arena config
                File file = new File(worlds + "/" + map + "/arenaSettings.yml");

                Config config = null;

                if(file.exists()==true) {
                    config = new Config(file, Config.YAML);

                    if(this.isValidArenaConfig(config)==false) {
                        config = this.getDefaultArenaConfig();
                    }
                } else config = this.getDefaultArenaConfig();

                String representationName = map;
                Object rawOverrideLevelName = config.get("overrideLevelName", null);

                if(rawOverrideLevelName instanceof String) {
                    String casted = (String) rawOverrideLevelName;

                    if(casted.isEmpty()==false) {
                        representationName = casted;
                    }
                }

                String finalRepresentationName = representationName;

                this.sendMessageByKey(playerOne, "starting_match", new HashMap<String, String>() {{
                    put("{player}", twoName);
                    put("{level}", finalRepresentationName);
                    put("{kit}", kitTwo.getDisplayName());
                }});
                this.sendMessageByKey(playerTwo, "starting_match", new HashMap<String, String>() {{
                    put("{player}", oneName);
                    put("{level}", finalRepresentationName);
                    put("{kit}", kitOne.getDisplayName());
                }});

                int id = this.sessions.size();

                Session session = new Session(this, id, playerOne, kitOne, playerTwo, kitTwo, map, representationName);
                this.sessions.put(id, session);
            } else {
                // p2 didn't pick

                this.sendMessageByKey(playerOne, "aborted_selection", new HashMap<String, String>() {{
                    put("{player}", twoName);
                }});
                this.sendMessageByKey(playerTwo, "you_aborted_selection", new HashMap<String, String>() {{
                    put("{player}", oneName);
                }});
            }
        } else {
            // p1 didn't  pick
            this.sendMessageByKey(playerOne, "you_aborted_selection", new HashMap<String, String>() {{
                put("{player}", twoName);
            }});
            this.sendMessageByKey(playerTwo, "aborted_selection", new HashMap<String, String>() {{
                put("{player}", oneName);
            }});
        }
    }

    public boolean removePreparation(String one) {
        return this.removePreparation(one, null);
    }

    /**
     * Removes a duel preparation if found
     * @param one
     * @param two fallback, if nothing can be found with name one
     * @return success
     */
    public boolean removePreparation(String one, String two) {
        DuelPreparation dP = this.getPreparation(one);

        if(dP==null) {
            dP = this.getPreparation(two);
        }

        if(dP==null) {
            return false;
        }

        this.preparations.remove(dP);
        return true;
    }

    public String getRandomMap() {
        String[] maps = this.spawnPoints.keySet().toArray(new String[] {});

        return maps[Random.getRandomNumber(0, maps.length-1)];
    }

    public boolean isValidArenaConfig(Config config) {
        // for now nothing gets parsed, since there is no fancy data inside yet
        return true;
    }

    public Config getMessages() {
        return messages;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onResponse(PlayerFormRespondedEvent event) {
        Player player = event.getPlayer();
        String name = player.getName();
        int id = event.getFormID();

        if(this.formIds.containsValue(id)==true) {
            // kit form
            FormResponseSimple response = (FormResponseSimple) event.getResponse();

            if(response==null) {
                // aborted, just check formally
                this.getLogger().error("Checking because aborted.");

                DuelPreparation dP = this.getPreparation(name);

                if(dP!=null) {
                    Player opponent = dP.getOpposite(name);

                    this.checkKitPick(player, opponent, player);
                } else this.getLogger().critical(player.getName() + " closed kit choose UI, but never challenged someone. Player might be using a modded client.");
                return;
            }

            int kitSelection = response.getClickedButtonId();

            String buttonName = Kit.getKitPickForm().getButtons().get(kitSelection).getText();
            Kit kit = Kit.getKitByDisplayName(buttonName);

            this.getLogger().error("Got response from " + name + ", buttonID=" + kitSelection + "; ButtonName: " + buttonName);

            if(kit==null) {
                this.getLogger().critical("Got non-existent kit with button name \"" + buttonName + "\". " + name + " maybe uses a modded client.");
                return;
            }

            this.sendMessageByKey(player, "successfully_picked", new HashMap<String, String >() {{
                put("{kit}", kit.getDisplayName());
            }});

            this.kit_selection.put(name, kit);
            this.formIds.remove(name);

            DuelPreparation dP = this.getPreparation(name);

            if(dP!=null) {
                Player opponent = dP.getOpposite(name);

                this.sendMessageByKey(opponent, "opponent_successfully_picked", new HashMap<String, String>() {{
                    put("{kit}", kit.getDisplayName());
                }});

                if(this.kit_selection.containsKey(opponent.getName())==true) {
                    // opponent also has key; cancel task
                    dP.getTask().cancel();

                    // execute now check Kit to start match; it's not even a check anymore, since we know both picked kits
                    this.checkKitPick(player, opponent);
                }
            }
        }/* else {
            this.sendMessageByKey(player, "you_didnt_pick");
        }*/
    }

    /*@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onUnload(LevelUnloadEvent event) {
        Level level = event.getLevel();
    }*/

    public void sendMessageByKey(Player player, String key) {
        this.sendMessageByKey(player, key, null);
    }

    /**
     * Send message to one player by its key
     * @param player
     * @param key
     * @param replacements
     */
    public void sendMessageByKey(Player player, String key, Map<String, String> replacements) {
        String message = this.messages.get(key, null);

        if(message!=null) {
            if(replacements!=null) {
                for(Map.Entry<String, String> entry : replacements.entrySet()) {
                    message = message.replace(entry.getKey(), entry.getValue());
                }
            }
            sendMessage(player, message);
        }
    }

    public void broadcastMessage(String key) {
        this.broadcastMessage(key, new HashSet<>(this.getServer().getOnlinePlayers().values()), null, null);
    }

    public void broadcastMessage(String key, Collection<Player> receivers) {
        this.broadcastMessage(key, receivers, null, null);
    }

    public void broadcastMessage(String key, Collection<Player> receivers, Map<String, String> replacements) {
        this.broadcastMessage(key, receivers, null, replacements);
    }

    /**
     * Broadcast a message
     * @param key message key
     * @param receivers
     * @param exceptions excluded receivers
     * @param replacements string replacements
     */
    public void broadcastMessage(String key, Collection<Player> receivers, Collection<Player> exceptions, Map<String, String> replacements) {
        String message = this.messages.get(key, null);

        if(message!=null) {
            if(exceptions!=null) {
                receivers.removeAll(exceptions);
            }

            if(replacements!=null) {
                for(Map.Entry<String, String> entry : replacements.entrySet()) {
                    message = message.replace(entry.getKey(), entry.getValue());
                }
            }

            for(Player receiver : receivers) {
                sendMessage(receiver, message);
            }
        }
    }

    /**
     * Send message by it's base form
     * @param player receiver
     * @param message unformatted like this "chat:Message"
     */
    public static void sendMessage(Player player, String message) {
        String[] data = message.split(":", 2);

        if(data.length==1) {
            // error resistance
            sendMessage(player, data[0], "chat");
        } else sendMessage(player, data[1], data[0]);
    }

    /**
     * Send a basic message
     * @param player receiver
     * @param message content
     * @param type message type(/channel)
     */
    public static void sendMessage(Player player, String message, String type) {
        switch(type) {
            case "chat":
                player.sendMessage(message);
            break;
            case "popup":
                player.sendPopup(message);
            break;
            case "tip":
                player.sendTip(message);
            break;
            case "title":
                String[] msg = message.split(":", 2);
                player.sendTitle(msg[0], msg.length >= 2 ? msg[1] : "");
            break;
            default:
                System.err.println("Cannot send message "+ "\"" + message + "\" to player " + player.getName() + ", due to unknown type (\"" + type + "\")" + " of message.");
            break;
        }
    }

    /**
     * Get the default arena config
     * @return Config
     */
    public Config getDefaultArenaConfig() {
        return this.arenaConfig;
    }

    /**
     * Get a session instance by its id
     * @param id
     * @return Session
     */
    public Session getSession(int id) {
        return this.sessions.get(id);
    }

    /**
     * Get all active sessions
     * @return Map<Integer, Session>
     */
    public Map<Integer, Session> getSessions() {
        return this.sessions;
    }

    public void notifyEndOfSession(Session ended) {
        for(Map.Entry<Integer, Session> entry : this.sessions.entrySet()) {
            if(ended.equals(entry.getValue())==true) {
                this.sessions.remove(entry.getKey());
                break;
            }
        }
    }

    public void notifyEndOfSession(int id) {
        this.sessions.remove(id);
    }

    /**
     * Get Spawnpoints of a map
     * @param map
     * @return 2-value array with spawnpoints
     */
    public Location[] getSpawnpoints(String map) {
        return this.spawnPoints.get(map);
    }

    /**
     * Get spawnspoints map
     * @return Map<String, Location[]>
     */
    public Map<String, Location[]> getSpawnpointsData() {
        return this.spawnPoints;
    }

    public boolean removeRequest(Player player, Player from) {
        return this.removeRequest(player.getName(), from.getName());
    }

    public boolean removeRequest(Player player, String from) {
        return this.removeRequest(player.getName(), from);
    }

    public boolean removeRequest(String player, Player from) {
        return this.removeRequest(player, from.getName());
    }

    /**
     * Removes request, if it hasn't been already. Sent to "player" (e.g. Steve) by "from" (e.g. Alex). So Alex's challenge to Steve will be removed in that sense.
     * If time has passed entry will still be removed.
     * @param player
     * @param from
     * @return result - If time ran out but from was still in map, this will return false cause request was invalid
     */
    public boolean removeRequest(String player, String from) {
        Map<String, Long> requestsFromPlayer = this.requests.get(player);

        if(requestsFromPlayer!=null && requestsFromPlayer.containsKey(from)==false) {
            Long timestamp = requestsFromPlayer.get(from);
            requestsFromPlayer.remove(from);

            this.requests.put(player, requestsFromPlayer);


            if(this.requestDuration!=null) {
                if(TimeHelper.hasTimePassed(timestamp, this.requestDuration)==false) {
                    return true;
                }
            } else return true;
        }
        return false;
    }

    /**
     * Get DuelPreparation object, if existent by player instance
     * @param player
     * @return DuelPreparation
     */
    public DuelPreparation getPreparation(Player player) {
        return this.getPreparation(player.getName());
    }

    /**
     * Get DuelPreparation object by player name
     * @param name
     * @return DuelPreparation
     */
    public DuelPreparation getPreparation(String name) {
        for(DuelPreparation dP : this.preparations) {
            if(dP.belongsTo(name)==true) {
                return dP;
            }
        }
        return null;
    }

    public static final long ATTACK_COOLDOWN = 1500; // 1.5 seconds

    public void giveAttackCooldown(Player player) {
        this.giveAttackCooldown(player.getName());
    }

    public void giveAttackCooldown(String name) {
        this.giveAttackCooldown(name, System.currentTimeMillis() + ATTACK_COOLDOWN);
    }

    public void giveAttackCooldown(Player player, Long till) {
        this.giveAttackCooldown(player.getName(), till);
    }

    /**
     * Give player attack cooldown (used after match, so player doesn't kills other one
     * @param name player name
     * @param till System.currentTimeMillis() + ms how long cooldown should last
     */
    public void giveAttackCooldown(String name, Long till) {
        this.damageCooldown.put(name, till);
    }

    public boolean hasAttackCooldown(Player player) {
        return this.hasAttackCooldown(player.getName());
    }

    /**
     * Returns if player has attack cooldown and if cooldown passed, it'll dynamically removes player from list
     * @param name player
     * @return result
     */
    public boolean hasAttackCooldown(String name) {
        Long till = this.damageCooldown.get(name);

        if(till!=null) {
            if(TimeHelper.hasTimePassed(till)==true) {
                this.damageCooldown.remove(name);
                return false;
            } else return true;
        }
        return false;
    }

    // countdown stop handling needed events
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onMove(PlayerMoveEvent event) {
        this.processEvent(event.getPlayer(), event);
    }

    /*@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        this.processEvent(event.getPlayer(), event);
    }*/

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onConsume(PlayerItemConsumeEvent event) {
        this.processEvent(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBedEnter(PlayerBedEnterEvent event) {
        this.processEvent(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDrop(PlayerDropItemEvent event) {
        this.processEvent(event.getPlayer(), event);
    }


    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        this.processEvent(event.getPlayer(), event);
    }


    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBucketFill(PlayerBucketFillEvent event) {
        this.processEvent(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onEat(PlayerEatFoodEvent event) {
        this.processEvent(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onFoodChange(PlayerFoodLevelChangeEvent event) {
        this.processEvent(event.getPlayer(), event);
    }

    /**
     * Pass event if it should be cancelled or handled
     * @param player
     * @param event
     * @return boolean - if event has been cancelled/prevented or not
     */
    public boolean processEvent(Player player, Event event) {
        for(Session session : this.sessions.values()) {
            if(session.inSession(player)==true) {
                if(session.getArena().matchStarted()==false) {
                    if(event instanceof PlayerMoveEvent) {
                        PlayerMoveEvent moveEvent = (PlayerMoveEvent) event;

                        Location from = moveEvent.getFrom();
                        Location to = moveEvent.getTo();

                        from.pitch = to.pitch;
                        from.yaw = to.yaw;

                        moveEvent.setTo(from);
                    } else {
                        event.setCancelled(true);
                    }
                    return true;
                }
                break;
            }
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();

        for(Session session : this.sessions.values()) {
            if(session.inSession(player)==true) {
                session.getArena().onBlockPlace(event);
                break;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        for(Session session : this.sessions.values()) {
            if(session.inSession(player)==true) {
                session.getArena().onBlockBreak(event);
                break;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        for(Session session : this.sessions.values()) {
            if(session.inSession(player)==true) {
                //this.getLogger().info("INTERACTION HAS BEEN MADE AND WILL BE REDIRECTED");
                session.getArena().onInteract(event);
                break;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onIllegalMove(PlayerInvalidMoveEvent event) {
        Player player = event.getPlayer();

        for(Session session : this.sessions.values()) {
            if(session.inSession(player)==true) {
                //this.getLogger().info("INTERACTION HAS BEEN MADE AND WILL BE REDIRECTED");
                session.getArena().onInvalidMoveEvent(event);
                break;
            }
        }
    }

    @Override
    public void onDisable() {
        this.getLogger().info(TextFormat.RED + "Disabling...");

        for(Session session : this.sessions.values()) {
            this.getLogger().info("Stopping session from players " + session.getPlayerOne().getName() + " and " + session.getPlayerTwo().getName() + ".");
            session.onDisable();
        }

        this.getLogger().info(TextFormat.RED + "Disabled.");
    }

    private void disablePlugin() {
        this.getPluginLoader().disablePlugin(this);
    }


    // for testing
    public static void main(String[] args) {
        System.out.println("Testing tandem formKey method...");

        Map<String, String> tandem = new LinkedHashMap<String, String>() {
            {
                put("Alpha", "Beta");
                put("Beta", "Alpha");
                put("Caesar", "Zenturio");
            }
        };

        for(Map.Entry<String, String> entry : tandem.entrySet()) {
            System.out.println("Building tandem between name \"" + entry.getKey() + "\" and \"" + entry.getValue() + "\".");
            System.out.println("Result: " + formKey(entry.getKey(), entry.getValue()) + "\n");
        }

        System.out.println("Test done!");
    }
}
