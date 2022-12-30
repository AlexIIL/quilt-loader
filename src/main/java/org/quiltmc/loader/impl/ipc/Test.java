package org.quiltmc.loader.impl.ipc;

import java.io.File;
import java.io.IOException;
import java.util.Random;

public class Test {
	public static void main(String[] args) throws IOException, InterruptedException {
		QuiltIPC ipc = QuiltIPC.connect(new File("quilt-ipc-1"));

		Random rand = new Random(42);
		for (int i = 0; i < 100; i++) {
			byte[] array = new byte[1 << 12];
			for (int j = 0; j < array.length; j++) {
				array[j] = (byte) rand.nextInt(256);
			}
			ipc.writerQueue.add(array);
			Thread.sleep(10);
		}
	}
}
