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

package org.quiltmc.loader.api.plugin;

import java.util.Map;

import org.quiltmc.loader.api.LoaderValue;
import org.quiltmc.loader.impl.metadata.qmj.JsonLoaderFactoryImpl;

/** Location-less factories */
public interface LoaderValueFactory {

	public static LoaderValueFactory getFactory() {
		return JsonLoaderFactoryImpl.INSTANCE;
	}

	LoaderValue nul();

	LoaderValue bool(boolean value);

	LoaderValue number(Number value);

	LoaderValue string(String value);

	LoaderValue array(LoaderValue[] values);

	LoaderValue object(Map<String, LoaderValue> map);
}
