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

package org.quiltmc.loader.impl.transformer;

import java.io.BufferedReader;
import java.io.IOError;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.quiltmc.loader.api.FasterFiles;
import org.quiltmc.loader.api.plugin.solver.ModLoadOption;
import org.quiltmc.loader.api.plugin.solver.ModSolveResult;
import org.quiltmc.loader.impl.discovery.ModResolutionException;
import org.quiltmc.loader.impl.discovery.RuntimeModRemapper;
import org.quiltmc.loader.impl.filesystem.QuiltMemoryFileStore.ReadWrite;
import org.quiltmc.loader.impl.filesystem.PartiallyWrittenIOException;
import org.quiltmc.loader.impl.filesystem.QuiltMemoryFileSystem;
import org.quiltmc.loader.impl.filesystem.QuiltMemoryPath;
import org.quiltmc.loader.impl.filesystem.QuiltZipFileSystem;
import org.quiltmc.loader.impl.filesystem.QuiltZipPath;
import org.quiltmc.loader.impl.util.FileSystemUtil;
import org.quiltmc.loader.impl.util.HashUtil;
import org.quiltmc.loader.impl.util.SystemProperties;
import org.quiltmc.loader.impl.util.log.Log;
import org.quiltmc.loader.impl.util.log.LogCategory;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public class TransformCache {

	private static final String FILE_TRANSFORM_COMPLETE = "__TRANSFORM_COMPLETE";

	public static QuiltZipPath populateTransformBundle(Path transformCacheFile, List<ModLoadOption> modList,
		ModSolveResult result) throws ModResolutionException {
		Map<String, String> map = new TreeMap<>();
		// Mod order is important? For now, assume it is
		int index = 0;
		for (ModLoadOption mod : modList) {
			map.put("mod#" + index++, mod.id());
		}

		for (Entry<String, ModLoadOption> provided : result.providedMods().entrySet()) {
			map.put("provided-mod:" + provided.getKey(), provided.getValue().metadata().id());
		}

		for (Entry<String, ModLoadOption> mod : result.directMods().entrySet()) {
			try {
				byte[] hash = mod.getValue().computeOriginHash();
				map.put("mod:" + mod.getKey(), HashUtil.hashToString(hash));
			} catch (IOException io) {
				throw new ModResolutionException("Failed to compute the hash of " + mod.getValue(), io);
			}
		}

		try {
			Files.createDirectories(transformCacheFile.getParent());
		} catch (IOException e) {
			throw new ModResolutionException("Failed to create parent directories of the transform cache file!", e);
		}

		QuiltZipPath existing = checkTransformCache(transformCacheFile, map);
		if (existing != null) {
			return existing;
		}

		return createTransformCache(transformCacheFile, toString(map), modList, result);
	}

	private static String toString(Map<String, String> map) {
		StringBuilder optionList = new StringBuilder();
		for (Entry<String, String> entry : map.entrySet()) {
			optionList.append(entry.getKey());
			optionList.append("=");
			optionList.append(entry.getValue());
			optionList.append("\n");
		}
		String options = optionList.toString();
		optionList = null;
		return options;
	}

	private static QuiltZipPath checkTransformCache(Path transformCacheFile, Map<String, String> options) throws ModResolutionException {
		if (!FasterFiles.exists(transformCacheFile)) {
			Log.info(LogCategory.CACHE, "Not reusing previous transform cache since it's missing");
			return null;
		}
		try (QuiltZipFileSystem fs = new QuiltZipFileSystem("transform-cache", transformCacheFile, "")) {
			QuiltZipPath inner = fs.getRoot();
			if (!FasterFiles.isRegularFile(inner.resolve(FILE_TRANSFORM_COMPLETE))) {
				Log.info(LogCategory.CACHE, "Not reusing previous transform cache since it's incomplete!");
				return null;
			}
			Path optionFile = inner.resolve("options.txt");

			try (BufferedReader br = Files.newBufferedReader(optionFile, StandardCharsets.UTF_8)) {
				String line;
				Map<String, String> oldOptions = new TreeMap<>(options);
				Map<String, String> newOptions = new TreeMap<>();
				Map<String, String> differingOptions = new TreeMap<>();
				while ((line = br.readLine()) != null) {
					if (line.isEmpty()) {
						continue;
					}
					int eq = line.indexOf('=');
					String key = line.substring(0, eq);
					String value = line.substring(eq + 1);
					String oldValue = oldOptions.remove(key);
					if (oldValue != null) {
						if (!value.equals(oldValue)) {
							differingOptions.put(key, value);
						}
					} else {
						newOptions.put(key, value);
					}
				}

				if (!oldOptions.isEmpty() || !newOptions.isEmpty() || !differingOptions.isEmpty()) {
					Log.info(LogCategory.CACHE, "Not reusing previous transform cache since it has different keys:");

					for (Map.Entry<String, String> old : oldOptions.entrySet()) {
						Log.info(LogCategory.CACHE, "  Missing: '" + old.getKey() + "': '" + old.getValue() + "'");
					}

					for (Map.Entry<String, String> added : newOptions.entrySet()) {
						Log.info(LogCategory.CACHE, "  Included: '" + added.getKey() + "': '" + added.getValue() + "'");
					}

					for (Map.Entry<String, String> diff : differingOptions.entrySet()) {
						String key = diff.getKey();
						String oldValue = diff.getValue();
						String newValue = options.get(key);
						Log.info(LogCategory.CACHE, "  Different: '" + key + "': '" + oldValue + "' -> '" + newValue + "'");
					}
					return null;
				}
			}
			return inner;
		} catch (IOException | IOError io) {
			if (io instanceof PartiallyWrittenIOException) {
				Log.info(LogCategory.CACHE, "Not reusing previous transform cache since it's incomplete!");
			} else {
				Log.info(LogCategory.CACHE, "Not reusing previous transform cache since something went wrong while reading it!");
			}
			try {
				Files.delete(transformCacheFile);
			} catch (IOException e) {
				ModResolutionException ex = new ModResolutionException(
					"Failed to read an older transform cache file " + transformCacheFile + " and then delete it!", e
				);
				ex.addSuppressed(io);
				throw ex;
			}
			return null;
		}
	}

	static final boolean WRITE_CUSTOM = true;

	private static QuiltZipPath createTransformCache(Path transformCacheFile, String options, List<ModLoadOption> modList,
		ModSolveResult result) throws ModResolutionException {

		if (FasterFiles.exists(transformCacheFile)) {
			try {
				Files.delete(transformCacheFile);
			} catch (IOException e) {
				throw new ModResolutionException("Failed to delete the previous transform bundle!", e);
			}
		}

		if (!Boolean.getBoolean(SystemProperties.DISABLE_OPTIMIZED_COMPRESSED_TRANSFORM_CACHE)) {
			try (QuiltMemoryFileSystem.ReadWrite rw = new QuiltMemoryFileSystem.ReadWrite("transform-cache", true)) {
				QuiltMemoryPath root = rw.getRoot();
				Files.write(root.resolve("options.txt"), options.getBytes(StandardCharsets.UTF_8));
				populateTransformCache(root, modList, result);
				// This isn't actually necessary
				Files.createFile(root.resolve(FILE_TRANSFORM_COMPLETE));
				QuiltZipFileSystem.writeQuiltCompressedFileSystem(root, transformCacheFile);

				return openCache(transformCacheFile);
			} catch (IOException e) {
				throw new ModResolutionException("Failed to create the transform bundle!", e);
			}
		}

		try (FileSystemUtil.FileSystemDelegate fs = FileSystemUtil.getJarFileSystem(transformCacheFile, true)) {
			URI fileUri = transformCacheFile.toUri();
			URI zipUri = new URI("jar:" + fileUri.getScheme(), fileUri.getPath(), null);

			Path inner = fs.get().getPath("/");

			populateTransformCache(inner, modList, result);

			Files.write(inner.resolve("options.txt"), options.getBytes(StandardCharsets.UTF_8));
			Files.createFile(inner.resolve(FILE_TRANSFORM_COMPLETE));

		} catch (IOException e) {
			throw new ModResolutionException("Failed to create the transform bundle!", e);
		} catch (URISyntaxException e) {
			throw new ModResolutionException(e);
		}

		return openCache(transformCacheFile);
	}

	private static QuiltZipPath openCache(Path transformCacheFile) throws ModResolutionException {
		try {
			return new QuiltZipFileSystem("transform-cache", transformCacheFile, "").getRoot();
		} catch (IOException e) {
			// TODO: Better error message for the gui!
			throw new ModResolutionException("Failed to read the newly written transform cache!", e);
		}
	}

	private static void populateTransformCache(Path root, List<ModLoadOption> modList, ModSolveResult result)
		throws ModResolutionException {
		RuntimeModRemapper.remap(root, modList);
		if (Boolean.getBoolean(SystemProperties.ENABLE_EXPERIMENTAL_CHASM)) {
			ChasmInvoker.applyChasm(root, modList, result);
		}
	}
}
