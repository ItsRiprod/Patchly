package com.riprod.patchly;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

public final class PatchlyPlugin extends JavaPlugin {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private final PatchManager patchManager;

    public PatchlyPlugin(JavaPluginInit init) {
        super(init);
        LOGGER.atInfo().log("Patchly %s initializing...", PatchManager.PATCHER_VERSION);
        patchManager = new PatchManager(this);
    }

    @Override
    protected void setup() {
        patchManager.install();
    }
}
