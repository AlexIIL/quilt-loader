package org.quiltmc.loader.impl.ipc;

import java.nio.MappedByteBuffer;

/** Stores various constants for buffers. */
class IpcBuffers {

	/** The size of a data buffer going from client->server.
	 * <p>
	 * The first two bytes are the "writer index" of this buffer. The third byte is a checksum byte (byte A ^ byte B ^
	 * 0x5A), and the forth byte is also a checksum byte (byte A ^ byte B ^ 0xA5). The fith, sixth, seventh, and eighth
	 * bytes are the same encoding, but the reader index of the *other* buffer.
	 * <p>
	 * All remaining bytes (from [8] to [4095]) are used for temporary data. */
	static final int BUFFER_SIZE_ONEWAY = 1 << 12;
	static final int BUFFER_CLIENT_WRITE = 0;
	static final int BUFFER_SERVER_WRITE = BUFFER_SIZE_ONEWAY;

	static void writeWriterIndex(MappedByteBuffer buffer, int index) {
		writeIndex(0, buffer, index);
	}

	static void writeReaderIndex(MappedByteBuffer buffer, int index) {
		writeIndex(4, buffer, index);
	}

	private static void writeIndex(int offset, MappedByteBuffer buffer, int index) {
		int b0 = 0xFF & (index >> 8);
		int b1 = 0xFF & index;
		int b2 = b0 ^ b1 ^ 0x5A;
		int b3 = b0 ^ b1 ^ 0xA5;

		// Ensure the value is wrong immediately
		// (This helps ensure it's not a valid checksum for both the previous and in-between value?)
		// ((in theory))
		buffer.put(offset + 3, (byte) (1));

		buffer.put(offset + 0, (byte) b0);
		buffer.put(offset + 1, (byte) b1);
		buffer.put(offset + 2, (byte) b2);
		buffer.put(offset + 3, (byte) b3);
	}

	static int readWriterIndex(MappedByteBuffer buffer) {
		return readIndex(0, buffer);
	}

	static int readReaderIndex(MappedByteBuffer buffer) {
		return readIndex(4, buffer);
	}

	private static int readIndex(int offset, MappedByteBuffer buffer) {
		for (int attempt = 0; true; attempt++) {
			int b0 = 0xFF & buffer.get(offset + 0);
			int b1 = 0xFF & buffer.get(offset + 1);
			int b2 = 0xFF & buffer.get(offset + 2);
			int b3 = 0xFF & buffer.get(offset + 3);
			int c2 = b0 ^ b1 ^ 0x5A;
			int c3 = b0 ^ b1 ^ 0xA5;
			if (b2 == c2 && b3 == c3) {
				return b0 << 8 | b1;
			}

			try {
				Thread.sleep((attempt + 1) * (attempt + 1));
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new Error("Inturrupt");
			}
			if (attempt == 3) {
				throw new Error("Value is wrong! " + b0 + ", " + b1 + ", " + b2 + ", " + b3);
			}
		}
	}
}
