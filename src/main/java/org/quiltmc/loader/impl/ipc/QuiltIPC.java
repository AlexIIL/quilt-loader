package org.quiltmc.loader.impl.ipc;

import java.io.File;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/** Client entry point for opening communication to a local server. */
public class QuiltIPC {

	private static final String SYS_PROP = "quiltmc.ipc.is_forked_client";

	public static QuiltIPC connect(File medium) throws IOException {

		boolean start = !Boolean.getBoolean(SYS_PROP);

		try (FileChannel fc = FileChannel.open(
			medium.toPath(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE,
			StandardOpenOption.READ
		)) {
			int size = IpcBuffers.BUFFER_SIZE_ONEWAY;
			MappedByteBuffer dataOut = fc.map(MapMode.READ_WRITE, IpcBuffers.BUFFER_CLIENT_WRITE, size);

			if (start) {
				MappedByteBuffer svOut = fc.map(MapMode.READ_WRITE, IpcBuffers.BUFFER_SERVER_WRITE, size);
				IpcBuffers.writeReaderIndex(svOut, 8);
				IpcBuffers.writeWriterIndex(svOut, 8);
			}

			MappedByteBuffer dataIn = fc.map(MapMode.READ_ONLY, IpcBuffers.BUFFER_SERVER_WRITE, size);

			IpcBuffers.writeReaderIndex(dataOut, 8);
			IpcBuffers.writeWriterIndex(dataOut, 8);

			if (start) {
				List<String> commands = new ArrayList<>();
				commands.add(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java");
				commands.add("-cp");
				// Is this a good idea?
				// I don't think we actually care about most of the classes here
				// just *this* jar file?
				commands.add(System.getProperty("java.class.path"));
				commands.add(QuiltIPCServerEntry.class.getName());

				commands.add("--file");
				commands.add(medium.getAbsolutePath());

				ProcessBuilder pb = new ProcessBuilder(commands);
				pb.redirectError(Redirect.INHERIT);
				pb.redirectOutput(Redirect.INHERIT);

				pb.start();
			}

			return new QuiltIPC(dataIn, dataOut, false);
		}
	}

	final MappedByteBuffer dataOut, dataIn;
	final Thread writer, reader;

	final BlockingQueue<byte[]> writerQueue;

	QuiltIPC(MappedByteBuffer dataIn, MappedByteBuffer dataOut, boolean isServer) {
		// TODO: Remove the "isServer" argument, since the server will instead open a swing GUI.
		this.dataIn = dataIn;
		this.dataOut = dataOut;

		writerQueue = new LinkedBlockingQueue<>();

		writer = new Thread("Quilt IPC Writer") {
			@Override
			public void run() {
				while (true) {
					try {
						byte[] nextByteArray = writerQueue.take();
						int index = 0;

						while (index < nextByteArray.length) {

							int writerIndex = IpcBuffers.readWriterIndex(dataOut);
							int readerIndex = IpcBuffers.readReaderIndex(dataIn);

							// Writer Index = where to start writing
							// Reader Index = where to stop writing

							int writeable = 0;

							if (readerIndex == writerIndex) {
								// No data left to read
								int end = IpcBuffers.BUFFER_SIZE_ONEWAY;
								if (writerIndex < end) {
									writeable = end - writerIndex;
								} else {
									// Reset
									writerIndex = 8;
									writeable = end - writerIndex;
								}
							} else if (readerIndex + 1 < writerIndex) {
								/* _____[RI...data..WI]_____ */
								int end = IpcBuffers.BUFFER_SIZE_ONEWAY;
								if (writerIndex < end) {
									writeable = end - writerIndex;
								} else if (readerIndex > 10) {
									// Reset
									writerIndex = 8;
									writeable = readerIndex - 1 - writerIndex;
								} else {
									writeable = 0;
								}
							} else if (writerIndex + 1 < readerIndex) {
								/* ..data..WI]__________[RI...data... */
								writeable = readerIndex - 1 - writerIndex;
							}

							if (writeable <= 0) {
								try {
									Thread.sleep(10);
								} catch (InterruptedException e) {
									Thread.currentThread().interrupt();
								}
								continue;
							}

							int length = Math.min(writeable, nextByteArray.length - index);

							dataOut.position(writerIndex);
							dataOut.put(nextByteArray, index, length);
							index += length;
							dataOut.position(0);
							IpcBuffers.writeWriterIndex(dataOut, writerIndex + length);
						}

					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}
				}
			}
		};
		writer.setDaemon(!isServer);
		writer.start();

		reader = new Thread("Quilt IPC Reader") {

			Random rand = new Random(42);
			int index = 0;

			@Override
			public void run() {
				int waitTime = 5;
				while (true) {
					int readerIndex = IpcBuffers.readReaderIndex(dataOut);
					int writerIndex = IpcBuffers.readWriterIndex(dataIn);
					if (readerIndex == writerIndex) {
						try {
							Thread.sleep(waitTime);
						} catch (InterruptedException e) {
							Thread.currentThread().interrupt();
						}
						waitTime += 5;
						if (waitTime > 50) {
							waitTime = 50;
						}
						continue;
					}

					waitTime = 1;

					int readable = writerIndex - readerIndex;

					if (readable < 0) {
						// Loop
						int end = IpcBuffers.BUFFER_SIZE_ONEWAY;
						if (readerIndex == end) {
							readerIndex = 8;
							readable = writerIndex - readerIndex;
						} else {
							readable = end - readerIndex;
						}
					}

					dataIn.position(readerIndex);
					byte[] array = new byte[readable];
					dataIn.get(array);
					readerIndex += readable;
					IpcBuffers.writeReaderIndex(dataOut, readerIndex);
					for (byte b : array) {

						int fr = rand.nextInt(256);
						index++;
						if (b != (byte) fr) {
							throw new Error("Different at index " + index);
						}
					}
				}
			}
		};
		reader.setDaemon(!isServer);
		reader.start();
	}
}
