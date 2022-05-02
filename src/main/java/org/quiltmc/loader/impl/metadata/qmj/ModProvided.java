/*
 * Copyright 2016 FabricMC
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

package org.quiltmc.loader.impl.metadata.qmj;

import org.quiltmc.loader.api.Version;

import java.util.Objects;

public class ModProvided {
	public final String group;
	public final String id;
	public final Version version;

	public ModProvided(String group, String id, Version version) {
		this.group = group;
		this.id = id;
		this.version = version;
	}

	@Override
	public String toString() {
		return "ModProvided { " + group + ":" + id + " v " + version + " }";
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof ModProvided)) return false;
		ModProvided that = (ModProvided) o;
		return Objects.equals(group, that.group) && Objects.equals(id, that.id) && Objects.equals(version, that.version);
	}

	@Override
	public int hashCode() {
		return Objects.hash(group, id, version);
	}
}
