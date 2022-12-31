package org.quiltmc.loader.impl.ipc;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ProcessBuilder.Redirect;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.quiltmc.loader.api.LoaderValue;
import org.quiltmc.loader.api.plugin.LoaderValueFactory;

/** Client entry point for opening communication to a local server. Fairly low level -  */
public class QuiltIPC {

	private static final String SYS_PROP = "quiltmc.ipc.is_forked_client";

	public static QuiltIPC connect(File medium, Consumer<LoaderValue> handler) throws IOException {

		boolean start = !Boolean.getBoolean(SYS_PROP);

		File portFile = new File(medium.toString() + ".port");
		File readyFile = new File(medium.toString() + ".ready");

		if (start) {
			if (portFile.exists()) {
				portFile.delete();
			}
			if (readyFile.exists()) {
				readyFile.delete();
			}

			List<String> commands = new ArrayList<>();
			commands.add(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java");
			commands.add("-cp");
			commands.add(System.getProperty("java.class.path"));
			commands.add(QuiltIPCServerEntry.class.getName());

			commands.add("--file");
			commands.add(medium.getAbsolutePath());

			ProcessBuilder pb = new ProcessBuilder(commands);
			pb.redirectError(Redirect.INHERIT);
			pb.redirectOutput(Redirect.INHERIT);

			pb.start();

			for (int cycle = 0; cycle < 10; cycle++) {
				if (readyFile.isFile()) {
					break;
				}
				try {
					Thread.sleep((cycle + 1) * (cycle + 1));
				} catch (InterruptedException ignored) {}
			}
		}

		int port;

		try (FileInputStream fis = new FileInputStream(portFile)) {
			byte[] bytes = new byte[4];
			int index = 0;
			while (true) {
				int read = fis.read(bytes, index, bytes.length - index);
				if (read < 0) {
					throw new IOException("Didn't find the port in " + medium);
				}
				index += read;
				if (index >= 4) {
					break;
				}
			}

			port = (bytes[0] & 0xFF) << 24//
				| (bytes[1] & 0xFF) << 16//
				| (bytes[2] & 0xFF) << 8//
				| (bytes[3] & 0xFF) << 0;
		}

		return new QuiltIPC(new Socket(InetAddress.getByName(null), port), false, handler);
	}

	final Socket socket;
	final Thread writer, reader;
	final Executor handler;
	final Consumer<LoaderValue> msgHandler;

	final BlockingQueue<LoaderValue> writerQueue;

	volatile Throwable exception;

	QuiltIPC(Socket socket, boolean isServer, Consumer<LoaderValue> msgHandler) {
		// TODO: Remove the "isServer" argument, since the server will instead open a swing GUI.
		this.socket = socket;
		this.msgHandler = msgHandler;

		writerQueue = new LinkedBlockingQueue<>();
		handler = Executors.newSingleThreadExecutor(new ThreadFactory() {
			final AtomicInteger number = new AtomicInteger();

			@Override
			public Thread newThread(Runnable r) {
				Thread th = new Thread(r, "Quilt IPC Handler " + number.incrementAndGet());
				th.setDaemon(!isServer);
				return th;
			}
		});

		writer = new Thread(this::runWriter, "Quilt IPC Writer");
		writer.setDaemon(!isServer);
		writer.start();

		reader = new Thread(this::runReader, "Quilt IPC Reader");
		reader.setDaemon(!isServer);
		reader.start();
	}

	public void send(LoaderValue value) {
		try {
			writerQueue.put(value);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	private void runWriter() {
		try {
			DataOutputStream stream = new DataOutputStream(socket.getOutputStream());
			while (true) {
				try {
					LoaderValue value = writerQueue.take();
					ByteArrayOutputStream baos = new ByteArrayOutputStream();
					LoaderValueFactory.getFactory().write(value, baos);
					byte[] written = baos.toByteArray();
					stream.writeInt(written.length);
					stream.write(written);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
			synchronized (this) {
				if (exception == null) {
					exception = e;
				} else {
					exception.addSuppressed(e);
				}
			}
		}
	}

	private void runReader() {
		try {
			DataInputStream stream = new DataInputStream(socket.getInputStream());
			while (true) {
				int length = stream.readInt();
				LoaderValue value = LoaderValueFactory.getFactory().read(new LimitedInputStream(stream, length));
				handler.execute(() -> msgHandler.accept(value));
			}
		} catch (IOException e) {
			e.printStackTrace();
			synchronized (this) {
				if (exception == null) {
					exception = e;
				} else {
					exception.addSuppressed(e);
				}
			}
		}
	}

	private static final class LimitedInputStream extends InputStream {
		private final InputStream from;
		private final int limit;

		private int position;

		public LimitedInputStream(InputStream from, int limit) {
			this.from = from;
			this.limit = limit;
		}

		@Override
		public int available() throws IOException {
			return limit - position;
		}

		@Override
		public int read() throws IOException {
			if (position < limit) {
				position++;
				return from.read();
			} else {
				return -1;
			}
		}

		@Override
		public int read(byte[] b, int off, int len) throws IOException {
			if (len <= 0) {
				return 0;
			}
			int max = Math.min(len, limit - position);
			if (max <= 0) {
				return -1;
			}
			int read = from.read(b, off, max);
			if (read > 0) {
				position += read;
			}
			return read;
		}
	}
}
