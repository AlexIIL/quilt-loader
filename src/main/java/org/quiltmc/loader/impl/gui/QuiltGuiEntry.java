/*
 * Copyright 2016 FabricMC
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

package org.quiltmc.loader.impl.gui;

import java.awt.GraphicsEnvironment;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.ProcessBuilder.Redirect;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.quiltmc.json5.JsonReader;
import org.quiltmc.json5.JsonWriter;
import org.quiltmc.loader.impl.QuiltLoaderImpl;
import org.quiltmc.loader.impl.game.GameProvider;
import org.quiltmc.loader.impl.gui.QuiltJsonGui.QuiltJsonGuiTreeTab;
import org.quiltmc.loader.impl.util.log.Log;
import org.quiltmc.loader.impl.util.log.LogCategory;

/** The main entry point for all quilt-based stuff. */
public final class QuiltGuiEntry {
	/** Opens the given {@link QuiltJsonGui} in a new swing window.
	 * 
	 * @throws Exception if something went wrong while opening the window. */
	public static void open(QuiltJsonGui tree) throws Exception {
		open(tree, null, true);
	}

	/** Opens the given {@link QuiltJsonGui} in a new swing window.
	 * 
	 * @param forceFork If true then this will create a new process to host the window, false will always use this
	 *            process, and null will only fork if the current operating system doesn't support LWJGL + swing windows
	 *            at the same time (such as mac osx).
	 * @param shouldWait If true then this call will wait until either the user clicks the "continue" button or the
	 *            window is closed before returning, otherwise this method will return as soon as the window has opened.
	 * @throws Exception if something went wrong while opening the window. */
	public static void open(QuiltJsonGui tree, Boolean forceFork, boolean shouldWait) throws Exception {
		final boolean fork;

		if (forceFork != null) {
			fork = forceFork;
		} else {
			fork = shouldFork();
		}

		if (fork) {
			fork(tree, shouldWait);
		} else {
			openWindow(tree, shouldWait);
		}
	}

	private static boolean shouldFork() {
		String osName = System.getProperty("os.name").toLowerCase(Locale.ROOT);

		if (osName.contains("mac")) {
			return true;
		}

		return false;
	}

	private static void openWindow(QuiltJsonGui tree, boolean shouldWait) throws Exception {
		QuiltMainDisplay.open(tree, shouldWait);
//		QuiltMainWindow.open(tree, shouldWait);
	}

	/** @param exitAfter If true then this will call {@link System#exit(int)} after showing the gui, otherwise this will
	 *            return normally. */
	public static void displayError(String mainText, Throwable exception, boolean warnEarly, boolean exitAfter) {
		if (warnEarly) {
			Log.error(LogCategory.GUI, "An error occurred: " + mainText, exception);
		}

		GameProvider provider = QuiltLoaderImpl.INSTANCE.tryGetGameProvider();

		if ((provider == null || provider.canOpenErrorGui()) && !GraphicsEnvironment.isHeadless()) {
			String title = "Quilt Loader " + QuiltLoaderImpl.VERSION;
			QuiltJsonGui tree = new QuiltJsonGui(title, mainText);
			QuiltJsonGuiTreeTab crashTab = tree.addTab("Crash");

			crashTab.node.addCleanedException(exception);

			// Maybe add an "open mods folder" button?
			// or should that be part of the main tree's right-click menu?
			// TODO: Add crash report generation functionality?
			// and then have a button to open a file explorer pointed to that file
			// and a button to open that file directly
			tree.addButton("Exit", QuiltJsonGui.QuiltBasicButtonType.CLICK_ONCE).makeClose();

			try {
				open(tree);
			} catch (Exception e) {
				if (exitAfter) {
					Log.warn(LogCategory.GUI, "Failed to open the error gui!", e);
				} else {
					throw new RuntimeException("Failed to open the error gui!", e);
				}
			}
		}

		if (exitAfter) {
			System.exit(1);
		}
	}

	private static void fork(QuiltJsonGui tree, boolean shouldWait) throws Exception {
		List<String> commands = new ArrayList<>();
		commands.add(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java");
		commands.add("-cp");
		// Is this a good idea?
		// I don't think we actually care about most of the classes here
		// just *this* jar file?
		commands.add(System.getProperty("java.class.path"));
		commands.add(QuiltGuiEntry.class.getName());

		File jsonFile = File.createTempFile("quilt-loader-tree", ".json.gz");

		try (OutputStream out = new GZIPOutputStream(new BufferedOutputStream(new FileOutputStream(jsonFile)))) {
			try (JsonWriter writer = JsonWriter.json(new OutputStreamWriter(out, StandardCharsets.UTF_8))) {
				writer.setIndent(" ");
				tree.write(writer);
				writer.flush();
			}
		}
		commands.add("--read-tree");
		commands.add(jsonFile.getAbsolutePath());

		ProcessBuilder pb = new ProcessBuilder(commands);
		pb.redirectError(Redirect.PIPE);
		// TODO: handle more complex button presses by sending data over sysout?
		pb.redirectOutput(Redirect.PIPE);

		Process p = pb.start();

		// TODO: Handle input (like "Status: Correct tree", or "Status: Continue")

		jsonFile.deleteOnExit();

		if (!shouldWait) {
			return;
		}

		int result = p.waitFor();

		if (result != 0) {
			throw new Exception("Failed to open the gui! (The error should be higher up in the log/output)");
		}
	}

	/** The entry point after forking the main application over into a different process to get around incompatibilities
	 * on OSX (and problems where some games switch swing over to use headless mode, which doesn't work very well for
	 * us). */
	public static void main(String[] args) throws Exception {
		if (args.length == 2 && "--read-tree".equals(args[0])) {
			System.out.println("Status:Reading");
			File from = new File(args[1]);

			try (InputStream is = new GZIPInputStream(new BufferedInputStream(new FileInputStream(from)))) {
				JsonReader reader = JsonReader.json(new InputStreamReader(is, StandardCharsets.UTF_8));
				QuiltJsonGui tree = new QuiltJsonGui(reader);
				System.out.println("Status:Opening");
				openWindow(tree, true);
			}
		} else {
			System.err.println("Expected 2 arguments: '--read-tree' followed by the tree.");
			System.err.println("Actually got " + args.length);

			for (String arg : args) {
				System.err.println(arg);
			}

			System.exit(-1);
		}
	}
}
