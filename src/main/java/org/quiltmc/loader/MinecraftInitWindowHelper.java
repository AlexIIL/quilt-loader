package org.quiltmc.loader;

import org.quiltmc.loader.impl.ipc.QuiltRemoteWindowHelper;

public class MinecraftInitWindowHelper {
    public static final int countMain = 497;
    public static final int count = 836;
    private static int indexMain;
    private static int index;

    public static void insnMain() {
    	if (index == 0) {
    		indexMain++;
        	QuiltRemoteWindowHelper.sendProgressUpdate("Starting Minecraft", 50 + indexMain * 25 / countMain, 0);
//			System.out.println(indexMain + " / " + countMain);
    	}
    }

    public static void insn() {
        index++;
        QuiltRemoteWindowHelper.sendProgressUpdate("Loading Minecraft", 75 + index * 25 / count, 0);
//		System.out.println(index + " / " + count);
    }
}
