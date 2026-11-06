package org.vivecraft.client_vr;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import org.vivecraft.client_vr.render.RenderPass;
import org.vivecraft.client_xr.render_pass.RenderPassType;
import org.vivecraft.client_xr.render_pass.WorldRenderPass;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A TextureTarget that holds multiple TextureTargets for each RenderPass, and delegates calls to the current active one
 * this assumes that the TextureTarget gets at least cleared/bound or something, before its width/height gets accessed
 */
public class MultiPassTextureTarget extends TextureTarget {

    // when either of those is set, methods will use their corresponding TextureTarget, instead of the current pass one
    private boolean isVanilla = false;
    private RenderPass passOverride = null;

    private RenderTarget last = null;

    // gets set after super call, so when it is null, it can be assumed to not be ready yet
    @Nullable
    private final EnumMap<RenderPass, TextureTarget> vrTargets;

    // claudecraft: not final, with shareOutlineTargets it is only created on first use
    private TextureTarget vanilla;

    // claudecraft: shareOutlineTargets. Read once, VRRenderer recreates this target on every framebuffer reinit.
    // When set, LEFT and RIGHT share one target (both render to WorldRenderPass.STEREO_XR), and targets are only
    // created once something glows in that pass, until then a 1x1 placeholder takes vanilla's per frame clear.
    private final boolean shared;
    private final String name;
    private final boolean useDepthBuffer;
    private int vanillaWidth;
    private int vanillaHeight;
    @Nullable
    private TextureTarget placeholder;
    // claudecraft: result of the last LevelRenderer#collectVisibleEntities, true when a visible entity glows
    private static boolean GLOWING = false;

    public MultiPassTextureTarget(String name, int width, int height, boolean useDepth) {
        super(name, width, height, useDepth);
        super.destroyBuffers();

        this.vrTargets = new EnumMap<>(RenderPass.class);

        // claudecraft: lazy and shared targets
        this.shared = ClientDataHolderVR.getInstance().vrSettings.shareOutlineTargets;
        this.name = name;
        this.useDepthBuffer = useDepth;
        this.vanillaWidth = width;
        this.vanillaHeight = height;
        if (this.shared) {
            // nothing allocated yet, getCurrent creates targets when they are needed
            return;
        }

        this.isVanilla = true;
        this.vanilla = new TextureTarget(name, width, height, useDepth);
        this.isVanilla = false;

        for (RenderPass pass : RenderPass.values()) {
            // create one TextureTarget for each active render pass
            WorldRenderPass worldPass = WorldRenderPass.getByRenderPass(pass);
            if (worldPass == null) continue;
            RenderTarget original = worldPass.target;
            this.vrTargets.put(pass, new TextureTarget(name + " " + pass, original.width, original.height, useDepth));
        }
        // set vanilla as default
        setLast(this.vanilla);
    }

    @Override
    public void resize(int width, int height) {
        if (this.vrTargets == null) {
            super.resize(width, height);
            return;
        }

        // resize all targets to their main counterpart
        this.vanillaWidth = width;
        this.vanillaHeight = height;
        // claudecraft: vanilla is null until first use with shareOutlineTargets
        if (this.vanilla != null) {
            this.isVanilla = true;
            this.vanilla.resize(width, height);
            this.isVanilla = false;
        }
        for (Map.Entry<RenderPass, TextureTarget> entry : this.vrTargets.entrySet()) {
            WorldRenderPass pass = WorldRenderPass.getByRenderPass(entry.getKey());
            if (pass != null) {
                this.passOverride = entry.getKey();
                entry.getValue().resize(pass.target.width, pass.target.height);
                this.passOverride = null;
            }
        }
    }

    @Override
    public void destroyBuffers() {
        if (this.vrTargets == null) {
            super.destroyBuffers();
            return;
        }
        // this one should be called on all TextureTargets
        callOnAllTarget(TextureTarget::destroyBuffers);
    }

    @Override
    public void copyDepthFrom(RenderTarget otherTarget) {
        if (this.vrTargets == null) {
            super.copyDepthFrom(otherTarget);
            return;
        }
        callOnTarget(r -> r.copyDepthFrom(otherTarget));
    }

    @Override
    public void createBuffers(int width, int height) {
        if (this.vrTargets == null) {
            super.createBuffers(width, height);
            return;
        }
        callOnTarget(r -> r.createBuffers(width, height));
    }

    @Override
    public void setFilterMode(FilterMode filterMode) {
        if (this.vrTargets == null) {
            super.setFilterMode(filterMode);
            return;
        }
        callOnTarget(r -> r.setFilterMode(filterMode));
    }

    @Override
    public void blitToScreen() {
        if (this.vrTargets == null) {
            super.blitToScreen();
            return;
        }
        callOnTarget(RenderTarget::blitToScreen);
    }

