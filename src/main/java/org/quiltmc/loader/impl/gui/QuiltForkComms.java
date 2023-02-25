package org.quiltmc.loader.impl.gui;

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
import org.quiltmc.loader.api.LoaderValue.LType;
import org.quiltmc.loader.api.plugin.LoaderValueFactory;
import org.quiltmc.loader.impl.util.LimitedInputStream;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

/** Client entry point for opening communication to a local server. */
@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public class QuiltForkComms {

	private static final String SYS_PROP = "quiltmc.loader.fork.comms_port";

	public static QuiltForkComms connect(File medium, Consumer<LoaderValue> handler) throws IOException {

		Integer overridePort = Integer.getInteger(SYS_PROP);

		QuiltForkComms ipc = new QuiltForkComms(handler);

		if (overridePort == null) {
			File portFile = new File(medium.toString() + ".port");
			File readyFile = new File(medium.toString() + ".ready");
			if (portFile.exists()) {
				portFile.delete();
			}
			if (readyFile.exists()) {
				readyFile.delete();
			}

			List<String> commands = new ArrayList<>();
			commands.add(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java");
			// GC chosen to minimise real memory usage, and maximise returning memory to the OS
			// (since the game is what should actually use more memory)
			commands.add("-Xms4M");
			commands.add("-XX:+UseSerialGC");
			commands.add("-XX:MaxHeapFreeRatio=10");
			commands.add("-XX:MinHeapFreeRatio=2");
			commands.add("-cp");
			commands.add(System.getProperty("java.class.path"));
			commands.add(QuiltForkServerMain.class.getName());

			commands.add("--file");
			commands.add(medium.getAbsolutePath());

			ProcessBuilder pb = new ProcessBuilder(commands);
			pb.redirectError(Redirect.INHERIT);
			pb.redirectOutput(Redirect.INHERIT);

			Process process = pb.start();

			ipc.sender = ipc.new ConnectingSender(portFile, readyFile, process);
		} else {
			ipc.sender = ipc.new ReadySender(overridePort);
		}

		return ipc;
	}

	private static int readPort(File portFile) throws IOException {
		try (FileInputStream fis = new FileInputStream(portFile)) {
			byte[] bytes = new byte[4];
			int index = 0;
			while (true) {
				int read = fis.read(bytes, index, bytes.length - index);
				if (read < 0) {
					throw new IOException("Didn't find the port in " + portFile);
				}
				index += read;
				if (index >= 4) {
					break;
				}
			}

			return (bytes[0] & 0xFF) << 24//
				| (bytes[1] & 0xFF) << 16//
				| (bytes[2] & 0xFF) << 8//
				| (bytes[3] & 0xFF) << 0;
		}
	}

	private final Consumer<LoaderValue> msgHandler;

	/** Set to null if we fail to connect. */
	private volatile BlockingQueue<LoaderValue> writerQueue;

	private Sender sender;
	private volatile Throwable exception;
	private volatile boolean closed;

	QuiltForkComms(Consumer<LoaderValue> msgHandler) {
		this.msgHandler = msgHandler;

		writerQueue = new LinkedBlockingQueue<>();
	}

	QuiltForkComms(Socket socket, Consumer<LoaderValue> msgHandler) {
		this.msgHandler = msgHandler;
		writerQueue = new LinkedBlockingQueue<>();
		sender = new ReadySender(socket);
	}

	public void send(LoaderValue value) {
		try {
			BlockingQueue<LoaderValue> queue = writerQueue;
			if (queue != null) {
				queue.put(value);
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	public boolean didFail() {
		return sender instanceof FailedSender;
	}

	public void close() {
		closed = true;
		writerQueue = null;
	}

	public boolean isClosed() {
		return closed;
	}

	private abstract class Sender {
		// Empty
	}

	private final class ConnectingSender extends Sender {

		final File portFile;
		final File readyFile;
		final Process waitingProcess;
		final Thread waitingThread;

		ConnectingSender(File portFile, File readyFile, Process waitingProcess) {
			this.portFile = portFile;
			this.readyFile = readyFile;
			this.waitingProcess = waitingProcess;
			this.waitingThread = new Thread(this::runWait, "Quilt IPC Launcher");
			waitingThread.setDaemon(true);
			waitingThread.start();
		}

		private void runWait() {
			while (waitingProcess.isAlive()) {
				if (readyFile.isFile()) {
					try {
						QuiltForkComms.this.sender = new ReadySender(readPort(portFile));
						return;
					} catch (IOException e) {
						e.printStackTrace();
						exception = e;
					}
					break;
				}
				try {
					Thread.sleep(10);
				} catch (InterruptedException ignored) {
					// No reason not to sleep since we're a daemon thread
				}
			}
			// Crashed
			QuiltForkComms.this.sender = QuiltForkComms.this.new FailedSender();
		}
	}

	private final class FailedSender extends Sender {
		FailedSender() {
			writerQueue = null;
		}
	}

	private final class ReadySender extends Sender {

		private final Socket socket;
		private final Thread writer, reader;
		private final Executor handler;

		ReadySender(int port) throws IOException {
			this(new Socket(InetAddress.getLoopbackAddress(), port));
		}

		ReadySender(Socket socket) {
			this.socket = socket;
			handler = Executors.newSingleThreadExecutor(new ThreadFactory() {
				final AtomicInteger number = new AtomicInteger();

				@Override
				public Thread newThread(Runnable r) {
					Thread th = new Thread(r, "Quilt IPC Handler " + number.incrementAndGet());
					th.setDaemon(true);
					return th;
				}
			});

			writer = new Thread(this::runWriter, "Quilt IPC Writer");
			writer.setDaemon(true);
			writer.start();

			reader = new Thread(this::runReader, "Quilt IPC Reader");
			reader.setDaemon(true);
			reader.start();
		}

		private void runWriter() {
			try {
				DataOutputStream stream = new DataOutputStream(socket.getOutputStream());
				while (true) {
					try {
						BlockingQueue<LoaderValue> queue = writerQueue;
						LoaderValue value = queue == null ? LoaderValueFactory.getFactory().nul() : queue.take();
						ByteArrayOutputStream baos = new ByteArrayOutputStream();
						LoaderValueFactory.getFactory().write(value, baos);
						byte[] written = baos.toByteArray();
						stream.writeInt(written.length);
						stream.write(written);
						if (queue == null) {
							// Closed
							return;
						}
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}
				}
			} catch (IOException e) {
				if (closed) {
					return;
				}
				e.printStackTrace();
				synchronized (QuiltForkComms.this) {
					if (exception == null) {
						exception = e;
					} else {
						exception.addSuppressed(e);
					}
				}
			}
		}

		private void runReader() {
			WatchingInputStream watchStream = null;
			try {
				watchStream = new WatchingInputStream(socket.getInputStream());
				DataInputStream stream = new DataInputStream(watchStream);
				while (true) {
					int length = stream.readInt();
					LoaderValue value = LoaderValueFactory.getFactory().read(new LimitedInputStream(stream, length));
					handler.execute(() -> msgHandler.accept(value));
					if (value.type() == LType.NULL) {
						close();
						return;
					}
				}
			} catch (IOException e) {
				if (closed) {
					return;
				}
				if (watchStream != null && watchStream.eof) {
					closed = true;
					return;
				}
				e.printStackTrace();
				synchronized (QuiltForkComms.this) {
					if (exception == null) {
						exception = e;
					} else {
						exception.addSuppressed(e);
					}
				}
			}
		}
	}

	/** An {@link InputStream} which sets {@link WatchingInputStream#eof} to true when the underlying input stream
	 * returns -1 from {@link InputStream#read()}. */
	static final class WatchingInputStream extends InputStream {

		final InputStream from;
		boolean eof;

		WatchingInputStream(InputStream from) {
			this.from = from;
		}

		@Override
		public int read() throws IOException {
			int read = from.read();
			if (read == -1) {
				eof = true;
			}
			return read;
		}

		@Override
		public int read(byte[] b, int off, int len) throws IOException {
			int read = from.read(b, off, len);
			if (read == -1) {
				eof = true;
			}
			return read;
		}

		@Override
		public int available() throws IOException {
			return from.available();
		}
	}
}
