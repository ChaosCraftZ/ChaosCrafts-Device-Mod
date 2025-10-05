package net.chaoscraft.chaoscrafts_device_mod.Core.Desktop.Model;

import net.minecraft.resources.ResourceLocation;

/**
 * Represents a search result with display name, icon, and action.
 */
public class SearchResult {
    public final String displayName;
    public final ResourceLocation iconRes;
    public final Runnable action;

    public SearchResult(String displayName, ResourceLocation iconRes, Runnable action) {
        this.displayName = displayName;
        this.iconRes = iconRes;
        this.action = action;
    }
}