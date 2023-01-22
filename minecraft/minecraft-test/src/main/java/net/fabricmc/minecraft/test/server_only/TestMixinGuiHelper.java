package net.fabricmc.minecraft.test.server_only;

import org.quiltmc.loader.impl.ipc.QuiltRemoteWindowHelper;
import org.quiltmc.loader.impl.QuiltLoaderImpl;

public class TestMixinGuiHelper {

    public static void help() {
        QuiltRemoteWindowHelper.close();
    	QuiltLoaderImpl.INSTANCE.getAccessWidener();
    }
}
