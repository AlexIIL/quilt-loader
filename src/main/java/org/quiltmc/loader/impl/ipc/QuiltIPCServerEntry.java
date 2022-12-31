package org.quiltmc.loader.impl.ipc;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;

import org.quiltmc.loader.api.LoaderValue;
import org.quiltmc.loader.api.LoaderValue.LObject;
import org.quiltmc.loader.api.LoaderValue.LType;
import org.quiltmc.loader.impl.gui.QuiltProgressWindow;

public class QuiltIPCServerEntry {
	public static void main(String[] args) {
		if (args.length < 2 || !"--file".equals(args[0])) {
			System.err.println("QUILT_IPC_SERVER: missing arguments / first argument wasn't a file!");
			System.exit(1);
			return;
		}

		try {
			run(args);
		} catch (IOException io) {
			System.err.println("QUILT_IPC_SERVER: Failed to run!");
			io.printStackTrace();
			System.exit(2);
			return;
		}
	}

	private static void run(String[] args) throws IOException {
		File portFile = new File(args[1] + ".port");
		File readyFile = new File(args[1] + ".ready");

		if (portFile.exists()) {
			System.err.println("QUILT_IPC_SERVER: IPC file already exists" + portFile);
			System.exit(3);
			return;
		}

		ServerSocket socket = new ServerSocket(0, 0, InetAddress.getByName(null));
		int port = socket.getLocalPort();
		System.out.println("Port = " + port);
		byte[] bytes = { //
			(byte) ((port >>> 24) & 0xFF), //
			(byte) ((port >>> 16) & 0xFF), //
			(byte) ((port >>> 8) & 0xFF), //
			(byte) ((port >>> 0) & 0xFF), //
		};
		Files.write(portFile.toPath(), bytes);
		Files.write(readyFile.toPath(), new byte[0]);
		portFile.deleteOnExit();
		readyFile.deleteOnExit();
		Socket connection = socket.accept();
		new QuiltIPCServerEntry(connection);
	}

	final QuiltProgressWindow progress;
	final QuiltIPC ipc;

	private QuiltIPCServerEntry(Socket connection) {
		progress = new QuiltProgressWindow();
		ipc = new QuiltIPC(connection, false, this::handleMessage);
	}

	private void handleMessage(LoaderValue value) {
		if (value.type() == LType.NULL) {
			System.exit(0);
			return;
		}
		LObject obj = value.asObject();
		LoaderValue type = obj.get("__TYPE");
		if ("QuiltProgressUpdate".equals(type.asString())) {
			String state = obj.get("state").asString();
			int percent = obj.get("percent").asNumber().intValue();
			progress.setProgress(state, percent);
		} else {
			throw new Error("Wrong type! " + value);
		}
	}
}
