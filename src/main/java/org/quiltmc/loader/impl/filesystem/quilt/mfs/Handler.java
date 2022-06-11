/*
 * Copyright 2022 QuiltMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.quiltmc.loader.impl.filesystem.quilt.mfs;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.file.Files;
import java.nio.file.Path;

import org.quiltmc.loader.impl.filesystem.QuiltMemoryFileSystem;
import org.quiltmc.loader.impl.filesystem.QuiltMemoryFileSystemProvider;

/** {@link URLStreamHandler} for {@link QuiltMemoryFileSystem}. */
public class Handler extends URLStreamHandler {
	@Override
	protected URLConnection openConnection(URL u) throws IOException {
		Path path;
		try {
			path = QuiltMemoryFileSystemProvider.instance().getPath(u.toURI());
		} catch (URISyntaxException e) {
			throw new IOException(e);
		}

		return new URLConnection(u) {
			@Override
			public void connect() {
				// No-op
			}

			@Override
			public InputStream getInputStream() throws IOException {
				return Files.newInputStream(path);
			}
		};
	}
}
