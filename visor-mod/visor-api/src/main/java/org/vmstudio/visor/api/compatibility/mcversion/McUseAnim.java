package org.vmstudio.visor.api.compatibility.mcversion;

/**
 * Item use animation, stable across the vanilla UseAnim/ItemUseAnimation rename
 */
public enum McUseAnim {
    NONE,
    EAT,
    DRINK,
    BLOCK,
    BOW,
    SPEAR,
    CROSSBOW,
    SPYGLASS,
    TOOT_HORN,
    BRUSH,
    // anything else, like the CUSTOM constant Forge and NeoForge add to UseAnim
    CUSTOM
}
