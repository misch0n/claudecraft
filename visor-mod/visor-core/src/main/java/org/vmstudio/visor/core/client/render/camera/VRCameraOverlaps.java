package org.vmstudio.visor.core.client.render.camera;

import com.mojang.blaze3d.vertex.PoseStack;
import lombok.Getter;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.vmstudio.visor.api.ModLoader;
import org.vmstudio.visor.api.client.player.pose.PlayerPoseType;
import org.vmstudio.visor.api.client.render.VRRenderPass;
import org.vmstudio.visor.core.client.ClientContext;
import org.vmstudio.visor.core.client.render.VRRenderState;
import org.vmstudio.visor.core.client.render.helpers.RenderHelper;
import org.vmstudio.visor.core.client.render.helpers.RenderPoseHelper;
import org.vmstudio.visor.extensions.client.render.GameRendererExtension;

import static org.vmstudio.visor.core.client.VisorClientImpl.MC;

// pass camera inside a block / on fire
public class VRCameraOverlaps {

    @Getter
    private static boolean onFire;
    @Getter
    private static boolean inBlock = false;
    /**
     *  Returns a 0..1 proximity factor describing how close the current camera
     *  eye is to a solid block surface.
     */
    @Getter
    private static float blockProximity = 0.0f;


    public static void updateCameraOverlaps() {
        inBlock = false;
        blockProximity = 0.0f;

        onFire = false;

        if(MC.player.isSpectator()
                || !MC.player.isAlive()
                || VRRenderState.getSceneType().isMainMenu()){
            return;
        }
        // fix for immersive portals issue
        if (MC.level != MC.player.level()) {
            return;
        }
        VRRenderPass renderPass = VRRenderState.getRenderPass();
        if (renderPass == null) {
            return;
        }
        var cameraPos = RenderPoseHelper.getCameraPosition(
                renderPass,
                ClientContext.localPlayer.getPoseData(PlayerPoseType.RENDER)
        );

        float inBlockEffectStart = 0.3f;
        float distance = RenderHelper.distanceToNearestSolidBlockSurface(
                new Vec3((Vector3f) cameraPos),
                inBlockEffectStart
        );

        blockProximity = Math.max(
                0.0f,
                1.0f - distance / inBlockEffectStart
        );
        inBlock = distance < ((GameRendererExtension) MC.gameRenderer).visor$getNearClipPlane() * 2.0f;


        onFire = VRRenderState.getRenderPass() != VRRenderPass.THIRD_PERSON
                && MC.player.isOnFire()
                && !ModLoader.get().renderFireOverlay(
                MC.player, new PoseStack()
        );
    }

}
