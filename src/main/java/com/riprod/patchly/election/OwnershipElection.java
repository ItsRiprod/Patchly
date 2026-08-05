package com.riprod.patchly.election;

import com.hypixel.hytale.common.semver.Semver;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.logging.Level;

/**
 * Handles all patchly single-owner rules to ensure only one version of patchly is active at a time.
 */
public final class OwnershipElection {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final String SYS_PROP_OWNER = "patcher.owner";
    private static final String SYS_PROP_VERSION = "patcher.version";
    private static final String SYS_PROP_PROTOCOL = "patcher.protocol";
    private static final String SYS_PROP_ACTIVATED = "patcher.activated";
    private static final String PROTOCOL_VERSION = "2";

    private final String ownerId;
    private final String version;
    private final Semver selfVersion;

    private volatile boolean winner = false;
    private boolean legacyDeferred = false;

    public OwnershipElection(@Nonnull String ownerId, @Nonnull String version) {
        this.ownerId = ownerId;
        this.version = version;
        this.selfVersion = Semver.fromString(version);
        vote();
    }

    private void vote() {
        String existingOwner = System.getProperty(SYS_PROP_OWNER);
        if (existingOwner != null && !existingOwner.isEmpty()
                && System.getProperty(SYS_PROP_PROTOCOL) == null) {
            legacyDeferred = true;
            return;
        }
        Semver existing = parseVersionProp(System.getProperty(SYS_PROP_VERSION));
        if (existing == null || selfVersion.compareTo(existing) > 0) {
            System.setProperty(SYS_PROP_OWNER, ownerId);
            System.setProperty(SYS_PROP_VERSION, version);
            System.setProperty(SYS_PROP_PROTOCOL, PROTOCOL_VERSION);
        }
    }

    public boolean claim() {
        if (legacyDeferred) {
            LOGGER.at(Level.WARNING).log(
                    "[patcher] legacy Patchly already active (%s); deferring %s v%s (pre-election behavior)",
                    System.getProperty(SYS_PROP_OWNER), ownerId, version);
            return false;
        }
        if (System.getProperty(SYS_PROP_ACTIVATED) != null) {
            LOGGER.at(Level.INFO).log(
                    "[patcher] boot election already concluded; deferring %s v%s", ownerId, version);
            return false;
        }
        if (!isWinner()) {
            LOGGER.at(Level.INFO).log(
                    "[patcher] deferring to %s v%s (this is %s v%s)",
                    System.getProperty(SYS_PROP_OWNER), System.getProperty(SYS_PROP_VERSION), ownerId, version);
            return false;
        }
        System.setProperty(SYS_PROP_ACTIVATED, ownerId);
        winner = true;
        LOGGER.at(Level.INFO).log("[patcher] won boot election: %s v%s", ownerId, version);
        return true;
    }

    public boolean isActive() {
        return winner;
    }

    @Nonnull
    public String getOwnerId() {
        return ownerId;
    }

    private boolean isWinner() {
        return ownerId.equals(System.getProperty(SYS_PROP_OWNER))
                && version.equals(System.getProperty(SYS_PROP_VERSION));
    }

    public void release() {
        System.clearProperty(SYS_PROP_OWNER);
        System.clearProperty(SYS_PROP_VERSION);
        System.clearProperty(SYS_PROP_PROTOCOL);
        System.clearProperty(SYS_PROP_ACTIVATED);
    }

    @Nullable
    private static Semver parseVersionProp(@Nullable String v) {
        if (v == null || v.isEmpty()) return null;
        try {
            return Semver.fromString(v);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
