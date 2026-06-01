package com.riprod.patchly.watch;

import com.hypixel.hytale.assetstore.AssetPack;

import javax.annotation.Nonnull;
import java.nio.file.Path;

public interface PatchChangeListener {
    boolean isSourceFile(@Nonnull Path path);

    boolean isBaseFile(@Nonnull Path path);

    void onPatchEvent(@Nonnull AssetPack pack, @Nonnull Path patchFile);

    void onBaseEvent(@Nonnull AssetPack pack, @Nonnull Path changedJson);
}
