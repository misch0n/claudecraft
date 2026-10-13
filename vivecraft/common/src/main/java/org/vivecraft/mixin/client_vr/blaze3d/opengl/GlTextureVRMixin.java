package org.vivecraft.mixin.client_vr.blaze3d.opengl;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.RenderTarget;
import org.lwjgl.opengl.GL11C;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.VRState;
import org.vivecraft.client_vr.gameplay.screenhandlers.GuiHandler;
import org.vivecraft.client_vr.gameplay.screenhandlers.KeyboardHandler;
import org.vivecraft.client_vr.gameplay.screenhandlers.RadialHandler;
import org.vivecraft.client_vr.provider.VRRenderer;

import javax.annotation.Nullable;

/**
 * claudecraft: ltwLinearFilterFix.
 * The GL to GLES layer QuestCraft ships (LTW) turns {@code glTexParameteri(MIN/MAG_FILTER, GL_LINEAR)} into
 * GL_NEAREST, but passes {@code glTexParameterf} through unchanged. GlTexture applies its filters through
 * {@code GlStateManager._texParameter(int, int, int)} when it is bound as a sampler, so for Vivecraft's own linear
 * targets (GUI, keyboard, radial menu, the 3D render that the eye post-process samples, camera and mirror sources) a
 * GL_LINEAR min/mag filter is set with glTexParameterf instead. Same result on desktop GL.
 */
@Mixin(GlTexture.class)
public abstract class GlTextureVRMixin {

    @WrapOperation(method = "flushModeChanges", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/opengl/GlStateManager;_texParameter(III)V"))
    private void vivecraft$linearFilterAsFloat(int target, int pname, int param, Operation<Void> original) {
        if (param == GL11C.GL_LINEAR &&
            (pname == GL11C.GL_TEXTURE_MIN_FILTER || pname == GL11C.GL_TEXTURE_MAG_FILTER) &&
            vivecraft$isVivecraftLinearTarget())
        {
            // the texture is bound to GL_TEXTURE_2D on the active unit while its modes are flushed
            GL11C.glTexParameterf(target, pname, (float) param);
        } else {
            original.call(target, pname, param);
        }
    }

    /**
     * @return if this texture is the color texture of one of Vivecraft's linear filtered RenderTargets, and the fix is on
     */
    @Unique
    private boolean vivecraft$isVivecraftLinearTarget() {
        if (!VRState.VR_INITIALIZED) {
            return false;
        }
        ClientDataHolderVR dataHolder = ClientDataHolderVR.getInstance();
        if (dataHolder.vrSettings == null || !dataHolder.vrSettings.ltwLinearFilterFix) {
            return false;
        }
        Object self = this;
        if (vivecraft$isColorOf(GuiHandler.GUI_FRAMEBUFFER, self) ||
            vivecraft$isColorOf(KeyboardHandler.FRAMEBUFFER, self) ||
            vivecraft$isColorOf(RadialHandler.FRAMEBUFFER, self))
        {
            return true;
        }
        VRRenderer vrRenderer = dataHolder.vrRenderer;
        return vrRenderer != null && (
            vivecraft$isColorOf(vrRenderer.framebufferVrRender, self) ||
                vivecraft$isColorOf(vrRenderer.cameraRenderFramebuffer, self) ||
                vivecraft$isColorOf(vrRenderer.framebufferUndistorted, self) ||
                vivecraft$isColorOf(vrRenderer.framebufferMR, self)
        );
    }

    @Unique
    private static boolean vivecraft$isColorOf(@Nullable RenderTarget renderTarget, Object texture) {
        return renderTarget != null && renderTarget.getColorTexture() == texture;
    }
}
