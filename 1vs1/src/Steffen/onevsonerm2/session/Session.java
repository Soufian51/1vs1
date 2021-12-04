package com.sero583.onevsonerm.session;

import cn.nukkit.Player;
import cn.nukkit.PlayerFood;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.player.PlayerDeathEvent;
import cn.nukkit.event.player.PlayerQuitEvent;
import cn.nukkit.event.player.PlayerTeleportEvent;
import cn.nukkit.level.Level;
import cn.nukkit.level.Location;
import cn.nukkit.level.Position;
import cn.nukkit.utils.Hash;
import com.sero583.onevsonerm.Main;
import com.sero583.onevsonerm.kit.Kit;

import java.util.*;

/**
 * @author Serhat G. (sero583)
 */
public class Session {
    protected Main plugin;
    protected int id;

    protected Arena arena;

    protected Player one;
    protected Kit kitOne;

    protected Player two;
    protected Kit kitTwo;

    protected Player[] players;
    protected List<Player> playersList;

    public Session(Main plugin, int id, Player one, Kit kitOne, Player two, Kit kitTwo, String arena) {
        this(plugin, id, one, kitOne, two, kitTwo, arena, null);
    }

    public Session(Main plugin, int id, Player one, Kit kitOne, Player two, Kit kitTwo, String arena, String representationName) {
        this.plugin = plugin;

        this.id = id;

        this.arena = new Arena(this, arena);
        this.arena.setOverrideLevelName(representationName); // will be overriden if config is found. nukkit gives sometimes for getName() the folder name, which is cryptic and not the real name..

        this.one = one;
        this.kitOne = kitOne;

        this.two = two;
        this.kitTwo = kitTwo;

        this.initialize();
    }

    protected void initialize() {
        this.players = new Player[] {
            this.one, this.two
        };

        // I admire my laziness
        this.playersList = Arrays.asList(this.players);
    }

    public void onDamage(EntityDamageEvent event, Player attacker, Player victim) {
        if(this.arena.matchStarted()==false) {
            event.setCancelled();
        } else {
            float health = victim.getHealth();

            health -= event.getFinalDamage();
            health = Math.round(health);

            if(health<=0) {
                // attacker won, victim lost

                // prevent death
                event.setCancelled(true);

                this.end(EndCause.KILLED, attacker, victim, health);
            }
        }
    }

    public void onDamageNonPlayer(EntityDamageEvent event, Player victimPlayer) {
        if(this.arena.matchStarted()==false) {
            event.setCancelled();
        } else {
            float health = victimPlayer.getHealth();

            health -= event.getFinalDamage();
            health = Math.round(health);

            if(health<=0) {
                // player died to something, not direct attack by opponent
                event.setCancelled(true);

                // anyway, still opponent is the winner
                this.end(EndCause.DIED, this.getOpposite(victimPlayer), victimPlayer, health);
            }
        }
    }


    public void onQuit(PlayerQuitEvent event) {
        Player looser = event.getPlayer();

        //looser.teleportImmediate(this.plugin.getDefaultSpawn());

        this.end(EndCause.QUIT, this.getOpposite(looser), looser);
    }

    public void onTeleport(PlayerTeleportEvent event) {
        if(this.ended==false) {
            Player player = event.getPlayer();
            Position to = event.getTo();

            if(player!=null&&to!=null) {
                //this.plugin.getLogger().info("Both not null");

                Level toLevel = to.level;

                if(toLevel!=null && toLevel.equals(this.arena.getLevel())==false /* quit the arena */) {
                    // tps out of level = lost
                    this.end(EndCause.QUIT_TELEPORT, this.getOpposite(player), player);
                }
            }
        }

    }

    public void end(EndCause cause) {
        this.end(cause, null, null, null);
    }

    public void end(EndCause cause, Player winner, Player looser) {
        this.end(cause, winner, looser, null);
    }

    protected boolean ended = false;

