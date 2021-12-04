package com.sero583.onevsonerm.session;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.event.block.BlockBreakEvent;
import cn.nukkit.event.block.BlockPlaceEvent;
import cn.nukkit.event.player.PlayerCommandPreprocessEvent;
import cn.nukkit.event.player.PlayerInteractEvent;
import cn.nukkit.event.player.PlayerInvalidMoveEvent;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBucket;
import cn.nukkit.level.Level;
import cn.nukkit.level.Location;
import cn.nukkit.scheduler.AsyncTask;
import cn.nukkit.scheduler.Task;
import cn.nukkit.utils.Config;
import com.sero583.onevsonerm.Main;
import com.sero583.onevsonerm.exception.runtime.BadNameGeneratedException;
import com.sero583.onevsonerm.task.RemoveLevelTaskDelayed;
import com.sero583.onevsonerm.task.RetryRemoveLevelTask;
import com.sero583.onevsonerm.utils.Random;
import com.sero583.onevsonerm.utils.TimeHelper;
import org.iq80.leveldb.util.FileUtils;

import java.io.File;
import java.util.HashMap;
import java.util.List;

/**
 * @author Serhat G. (sero583)
 */
public class Arena {
    public static final int ONE_SECOND_TICK = 20;

    public static final int RANDOM_NAME_LENGTH = 12;
    public static String WORLDS_DIR_NAME = "worlds";

    protected final String folderName;

    protected final Session session;
    protected String copyName;

    protected TickTask tickTask;

    // Arena settings
    protected int countdown = 0;
    protected int time = 0;
    protected List<String> blockedCommands;
    protected boolean blockBreak;
    protected boolean blockPlace;
    protected boolean blockInteractions;

    protected Level level_cache = null;

    protected String overrideLevelName = null;

    private boolean deleted = false;
    private boolean allowIllegalMove = false;

    // TODO: Rng name generation, copy folder and load
    public Arena(Session session, String folderName) {
        this.session = session;
        this.folderName = folderName;

        CopyMapTask copyMapTask = new CopyMapTask(session.getId(), folderName);
        this.session.plugin.getServer().getScheduler().scheduleAsyncTask(this.session.plugin, copyMapTask);
    }

    protected void arenaCopied(String copyName) {
        this.copyName = copyName;

        File server = new File(Server.getInstance().getFilePath());
        File worlds = new File(server + "/" + WORLDS_DIR_NAME);
        File file = new File(worlds + "/" + this.copyName + "/arenaSettings.yml"); // TODO check if pre / is required

        Config config = null;
        if(file.exists()==true) {
            config = new Config(file, Config.YAML);

            if(this.session.getPlugin().isValidArenaConfig(config)==false) {
                this.session.getPlugin().getLogger().critical("Couldn't load config from arena " + this.folderName + ". Using default arena configuration.");

                config = this.session.getPlugin().getDefaultArenaConfig();
            }
        } else config = this.session.getPlugin().getDefaultArenaConfig();

        List<String> loadedBlockedCommandsValue = config.isList("blockedCommands") == true ? config.getStringList("blockedCommands") : null;

        this.time = config.getInt("duration", 0);
        this.countdown = config.getInt("countdown", 0);
        this.blockedCommands = loadedBlockedCommandsValue;
        this.blockBreak = config.getBoolean("blockBreak", false);
        this.blockPlace = config.getBoolean("blockPlace", false);
        this.blockInteractions = config.getBoolean("blockInteractions", false);
        this.allowIllegalMove = config.getBoolean("allowIllegalMove", false);

        Object rawOverrideLevelName = config.get("overrideLevelName", null);

        if(rawOverrideLevelName instanceof String) {
            String casted = (String) rawOverrideLevelName;

            if(casted.isEmpty()==false) {
                this.overrideLevelName = casted;
            }
        }

        session.getPlugin().getServer().loadLevel(this.copyName);

        this.initializePlayers();

        if(this.time > 0 || this.countdown > 0) {
            this.tickTask = new TickTask(this);
            this.session.plugin.getServer().getScheduler().scheduleRepeatingTask(this.tickTask, ONE_SECOND_TICK);
        }
    }

