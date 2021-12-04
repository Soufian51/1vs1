package com.sero583.onevsonerm.task;

import cn.nukkit.scheduler.Task;
import com.sero583.onevsonerm.Main;
import org.iq80.leveldb.util.FileUtils;

import java.io.File;

/**
 * @author Serhat G. (sero583)
 */
public class RetryRemoveLevelTask extends Task {
    public static final int MAX_TRIES = 3;
    public static final int DELAY_BETWEEN = 60; // 3 seconds

    protected Main plugin;
    protected File toRemove;
    protected int tryCount;

    public RetryRemoveLevelTask(Main plugin, File toRemove) {
        this(plugin, toRemove, 1);
    }

    public RetryRemoveLevelTask(Main plugin, File toRemove, int tryCount) {
        this.plugin = plugin;
        this.toRemove = toRemove;
        this.tryCount = tryCount;
    }

    @Override
    public void onRun(int i) {
        this.plugin.getLogger().info("Trying to remove level at File " + this.toRemove + " again...");

        if(FileUtils.deleteRecursively(this.toRemove)==false) {
            if(this.tryCount<MAX_TRIES) {
                this.plugin.getLogger().critical(this.toRemove + " couldn't be removed. Trying again... (try count=" + this.tryCount + ", max try count=" + MAX_TRIES + ").");

                this.plugin.getServer().getScheduler().scheduleDelayedTask(new RetryRemoveLevelTask(this.plugin, this.toRemove, this.tryCount+1), DELAY_BETWEEN);
            } else {
                this.plugin.getLogger().critical(this.toRemove + " couldn't be removed. Calling again deleteOnExit() on file.");
                this.toRemove.deleteOnExit();
            }
        } else this.plugin.getLogger().info(this.toRemove + " has been removed successfully.");
    }
}