    @Override
    public void blitAndBlendToTexture(GpuTexture gpuTexture) {
        if (this.vrTargets == null) {
            super.blitAndBlendToTexture(gpuTexture);
            return;
        }
        // claudecraft: the placeholder only ever holds vanilla's clear, blending it would change nothing
        if (this.shared) {
            TextureTarget current = getCurrent(true);
            if (current != null && current == this.placeholder) {
                return;
            }
        }
        callOnTarget(r -> r.blitAndBlendToTexture(gpuTexture));
    }

    @Override
    public GpuTexture getColorTexture() {
        if (this.vrTargets == null) {
            return super.getColorTexture();
        }
        return callOnTargetTexture(RenderTarget::getColorTexture);
    }

    @Override
    public GpuTexture getDepthTexture() {
        if (this.vrTargets == null) {
            return super.getDepthTexture();
        }
        return callOnTargetTexture(RenderTarget::getDepthTexture);
    }

    private void callOnAllTarget(Consumer<TextureTarget> consumer) {
        // claudecraft: vanilla is null until first use with shareOutlineTargets
        if (this.vanilla != null) {
            this.isVanilla = true;
            consumer.accept(this.vanilla);
            this.isVanilla = false;
        }
        for (Map.Entry<RenderPass, TextureTarget> entry : this.vrTargets.entrySet()) {
            this.passOverride = entry.getKey();
            consumer.accept(entry.getValue());
            this.passOverride = null;
        }
        if (this.placeholder != null) {
            consumer.accept(this.placeholder);
        }
    }

    private void callOnTarget(Consumer<TextureTarget> consumer) {
        TextureTarget current = getCurrent();
        if (current != this.last) {
            setLast(current);
        }
        consumer.accept(current);
    }

    private GpuTexture callOnTargetTexture(Function<TextureTarget, GpuTexture> function) {
        // claudecraft: texture getters are what vanilla's per frame clear uses, they may get the placeholder
        TextureTarget current = getCurrent(true);
        if (current != this.last) {
            setLast(current);
        }
        return function.apply(current);
    }

    /**
     * @return the TextureTarget that should be rendered to now
     */
    private TextureTarget getCurrent() {
        return getCurrent(false);
    }

    /**
     * @param allowPlaceholder if a target that doesn't exist yet may be substituted by the 1x1 placeholder while
     *                         nothing glows, only used with shareOutlineTargets
     * @return the TextureTarget that should be rendered to now
     */
    private TextureTarget getCurrent(boolean allowPlaceholder) {
        if (this.isVanilla || RenderPassType.isVanilla()) {
            if (this.vanilla == null) {
                // claudecraft: only with shareOutlineTargets, create on first use
                if (allowPlaceholder && !GLOWING) {
                    return getPlaceholder();
                }
                this.vanilla = new TextureTarget(this.name, this.vanillaWidth, this.vanillaHeight,
                    this.useDepthBuffer);
            }
            return this.vanilla;
        } else {
            RenderPass pass =
                this.passOverride != null ? this.passOverride : ClientDataHolderVR.getInstance().currentPass;
            if (!this.shared) {
                return this.vrTargets.get(pass);
            }
            // claudecraft: LEFT and RIGHT both render to WorldRenderPass.STEREO_XR, so they can share one target,
            // each eye clears, draws, post processes and blends it before the next eye starts
            RenderPass key = pass == RenderPass.RIGHT ? RenderPass.LEFT : pass;
            TextureTarget target = key == null ? null : this.vrTargets.get(key);
            if (target == null && key != null) {
                WorldRenderPass worldPass = WorldRenderPass.getByRenderPass(key);
                if (worldPass == null) {
                    // same as without sharing, there is no target for passes without a world pass
                    return null;
                }
                if (allowPlaceholder && !GLOWING) {
                    return getPlaceholder();
                }
                target = new TextureTarget(this.name + " " + key, worldPass.target.width, worldPass.target.height,
                    this.useDepthBuffer);
                this.vrTargets.put(key, target);
            }
            return target;
        }
    }

    /**
     * claudecraft: 1x1 target that takes vanilla's per frame clear of the outline target while nothing glows
     */
    private TextureTarget getPlaceholder() {
        if (this.placeholder == null) {
            this.placeholder = new TextureTarget(this.name + " placeholder", 1, 1, this.useDepthBuffer);
        }
        return this.placeholder;
    }

    /**
     * claudecraft: called with the result of LevelRenderer#collectVisibleEntities, which runs before the outline
     * target is cleared, drawn to and blended for the current pass
     *
     * @param glowing if any visible entity glows in the current pass
     */
    public static void setGlowing(boolean glowing) {
        GLOWING = glowing;
    }

    /**
     * sets the public fields to the ones of the provided TextureTarget
     *
     * @param current TextureTarget to set
     */
    private void setLast(TextureTarget current) {
        this.last = current;
        this.width = current.width;
        this.height = current.height;
        this.viewWidth = current.viewWidth;
        this.viewHeight = current.viewHeight;
        this.filterMode = current.filterMode;
    }
}
