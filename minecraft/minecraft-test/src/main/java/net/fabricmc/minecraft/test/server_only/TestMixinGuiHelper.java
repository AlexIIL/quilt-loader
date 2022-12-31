package net.fabricmc.minecraft.test.server_only;

import org.quiltmc.loader.impl.ipc.QuiltRemoteWindowHelper;

public class TestMixinGuiHelper {

    public static void help() {
        QuiltRemoteWindowHelper.close();
    }

}
