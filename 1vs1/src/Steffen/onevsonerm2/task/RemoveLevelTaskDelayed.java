package com.sero583.onevsonerm.task;

import cn.nukkit.scheduler.Task;
import com.sero583.onevsonerm.session.Arena;

/**
 * @author Serhat G. (sero583)
 */
public class RemoveLevelTaskDelayed extends Task {
    protected Arena arena;

    public RemoveLevelTaskDelayed(Arena arena) {
        this.arena = arena;
    }

    @Override
    public void onRun(int i) {
        this.arena.removeLevel();
    }
}
