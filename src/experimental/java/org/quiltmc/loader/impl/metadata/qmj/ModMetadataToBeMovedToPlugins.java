package org.quiltmc.loader.impl.metadata.qmj;

import java.util.Collection;

import org.jetbrains.annotations.ApiStatus;
import org.quiltmc.loader.api.ModMetadata;

import net.fabricmc.api.EnvType;

/**
 * Holder interface for all fields that should be moved to a loader plugin.
 *
 * @deprecated subject to removal after plugins are implemented.
 */
@Deprecated
@ApiStatus.ScheduledForRemoval
public interface ModMetadataToBeMovedToPlugins extends ModMetadata {
	Collection<MixinEntry> mixins();

	Collection<String> accessWideners();

	MinecraftEnvironmentSelector environment();

	public static final class MixinEntry {
		public final String path;
		public final MinecraftEnvironmentSelector environment;

		public MixinEntry(String path, MinecraftEnvironmentSelector environment) {
			this.path = path;
			this.environment = environment;
		}
	}
}