    protected void initializePlayers() {
        Location[] spawnpoints = this.session.getPlugin().getSpawnpoints(this.folderName).clone();

        int rng = Random.getRandomNumber(0, 1);

        Location spawnpointOne = spawnpoints[rng];
        spawnpointOne.level = this.getLevel();

        this.session.getPlayerOne().teleport(spawnpointOne);

        Location spawnpointTwo = spawnpoints[(rng == 1 ? 0 : 1)];
        spawnpointTwo.level = this.getLevel();

        this.session.getPlayerTwo().teleport(spawnpointTwo);

        this.session.kitOne.equip(this.session.one);
        this.session.kitTwo.equip(this.session.two);
    }

    public void setOverrideLevelName(String newName) {
        this.overrideLevelName = newName;
    }

    /**
     * Copy level with a random name
     * @param folderName
     * @return Folder name of copied Level
     */
    public static String copyLevel(String folderName) {
        String name = Random.randomString(RANDOM_NAME_LENGTH);

        try {
            copyLevel(folderName, name);
        } catch(BadNameGeneratedException e) {
            // try until good name is found
            return copyLevel(folderName);
        }
        return name;
    }

    /**
     * Create copy of level with choosing its new folder name
     * @param folderName
     * @param newName
     */
    public static void copyLevel(String folderName, String newName) {
        File server = new File(Server.getInstance().getFilePath());
        File worlds = new File(server + "/" + WORLDS_DIR_NAME);

        // scan if something exists already under the name
        for(File world : worlds.listFiles()) {
            if(world.isDirectory() && world.getName().equals(newName)) {
                System.err.println(newName + " and " + world.getName() + " are directory and equal.");
                throw new BadNameGeneratedException("Tried to override existing world folder " + newName + " with level " + folderName);
            }
        }

        // now really copy
        File origin = new File(worlds + "/" + folderName);
        File dest = new File(worlds + "/" + newName);

        if(dest.exists()==false) {
            dest.mkdir();
        }

        FileUtils.copyRecursively(origin, dest);
    }

    /**
     * Removes created copy file. Call only when level has been unloaded, otherwise it may errors.
     */
    public void removeLevel() {
        File levelFolder = new File(this.session.getPlugin().getServer().getFilePath() + "/" + WORLDS_DIR_NAME + "/" + this.copyName + "/");

        if(FileUtils.deleteRecursively(levelFolder)==false) {
            System.err.println("Couldn't delete folder " + levelFolder.getPath() + ". Trying later again...");

            this.session.plugin.getServer().getScheduler().scheduleDelayedTask(new RetryRemoveLevelTask(this.session.plugin, levelFolder), RetryRemoveLevelTask.DELAY_BETWEEN);
        }
    }


    public void tick() {
        if(this.countdown>0) {
            // send countdown progression
            this.session.plugin.broadcastMessage("countdown_tick", this.session.getPlayersList(), new HashMap<String, String>() {{
                put("{time}", TimeHelper.displayTime(Arena.this.countdown));
            }});

            this.countdown--;

            if(this.countdown==0) {
                // send start message
                this.session.plugin.broadcastMessage("started", this.session.getPlayersList());

                if(this.time<=0) {
                    // stop task, if match is not time-limited
                    this.tickTask.stop();
                    this.tickTask = null;
                }
            }
        } else if(this.time>0) {
            // send time progression
            this.session.plugin.broadcastMessage("time_tick", this.session.getPlayersList(), new HashMap<String, String>() {{
                put("{time}", TimeHelper.displayTime(Arena.this.time));
            }});

            this.time--;

            if(this.time==0) {
                // choose winner

                this.tickTask.stop();
                this.tickTask = null;

                this.session.end(Session.EndCause.TIMEOUT);
            }
        }
    }

