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

package org.quiltmc.loader.impl.plugin.base;

import java.io.IOException;
import java.nio.file.Path;

import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.api.plugin.QuiltPluginContext;
import org.quiltmc.loader.api.plugin.gui.PluginGuiIcon;
import org.quiltmc.loader.api.plugin.solver.ModLoadOption;
import org.quiltmc.loader.impl.metadata.qmj.InternalModMetadata;
import org.quiltmc.loader.impl.util.HashUtil;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public abstract class InternalModOptionBase extends ModLoadOption {

	protected final QuiltPluginContext pluginContext;
	protected final InternalModMetadata metadata;
	protected final Path from, resourceRoot;
	protected final boolean mandatory;
	protected final boolean requiresRemap;
	protected final PluginGuiIcon fileIcon;

	byte[] hash;

	public InternalModOptionBase(QuiltPluginContext pluginContext, InternalModMetadata meta, Path from,
		PluginGuiIcon fileIcon, Path resourceRoot, boolean mandatory, boolean requiresRemap) {

		this.pluginContext = pluginContext;
		this.metadata = meta;
		this.from = from;
		this.fileIcon = fileIcon;
		this.resourceRoot = resourceRoot;
		this.mandatory = mandatory;
		this.requiresRemap = requiresRemap;
	}

	@Override
	public InternalModMetadata metadata() {
		return metadata;
	}

	@Override
	public Path from() {
		return from;
	}

	@Override
	public PluginGuiIcon modFileIcon() {
		return fileIcon;
	}

	@Override
	public Path resourceRoot() {
		return resourceRoot;
	}

	@Override
	public boolean isMandatory() {
		return mandatory;
	}

	@Override
	public String toString() {
		return "{" + getClass().getName() + " '" + metadata.id() + "' from " //
			+ pluginContext.manager().describePath(from) + "}";
	}

	@Override
	public QuiltPluginContext loader() {
		return pluginContext;
	}

	@Override
	public String shortString() {
		return toString();
	}

	@Override
	public String getSpecificInfo() {
		return toString();
	}

	@Override
	public @Nullable String namespaceMappingFrom() {
		return requiresRemap ? metadata.intermediateMappings() : null;
	}

	@Override
	public boolean needsChasmTransforming() {
		return true;
	}

	@Override
	public byte[] computeOriginHash() throws IOException {
		if (hash == null) {
			hash = HashUtil.computeHash(from);
		}
		return hash;
	}
}
