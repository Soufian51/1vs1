package com.sero583.onevsonerm.utils;

import cn.nukkit.Player;
import com.sero583.onevsonerm.task.CheckKitPickedTask;

/**
 * @author Serhat G. (sero583)
 */
public class DuelPreparation {
    protected Player one;
    protected Player two;
    protected CheckKitPickedTask task;

    public DuelPreparation(Player one, Player two) {
        this.one = one;
        this.two = two;
    }

    public Player getOne() {
        return one;
    }

    public Player getTwo() {
        return two;
    }

    public CheckKitPickedTask getTask() {
        return task;
    }

    public boolean belongsTo(Player player) {
        return this.belongsTo(player.getName());
    }

    public boolean belongsTo(String name) {
        return this.one.getName().equals(name) || this.two.getName().equals(name);
    }

    public Player getOpposite(Player player) {
        return this.getOpposite(player.getName());
    }

    public Player getOpposite(String name) {
        return this.one.getName().equals(name) == true ? this.two : this.one;
    }

    public void assignCheckTask(CheckKitPickedTask checkTask) {
        if(this.task!=null) {
            throw new RuntimeException("Tried to assign a new task to " + this + " while it already has one assigned.");
        }
        this.task = checkTask;
    }

    @Override
    public String toString() {
        return "DuelPreparation(one=" + this.one.getName() + ", two=" + this.two.getName() + ", data=" + super.toString() + ")";
    }
}