    public void end(EndCause cause, Player winner, Player looser, Float looserHealth) {
        this.ended = true;

        String broadcastMessageKey = null;
        String winnerMessageKey = null;
        String looserMessageKey = null;

        switch(cause) {
            case DIED:
                // winner and looser are known
                broadcastMessageKey = "win_broadcast_death";
                winnerMessageKey = "you_won_death";
                looserMessageKey = "you_lost_death";
            break;
            case KILLED:
                // winner and looser are known
                broadcastMessageKey = "win_broadcast";
                winnerMessageKey = "you_won";
                looserMessageKey = "you_lost";
            break;
            case QUIT:
                // winner and looser are known
                broadcastMessageKey = "win_broadcast_quit";

                winnerMessageKey = "you_won_quit";
            break;
            case QUIT_TELEPORT:
                // winner and looser are known
                broadcastMessageKey = "win_broadcast_quit_teleport";
                winnerMessageKey = "you_won_quit_teleport";
                looserMessageKey = "you_lost_quit_teleport";
            break;
            case TIMEOUT:
                // special handling, decision for the winner is handled by this case

                float healthOne = this.one.getHealth();
                float healthTwo = this.two.getHealth();

                if(healthOne==healthTwo) {
                    // draw
                    winner = this.one;
                    looser = this.two;

                    broadcastMessageKey = "draw_broadcast";
                    winnerMessageKey = looserMessageKey = "you_had_a_draw";
                } else if(healthOne>healthTwo) {
                    // player one wins
                    winner = this.one;
                    looser = this.two;

                    broadcastMessageKey = "win_broadcast_timeout";
                    winnerMessageKey = "you_won_timeout";
                    looserMessageKey = "you_lost_timeout";
                } else if(healthTwo>healthOne) {
                    // player two wins
                    winner = this.two;
                    looser = this.one;

                    broadcastMessageKey = "win_broadcast_timeout";
                    winnerMessageKey = "you_won_timeout";
                    looserMessageKey = "you_lost_timeout";
                }
            break;
            default:
                this.plugin.getLogger().critical("Unimplemented type with details \"" + cause.getDetails() + "\". Simulating KILLED behaviour.");
            break;
        }

        if(looserHealth==null) {
            looserHealth = looser.getHealth();
        }

        Player finalWinner = winner;
        Player finalLooser = looser;

        String winnerName = winner.getName();
        String looserName = looser.getName();

        float winnerHealth = winner.getHealth();
        Float finalLooserHealth = looserHealth;

        Map<String, String> replacementsBroadcast = new HashMap<String, String>() {
            {
                // general
                put("{level}", Session.this.getArena().getLevelName());

                // winner values
                put("{winner}", finalWinner.getName());
                put("{hearts_winner}", prettyPrintFloatingPoint(winnerHealth / 2));
                put("{hp_winner}", String.valueOf(Math.round(winnerHealth)));
                put("{kit_winner}", Session.this.getKitFrom(finalWinner).getDisplayName());

                // looser values
                put("{looser}", finalLooser.getName());
                put("{hearts_looser}", prettyPrintFloatingPoint(finalLooserHealth/ 2));
                put("{hp_looser}", String.valueOf(Math.round(finalLooserHealth)));
                put("{kit_looser}", Session.this.getKitFrom(finalLooser).getDisplayName());
            }
        };

        HashSet<Player> receivers = new HashSet<>(this.plugin.getServer().getOnlinePlayers().values());
        HashSet<Player> exceptions = new HashSet<>();

        if(this.plugin.getMessages().getBoolean("send_broadcast_to_ingame_players", false)==false) {
            for(Session session : this.plugin.getSessions().values()) {
                if(session.inSession(this.one)==true) { // dont check for p2 cause one of them must be inside so no performance gets wasted
                    continue;
                }

                for(Player p : session.getPlayers()) {
                    exceptions.add(p);
                }
            }
        }

        if(this.plugin.getMessages().getBoolean("send_broadcast_to_involving_players", false)==false) {
            exceptions.add(this.one);
            exceptions.add(this.two);
        }

        // (String key, Collection<Player> receivers, Collection<Player> exceptions, Map<String, String> replacements

        this.plugin.broadcastMessage(broadcastMessageKey, receivers, exceptions, replacementsBroadcast);

        if(winnerMessageKey!=null) {
            this.plugin.sendMessageByKey(winner, winnerMessageKey, new HashMap<String, String>() {{
                put("{level}", Session.this.getArena().getLevelName());
                put("{looser}", looserName);
                put("{opponent}", looserName); // for draw
                put("{kit}", Session.this.getKitFrom(looserName).getDisplayName());
                put("{hearts}", prettyPrintFloatingPoint(finalLooserHealth/ 2));
                put("{hp}", String.valueOf(Math.round(finalLooserHealth)));
            }});
        }

        if(looserMessageKey!=null) {
            this.plugin.sendMessageByKey(looser, looserMessageKey, new HashMap<String, String>() {{
                put("{level}", Session.this.getArena().getLevelName());
                put("{winner}", winnerName);
                put("{opponent}", winnerName); // for draw
                put("{kit}", Session.this.getKitFrom(winnerName).getDisplayName());
                put("{hearts}", prettyPrintFloatingPoint(winnerHealth / 2));
                put("{hp}", String.valueOf(Math.round(winnerHealth)));
            }});
        }

        this.resetPlayers();

        this.arena.delete();

        this.plugin.notifyEndOfSession(this.id);
    }

