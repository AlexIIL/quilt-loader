/*
 * Copyright 2022, 2023 QuiltMC
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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.objectweb.asm.ClassReader;
import org.quiltmc.loader.api.FasterFiles;
import org.quiltmc.loader.api.LoaderValue;
import org.quiltmc.loader.api.LoaderValue.LArray;
import org.quiltmc.loader.api.LoaderValue.LType;
import org.quiltmc.loader.api.plugin.solver.ModLoadOption;
import org.quiltmc.loader.api.plugin.solver.ModSolveResult;
import org.quiltmc.loader.impl.discovery.ModResolutionException;
import org.quiltmc.loader.impl.util.ExceptionUtil;
import org.quiltmc.loader.impl.util.FileUtil;
import org.quiltmc.loader.impl.util.LoaderUtil;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;
import org.quiltmc.loader.impl.util.log.Log;
import org.quiltmc.loader.impl.util.log.LogCategory;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
class ChasmInvoker {

	static void applyChasm(Path root, List<ModLoadOption> modList, ModSolveResult result)
		throws ModResolutionException {
		try {
			applyChasm0(root, modList, result);
		} catch (Throwable t) {
			throw new ChasmTransformException("Failed to apply chasm!", t);
		}
	}

	static void applyChasm0(Path root, List<ModLoadOption> modList, ModSolveResult solveResult) throws Throwable {
		Map<String, String> package2mod = new HashMap<>();
		Map<String, byte[]> inputClassCache = new HashMap<>();

		// TODO: Move chasm searching to here!
		for (ModLoadOption mod : modList) {
			Path path2 = root.resolve(mod.id());
			if (!FasterFiles.isDirectory(path2)) {
				continue;
			}
			Files.walkFileTree(path2, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					if (file.getFileName().toString().endsWith(".class")) {
						package2mod.put(path2.relativize(file.getParent()).toString(), mod.id());
						inputClassCache.put(path2.relativize(file).toString(), Files.readAllBytes(file));
					}
					return FileVisitResult.CONTINUE;
				}
			});
		}

		Lookup lookup = MethodHandles.lookup();
		Class<?> chasmAPI = Class.forName("org.quiltmc.chasm.api.ChasmReflectedApi");

		MethodHandle create = lookup.findStatic(chasmAPI, "createProcessor",
			MethodType.methodType(Object.class, MethodHandle.class));

		MethodHandle addClass = lookup.findStatic(chasmAPI, "addClass", 
			MethodType.methodType(void.class, Object.class, byte[].class, Object.class));

		MethodHandle addTransformer = lookup.findStatic(chasmAPI, "addTransformer",
			MethodType.methodType(void.class, Object.class, String.class, Path.class));

		Object chasm = create.invokeExact(lookup.findStatic(ChasmInvoker.class, "readFile", 
			MethodType.methodType(byte[].class, String.class)));

		for (ModLoadOption mod : modList) {
			Path modPath = root.resolve(mod.id());
			if (!FasterFiles.exists(modPath)) {
				continue;
			}

			// QMJ spec: "experimental_chasm_transformers"
			// either a string, or a list of strings
			// each string is a folder which will be recursively searched for chasm transformers.
			LoaderValue value = mod.metadata().value("experimental_chasm_transformers");

			final String[] paths;
			if (value == null) {
				paths = new String[0];
			} else if (value.type() == LType.STRING) {
				paths = new String[] { value.asString() };
			} else if (value.type() == LType.ARRAY) {
				LArray array = value.asArray();
				paths = new String[array.size()];
				for (int i = 0; i < array.size(); i++) {
					LoaderValue entry = array.get(i);
					if (entry.type() == LType.STRING) {
						paths[i] = entry.asString();
					} else {
						Log.warn(LogCategory.CHASM, "Unknown value found for 'experimental_chasm_transformers[" + i + "]' in " + mod.id());
					}
				}
			} else {
				paths = new String[0];
				Log.warn(LogCategory.CHASM, "Unknown value found for 'experimental_chasm_transformers' in " + mod.id());
			}

			List<Path> chasmRoots = new ArrayList<>();

			for (String path : paths) {
				if (path == null) {
					continue;
				}
				try {
					chasmRoots.add(modPath.resolve(path.replace("/", modPath.getFileSystem().getSeparator())));
				} catch (InvalidPathException e) {
					Log.warn(LogCategory.CHASM, "Invalid path '" + path + "' for 'experimental_chasm_transformers' in " + mod.id());
				}
			}

			Files.walkFileTree(modPath, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					if (file.getFileName().toString().endsWith(".class")) {
						byte[] bytes = Files.readAllBytes(file);
						try {
							addClass.invokeExact(chasm, bytes, new QuiltMetadata(mod));
						} catch (Throwable t) {
							throw ExceptionUtil.wrap(t);
						}
					} else if (file.getFileName().toString().endsWith(".chasm")) {
						for (Path chasmRoot : chasmRoots) {
							if (file.startsWith(chasmRoot)) {
								String chasmId = mod.id() + ":" + chasmRoot.relativize(file).toString();
								chasmId = chasmId.replace(chasmRoot.getFileSystem().getSeparator(), "/");
								if (chasmId.endsWith(".chasm")) {
									chasmId = chasmId.substring(0, chasmId.length() - ".chasm".length());
								}
								Log.info(LogCategory.CHASM, "Found chasm transformer: '" + chasmId + "'");
								try {
									addTransformer.invokeExact(chasm, chasmId, file);
								} catch (Throwable t) {
									throw ExceptionUtil.wrap(t);
								}
								break;
							}
						}
					}
					return FileVisitResult.CONTINUE;
				}
			});
		}

		MethodHandle process = lookup.findStatic(chasmAPI, "process",
			MethodType.methodType(Iterable.class, Object.class));

		MethodHandle getResultType = lookup.findStatic(chasmAPI, "getResultType",
			MethodType.methodType(String.class, Object.class));

		MethodHandle getResultBytes = lookup.findStatic(chasmAPI, "getResultBytes",
			MethodType.methodType(byte[].class, Object.class));

		MethodHandle getExternalMeta = lookup.findStatic(chasmAPI, "getExternalMeta",
			MethodType.methodType(Object.class, Object.class));

		Iterable<?> result = (Iterable<?>) process.invokeExact(chasm);

		for (Object cls : result) {
			String type = (String) getResultType.invokeExact(cls);
			switch (type) {
				case "ADDED":
				case "MODIFIED": {
					Object externalMeta = getExternalMeta.invokeExact(cls);
					QuiltMetadata qm = (QuiltMetadata) externalMeta;
					byte[] bytes = (byte[]) getResultBytes.invokeExact(cls);
					ClassReader cr = new ClassReader(bytes);
					String className = cr.getClassName();
					final Path rootTo;
					if (qm != null) {
						rootTo = root.resolve(qm.from.id());
					} else {
						String mod = package2mod.get(className.substring(0, className.lastIndexOf('/')));
						if (mod == null) {
							mod = TransformCache.TRANSFORM_CACHE_NONMOD_CLASSLOADABLE;
							throw new AbstractMethodError("// TODO: Support classloading from unknown mods!");
						}
						rootTo = root.resolve(mod);
					}
					Path to = rootTo.resolve(LoaderUtil.getClassFileName(className));
					Files.createDirectories(to.getParent());
					Files.write(to, bytes);
					break;
				}
				case "UNMODIFIED": {
					break;
				}
				case "REMOVED": {
					// We need to prevent resource loading from accessing this file.

					// Deleting the file from the transform cache isn't enough.
					// QuiltLoaderImpl#setup() is missing functionality
					// and so are the file systems?
					throw new AbstractMethodError("// TODO: Support REMOVED files in the path system!");
				}
				default: {
					throw new UnsupportedChasmException(
						"Chasm returned an unknown 'ClassResult.getType()': ''" + type
							+ "' - you might need to update loader?"
					);
				}
			}
		}
	}

	private static byte[] readFile(String filePath) {
		try (InputStream stream = ChasmInvoker.class.getClassLoader().getResourceAsStream(filePath)) {
			return stream != null ? FileUtil.readAllBytes(stream) : null;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	static class QuiltMetadata {

		final ModLoadOption from;

		public QuiltMetadata(ModLoadOption from) {
			this.from = from;
		}
	}
}
