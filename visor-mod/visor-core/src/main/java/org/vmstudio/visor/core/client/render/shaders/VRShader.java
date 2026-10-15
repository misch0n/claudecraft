package org.vmstudio.visor.core.client.render.shaders;

import org.jetbrains.annotations.NotNull;
import org.vmstudio.visor.api.compatibility.mcversion.render.McShaderProgram;

public interface VRShader {
    @NotNull
    McShaderProgram getHandle();

    void init() throws Exception;

}
