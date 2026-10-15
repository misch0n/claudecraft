package org.vmstudio.visor.core.client.render.camera;

import lombok.Getter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.vmstudio.visor.api.client.player.pose.PlayerPoseType;
import org.vmstudio.visor.api.client.render.VRRenderPass;
import org.vmstudio.visor.api.common.player.VRPose;
import org.vmstudio.visor.core.client.ClientContext;
import org.vmstudio.visor.core.client.render.VRRenderState;

import static org.vmstudio.visor.core.client.VisorClientImpl.MC;

// camera entity moved to a VR pose
public class VRCameraEntitySwap {

    @Getter
    private static VRCameraEntityCache cameraEntityCache = new VRCameraEntityCache();

    private static boolean cameraEntityCached;
    private static int cameraEntityCacheDepth;


    public static void cacheCameraEntity(Entity cameraEntity) {
        if (MC.getCameraEntity() != null) {
            cameraEntityCacheDepth++;
            if (!cameraEntityCached) {
                LivingEntity livingEntity = cameraEntity instanceof LivingEntity ent ? ent : null;
                cameraEntityCache = new VRCameraEntityCache(
                        cameraEntity.getX(), cameraEntity.getY(),
                        cameraEntity.getZ(),

                        cameraEntity.xOld, cameraEntity.yOld,
                        cameraEntity.zOld,

                        cameraEntity.xo, cameraEntity.yo,
                        cameraEntity.zo,

                        livingEntity != null ? livingEntity.yHeadRot : cameraEntity.getYRot(),
                        cameraEntity.getXRot(),

                        livingEntity != null ? livingEntity.yHeadRotO : cameraEntity.yRotO,
                        cameraEntity.xRotO,

                        cameraEntity.getEyeHeight()
                );
                cameraEntityCached = true;
            }
        }
    }

    public static void setupCameraEntity(VRPose vrPose) {
        if (!cameraEntityCached) {
            return;
        }
        var position = vrPose.getPosition();
        float x = position.x();
        float y = position.y();
        float z = position.z();

        LivingEntity cameraEntity = (LivingEntity) MC.getCameraEntity();
        cameraEntity.setPosRaw(x, y, z);
        cameraEntity.xo = cameraEntity.xOld = x;
        cameraEntity.yo = cameraEntity.yOld = y;
        cameraEntity.zo = cameraEntity.zOld = z;

        cameraEntity.setXRot(-vrPose.getPitchDegrees());
        cameraEntity.setYRot(vrPose.getYawDegrees());
        cameraEntity.xRotO = cameraEntity.getXRot();
        cameraEntity.yHeadRot = cameraEntity.getYRot();
        cameraEntity.yHeadRotO = cameraEntity.getYRot();

        // collapse the eye offset so the entity position is the pose position
        cameraEntity.eyeHeight = 0.0001F;
    }

    public static void setupCameraEntityAsVRCamera(){
        VRRenderPass renderPass = VRRenderState.getRenderPass();
        if (renderPass == null) return;
        setupCameraEntity(
                ClientContext.localPlayer
                        .getPoseData(PlayerPoseType.RENDER)
                        .getCameraPose(renderPass)
        );
    }

    public static void restoreCameraEntity(Entity cameraEntity) {
        if (cameraEntityCacheDepth > 0) {
            cameraEntityCacheDepth--;
        }
        if (cameraEntity != null
                && cameraEntityCached
                && cameraEntityCacheDepth == 0) {
            cameraEntityCache.apply(cameraEntity);
            cameraEntityCached = false;
        }
    }

    public static void applyCachedCameraEntityPosition(Entity cameraEntity) {
        if (cameraEntity != null && cameraEntityCached) {
            cameraEntityCache.apply(cameraEntity);
        }
    }

}
