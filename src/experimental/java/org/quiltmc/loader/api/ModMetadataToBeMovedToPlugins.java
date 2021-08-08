package org.quiltmc.loader.api;

import java.util.Collection;

import net.fabricmc.loader.api.metadata.ModEnvironment;
import org.jetbrains.annotations.ApiStatus;

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

	ModEnvironment environment();

	public static final class MixinEntry {
		public final String path;
		public final ModEnvironment environment;

		public MixinEntry(String path, ModEnvironment environment) {
			this.path = path;
			this.environment = environment;
		}
	}
}
