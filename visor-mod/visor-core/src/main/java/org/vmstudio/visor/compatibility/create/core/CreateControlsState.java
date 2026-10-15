package org.vmstudio.visor.compatibility.create.core;

import lombok.Getter;
import lombok.Setter;

public final class CreateControlsState {
    @Setter
    @Getter
    private static boolean trainControlsActive;

    @Setter
    @Getter
    private static boolean linkedControllerActive;

    public static boolean isAnyControlsActive() {
        return trainControlsActive || linkedControllerActive;
    }

    private CreateControlsState() {}
}
