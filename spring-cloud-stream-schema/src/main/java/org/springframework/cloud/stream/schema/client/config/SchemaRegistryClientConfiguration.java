/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.stream.schema.client.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.stream.schema.client.CachingRegistryClient;
import org.springframework.cloud.stream.schema.client.DefaultSchemaRegistryClient;
import org.springframework.cloud.stream.schema.client.SchemaRegistryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * @author Marius Bogoevici
 * @author Vinicius Carvalho
 */
@Configuration
@EnableConfigurationProperties(SchemaRegistryClientProperties.class)
@EnableCaching
public class SchemaRegistryClientConfiguration {

	@Autowired
	private SchemaRegistryClientProperties schemaRegistryClientProperties;

	@Bean
	@ConditionalOnMissingBean(SchemaRegistryClient.class)
	public SchemaRegistryClient schemaRegistryClient(SchemaRegistryClientProperties schemaRegistryClientProperties) {
		return new CachingRegistryClient(createDefaultSchemaClient());
	}

	private DefaultSchemaRegistryClient createDefaultSchemaClient(){
		DefaultSchemaRegistryClient defaultSchemaRegistryClient = new DefaultSchemaRegistryClient();
		if (StringUtils.hasText(schemaRegistryClientProperties.getEndpoint())) {
			defaultSchemaRegistryClient.setEndpoint(schemaRegistryClientProperties.getEndpoint());
		}
		return defaultSchemaRegistryClient;
	}

}
