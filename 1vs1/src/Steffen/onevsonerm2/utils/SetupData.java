package com.sero583.onevsonerm.utils;

import cn.nukkit.Player;
import cn.nukkit.level.Level;
import cn.nukkit.level.Location;
import cn.nukkit.level.Position;
import cn.nukkit.utils.Config;
import cn.nukkit.utils.ConfigSection;

/**
 * @author Serhat G. (sero583)
 */
public class SetupData {
    protected Level level;
    protected Location one;
    protected Location two;


    public SetupData(Level level) {
        this.level = level;
    }

    public Level getLevel() {
        return level;
    }

    public Location getLocationOne() {
        return one;
    }

    public void setLocationOne(Location one) {
        this.one = one;
    }

    public Location getLocationTwo() {
        return two;
    }

    public void setLocationTwo(Location two) {
        this.two = two;
    }

    public ConfigSection generateSaveData() {
        ConfigSection section = new ConfigSection();

        section.set("spawnOne", pos2string(this.getLocationOne()));
        section.set("spawnTwo", pos2string(this.getLocationTwo()));

        return section;
    }

    public static String pos2string(Position pos) {
        String result = pos.getX() + ":" + pos.getY() + ":" + pos.getZ();

        if(pos instanceof Location) {
            Location loc = (Location) pos;

            result += ":" + loc.getPitch() + ":" + loc.getYaw();
        }
        return result;
    }

    public static Location string2pos(String data) {
        String[] split = data.split(":");
        int len = split.length;

        if(len<=0) {
            throw new RuntimeException("Gave invalid data-string (\"" + data + "\"), cannot convert.");
        }

        Location loc = new Location();

        loc.x = len >= 1 ? Double.parseDouble(split[0]) : 0;
        loc.y = len >= 2 ? Double.parseDouble(split[1]) : 0;
        loc.z = len >= 3 ? Double.parseDouble(split[2]) : 0;
        loc.pitch = len >= 4 ? Double.parseDouble(split[3]) : 0;
        loc.yaw = len >= 5 ? Double.parseDouble(split[4]) : 0;

        return loc;
    }
}
