package com.riprod.patchly.store;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.logging.Level;

public final class OverrideStore {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final Path dir;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();

    public OverrideStore(@Nonnull Path dir) {
        this.dir = dir;
    }

    public void wipe() {
        try {
            if (Files.isDirectory(dir)) {
                deleteTree(false);
            }
            Files.createDirectories(dir);
        } catch (IOException e) {
            LOGGER.at(Level.WARNING).withCause(e).log("[patcher] startup wipe failed.");
        }
    }

    public void writeManifest(@Nonnull String group, @Nonnull String name) {
        JsonObject manifest = new JsonObject();
        manifest.addProperty("Group", group);
        manifest.addProperty("Name", name);
        manifest.addProperty("Version", "1.0.0");
        manifest.addProperty("IncludesAssetPack", true);
        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("manifest.json"), gson.toJson(manifest));
        } catch (IOException e) {
            LOGGER.at(Level.WARNING).withCause(e).log("[patcher] failed to write override manifest");
        }
    }

    public void clear(boolean includingRoot) {
        if (!Files.isDirectory(dir)) return;
        try {
            deleteTree(includingRoot);
        } catch (IOException e) {
            LOGGER.at(Level.WARNING).withCause(e).log("[patcher] failed to clear override directory");
        }
    }

    @Nullable
    public Path writeIfChanged(@Nonnull String relativeTarget, @Nonnull JsonObject value) {
        Path outPath = dir.resolve(relativeTarget);
        String newContent = gson.toJson(value);
        try {
            if (Files.isRegularFile(outPath)) {
                if (Files.readString(outPath).equals(newContent)) return null;
            }
            Files.createDirectories(outPath.getParent());
            Files.writeString(outPath, newContent);
            return outPath;
        } catch (IOException e) {
            LOGGER.at(Level.WARNING).withCause(e).log("[patcher] write failed: %s", outPath);
            return null;
        }
    }

    public void deleteOverride(@Nonnull String relativeTarget) {
        try {
            Files.deleteIfExists(dir.resolve(relativeTarget));
        } catch (IOException e) {
            LOGGER.at(Level.WARNING).withCause(e).log("[patcher] failed to delete override %s", relativeTarget);
        }
    }

    private void deleteTree(boolean includingRoot) throws IOException {
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path visited, IOException exc) throws IOException {
                if (includingRoot || !visited.equals(dir)) Files.delete(visited);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