    private void resetPlayers() {
        for(Player player : this.getPlayers()) {
            player.getInventory().clearAll();
            player.setHealth(player.getMaxHealth());
            player.removeAllEffects();

            if(player.isFoodEnabled()==true) {
                PlayerFood food = player.getFoodData();
                food.reset();
            }

            // thank you nukkit aka bugkit...

            Location defaultSpawn = this.plugin.getDefaultSpawn();

            if(defaultSpawn!=null) {
                player.teleportImmediate(defaultSpawn);
            } else {
                Level defaultLevel = this.plugin.getServer().getDefaultLevel();

                if(defaultLevel!=null) {
                    Position pos = defaultLevel.getSafeSpawn();
                    if(pos!=null) {
                        player.teleport(pos);
                    }
                }
            }
        }
    }

    public Kit getKitFrom(Player player) {
        return this.getKitFrom(player.getName());
    }

    public Kit getKitFrom(String name) {
        if(this.inSession(name)==false) {
            throw new RuntimeException("Player with name \"" + name + "\" is not in this match, so you cant get his Kit.");
        }
        return this.one.getName().equals(name) == true ? this.kitOne : this.kitTwo;
    }

    public Arena getArena() {
        return arena;
    }

    public Player getPlayerOne() {
        return this.one;
    }

    public Kit getKitOne() {
        return kitOne;
    }

    public Player getPlayerTwo() {
        return this.two;
    }

    public Kit getKitTwo() {
        return kitTwo;
    }

    public Player[] getPlayers() {
        return this.players;
    }

    public List<Player> getPlayersList() {
        return this.playersList;
    }

    public Player getOpposite(Player player) {
        return this.getOpposite(player.getName());
    }

    public Player getOpposite(String name) {
        return this.one.getName().equals(name) == true ? this.two : this.one;
    }

    public boolean inSession(Player player) {
        return this.inSession(player.getName());
    }

    public boolean inSession(String name) {
        return this.one.getName().equals(name) || this.two.getName().equals(name);
    }

    public int getId() {
        return this.id;
    }

    public Main getPlugin() {
        return this.plugin;
    }

    public void onDisable() {
        for(Player p : this.getPlayers()) {
            this.plugin.sendMessageByKey(p, "premature_ending");
        }

        this.resetPlayers();
        this.arena.onDisable();
    }

    // should never appear because onDamage should be called first, but for safety this was also implemented
    public void onDeath(PlayerDeathEvent event) {
        Player looser = event.getEntity();

        event.setDrops(null); // to avoid additional, unneeded heap by item entities
        this.end(EndCause.DIED, this.getOpposite(looser), looser);
    }

    @Override
    public boolean equals(Object obj) {
        if(obj instanceof Session) {
            Session comp = (Session) obj;

            return this.getPlayerOne().equals(comp.getPlayerOne()) && this.getPlayerTwo().equals(comp.getPlayerTwo()) && this.id == comp.id;
        }
        return false;
    }

    public enum EndCause {
        DIED("Player died."),
        KILLED("Player killed opponent."),
        TIMEOUT("Time ran out."),
        QUIT("Player quit the server."),
        QUIT_TELEPORT("Player quit the arena.");

        private final String details;

        EndCause(String details) {
            this.details = details;
        }

        public String getDetails() {
            return details;
        }
    }

    /**
     * Prints for e.g. 2.5 as 2.5, but value of 2 not as 2.0, it prints it as 2
     * @param d
     * @return string of formatted value
     */
    public static String prettyPrintFloatingPoint(double d) {
        int i = (int) d;
        return d == i ? String.valueOf(i) : String.valueOf(d);
    }
}
