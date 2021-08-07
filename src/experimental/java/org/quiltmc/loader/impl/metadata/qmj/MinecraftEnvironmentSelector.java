package org.quiltmc.loader.impl.metadata.qmj;

import net.fabricmc.api.EnvType;

public enum MinecraftEnvironmentSelector {
	CLIENT_ONLY(true, false),
	SERVER_ONLY(false, true),
	// Data_generator?
	EITHER(true, true);

	private boolean client, server;

	private MinecraftEnvironmentSelector(boolean client, boolean server) {
		this.client = client;
		this.server = server;
	}

	public boolean loadsIn(EnvType type) {
		switch (type) {
			case CLIENT:
				return client;
			case SERVER:
				return server;
			default:
				throw new IllegalArgumentException("Unknown EnvType " + type);
		}
	}
}
