package org.quiltmc.loader.impl.ipc;

import java.io.File;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.file.StandardOpenOption;

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
		File f = new File(args[1]);
		if (!f.exists()) {
			System.err.println("QUILT_IPC_SERVER: Missing IPC file " + f);
			System.exit(3);
			return;
		}

		try (FileChannel fc = FileChannel.open(f.toPath(), StandardOpenOption.WRITE, StandardOpenOption.READ)) {
			int size = IpcBuffers.BUFFER_SIZE_ONEWAY;
			MappedByteBuffer dataOut = fc.map(MapMode.READ_WRITE, IpcBuffers.BUFFER_SERVER_WRITE, size);
			MappedByteBuffer dataIn = fc.map(MapMode.READ_ONLY, IpcBuffers.BUFFER_CLIENT_WRITE, size);
			IpcBuffers.writeReaderIndex(dataOut, 8);
			IpcBuffers.writeWriterIndex(dataOut, 8);
			QuiltIPC ipc = new QuiltIPC(dataIn, dataOut, true);
		}
	}
}
