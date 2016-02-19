/*
 * Copyright 2015-2016 the original author or authors.
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

package org.springframework.cloud.stream.binder.kafka.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.stream.binder.Binder;
import org.springframework.cloud.stream.binder.kafka.KafkaMessageChannelBinder;
import org.springframework.cloud.stream.config.codec.kryo.KryoCodecAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;
import org.springframework.integration.codec.Codec;
import org.springframework.integration.kafka.support.LoggingProducerListener;
import org.springframework.integration.kafka.support.ProducerListener;
import org.springframework.integration.kafka.support.ZookeeperConnect;
import org.springframework.util.ObjectUtils;

/**
 * @author David Turanski
 * @author Marius Bogoevici
 * @author Soby Chacko
 * @author Mark Fisher
 * @author Ilayaperumal Gopinathan
 */
@Configuration
@ConditionalOnMissingBean(Binder.class)
@Import({KryoCodecAutoConfiguration.class, PropertyPlaceholderAutoConfiguration.class})
@EnableConfigurationProperties({KafkaBinderConfigurationProperties.class, KafkaBinderDefaultProperties.class})
@PropertySource("classpath:/META-INF/spring-cloud-stream/kafka-binder.properties")
public class KafkaServiceAutoConfiguration {

	@Autowired
	private Codec codec;

	@Autowired
	private KafkaBinderDefaultProperties kafkaBinderDefaultProperties;

	@Autowired
	private KafkaBinderConfigurationProperties kafkaBinderConfigurationProperties;

	@Autowired
	private ProducerListener producerListener;

	@Bean
	ZookeeperConnect zookeeperConnect() {
		ZookeeperConnect zookeeperConnect = new ZookeeperConnect();
		zookeeperConnect.setZkConnect(kafkaBinderConfigurationProperties.getZkConnectionString());
		return zookeeperConnect;
	}

	@Bean
	KafkaMessageChannelBinder kafkaMessageChannelBinder() {
		String[] headers = kafkaBinderConfigurationProperties.getHeaders();
		String kafkaConnectionString = kafkaBinderConfigurationProperties.getKafkaConnectionString();
		String zkConnectionString = kafkaBinderConfigurationProperties.getZkConnectionString();
		KafkaMessageChannelBinder kafkaMessageChannelBinder = ObjectUtils.isEmpty(headers) ?
				new KafkaMessageChannelBinder(zookeeperConnect(), kafkaConnectionString, zkConnectionString)
				: new KafkaMessageChannelBinder(zookeeperConnect(), kafkaConnectionString, zkConnectionString,
						headers);
		kafkaMessageChannelBinder.setCodec(codec);
		kafkaMessageChannelBinder.setMode(kafkaBinderConfigurationProperties.getMode());
		kafkaMessageChannelBinder.setOffsetUpdateTimeWindow(kafkaBinderConfigurationProperties.getOffsetUpdateTimeWindow());
		kafkaMessageChannelBinder.setOffsetUpdateCount(kafkaBinderConfigurationProperties.getOffsetUpdateCount());
		kafkaMessageChannelBinder.setOffsetUpdateShutdownTimeout(kafkaBinderConfigurationProperties.getOffsetUpdateShutdownTimeout());

		kafkaMessageChannelBinder.setResetOffsets(kafkaBinderConfigurationProperties.isResetOffsets());
		kafkaMessageChannelBinder.setStartOffset(kafkaBinderConfigurationProperties.getStartOffset());

		kafkaMessageChannelBinder.setSyncProducer(kafkaBinderConfigurationProperties.isSyncProducer());

		kafkaMessageChannelBinder.setDefaultAutoCommitEnabled(kafkaBinderDefaultProperties.isAutoCommitEnabled());
		kafkaMessageChannelBinder.setDefaultBatchSize(kafkaBinderDefaultProperties.getBatchSize());
		kafkaMessageChannelBinder.setDefaultBatchTimeout(kafkaBinderDefaultProperties.getBatchTimeout());
		kafkaMessageChannelBinder.setDefaultCompressionCodec(kafkaBinderDefaultProperties.getCompressionCodec());
		kafkaMessageChannelBinder.setDefaultConcurrency(kafkaBinderDefaultProperties.getConcurrency());
		kafkaMessageChannelBinder.setDefaultFetchSize(kafkaBinderDefaultProperties.getFetchSize());
		kafkaMessageChannelBinder.setDefaultMinPartitionCount(kafkaBinderDefaultProperties.getMinPartitionCount());
		kafkaMessageChannelBinder.setDefaultQueueSize(kafkaBinderDefaultProperties.getQueueSize());
		kafkaMessageChannelBinder.setDefaultReplicationFactor(kafkaBinderDefaultProperties.getReplicationFactor());
		kafkaMessageChannelBinder.setDefaultRequiredAcks(kafkaBinderDefaultProperties.getRequiredAcks());

		kafkaMessageChannelBinder.setProducerListener(producerListener);
		return kafkaMessageChannelBinder;
	}

	@Bean
	@ConditionalOnMissingBean(ProducerListener.class)
	ProducerListener producerListener() {
		return new LoggingProducerListener();
	}
}
