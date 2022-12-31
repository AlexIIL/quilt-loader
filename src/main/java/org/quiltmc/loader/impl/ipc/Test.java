package org.quiltmc.loader.impl.ipc;

import java.io.File;
import java.io.IOException;

import org.quiltmc.loader.api.plugin.LoaderValueFactory;

public class Test {
	public static void main(String[] args) throws IOException, InterruptedException {
		QuiltIPC ipc = QuiltIPC.connect(new File("quilt-ipc-1"), value -> {
			System.out.println("CL: " + value + " '" + value.asString() + "'");
		});

		ipc.send(LoaderValueFactory.getFactory().string("Hello World"));
		ipc.send(LoaderValueFactory.getFactory().string("Second Message"));

		Thread.sleep(10000);
	}
}