    /**
     * Returns if match has started
     * @return boolean
     */
    public boolean matchStarted() {
        return this.countdown <= 0;
    }

    public void delete() {
        if(this.deleted==false) {
            if(this.tickTask!=null) {
                this.tickTask.stop();
                this.tickTask = null;
            }

            for(Player player : this.getLevel().getPlayers().values()) {
                // maybe there is someone still in there, for safety just loop over it
                player.teleport(this.session.plugin.getServer().getDefaultLevel().getSafeSpawn());
            }

            this.getLevel().unload(true);

            //this.removeLevel();
            this.session.plugin.getServer().getScheduler().scheduleDelayedTask(new RemoveLevelTaskDelayed(this), RetryRemoveLevelTask.DELAY_BETWEEN);

            this.deleted = true;
        }
    }

    public Level getLevel() {
        if(this.level_cache==null) {
            this.level_cache = this.session.plugin.getServer().getLevelByName(this.copyName);
        }
        return this.level_cache;
    }

    public String getLevelName() {
        return this.overrideLevelName == null ? this.getLevel().getName() : this.overrideLevelName;
    }

    public String getCopyName() {
        return this.copyName;
    }

    public boolean onCommandPreProcess(PlayerCommandPreprocessEvent event) {
        if(this.matchStarted()==false) {
            event.setCancelled();
        } else if(this.blockedCommands!=null) {
            String message = event.getMessage();
            message = message.substring(1); // remove /

            String data[] = message.split(" ");
            String command = data[0];

            if(this.blockedCommands.contains(command)==true) {
                event.setCancelled(true);
            }
        }
        return event.isCancelled();
    }

    public boolean onBlockBreak(BlockBreakEvent event) {
        if(this.matchStarted()==false) {
            event.setCancelled();
        } else if(this.blockBreak==false) {
            event.setCancelled();
        }
        return event.isCancelled();
    }

    public boolean onBlockPlace(BlockPlaceEvent event) {
        if(this.matchStarted()==false) {
            event.setCancelled();
        } else if(this.blockPlace==false) {
            event.setCancelled();
        }
        return event.isCancelled();
    }


    public boolean onInvalidMoveEvent(PlayerInvalidMoveEvent event) {
        if(this.allowIllegalMove==true) {
            event.setCancelled();
        }
        return event.isCancelled();
    }


    public boolean onInteract(PlayerInteractEvent event) {
        if(this.matchStarted()==false) {
            event.setCancelled();
        } else if(this.blockInteractions==false) {
            PlayerInteractEvent.Action action = event.getAction();
            // check what happened

            if(action==PlayerInteractEvent.Action.LEFT_CLICK_AIR || action==PlayerInteractEvent.Action.RIGHT_CLICK_AIR) { // allow those
                return event.isCancelled();
            } else if(action==PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) {
                Item item = event.getItem();

                if(item instanceof ItemBucket) {
                    return event.isCancelled();
                }
            }
            event.setCancelled();
        }
        return event.isCancelled();
    }

    public void onDisable() {
        if(this.tickTask!=null) {
            this.tickTask.stop();
        }
        this.delete();
    }

    protected class TickTask extends Task {
        private Arena arena;

        public TickTask(Arena arena) {
            this.arena = arena;
        }

        @Override
        public void onRun(int i) {
            this.arena.tick();
        }

        public void stop() {
            this.arena.session.getPlugin().getServer().getScheduler().cancelTask(this.getTaskId());
        }
    }

    protected class CopyMapTask extends AsyncTask {
        protected int sessionId;
        protected String mapName;

        public CopyMapTask(int sessionId, String mapName) {
            this.sessionId = sessionId;
            this.mapName = mapName;
        }

        @Override
        public void onRun() {
            this.setResult(Arena.copyLevel(this.mapName));
        }

        @Override
        public void onCompletion(Server server) {
            Main main = Main.getInstance();
            String copyName = (String) this.getResult();

            Session session = main.getSession(this.sessionId);

            session.getArena().arenaCopied(copyName);
        }
    }
}
