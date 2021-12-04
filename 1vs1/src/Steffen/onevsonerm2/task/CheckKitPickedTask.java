package com.sero583.onevsonerm.task;

import cn.nukkit.Player;
import cn.nukkit.scheduler.Task;
import com.sero583.onevsonerm.Main;

/**
 * @author Serhat G. (sero583)
 */
public class CheckKitPickedTask extends Task {
    protected Main plugin;

    protected Player one;
    protected Player two;

    public CheckKitPickedTask(Main plugin, Player one, Player two) {
        this.plugin = plugin;

        this.one = one;
        this.two = two;
    }

    @Override
    public void onRun(int i) {
        this.plugin.checkKitPick(this.one, this.two);
    }

    public void cancel() {
        this.plugin.getServer().getScheduler().cancelTask(this.getTaskId());
    }
}
