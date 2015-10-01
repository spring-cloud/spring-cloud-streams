/*
 * Copyright 2015 the original author or authors.
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
package org.springframework.cloud.stream.binding;

import java.util.ArrayList;
import java.util.Collection;

import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.config.BindingProperties;
import org.springframework.cloud.stream.config.ChannelBindingServiceProperties;
import org.springframework.cloud.stream.converter.AbstractFromMessageConverter;
import org.springframework.cloud.stream.converter.ByteArrayToStringMessageConverter;
import org.springframework.cloud.stream.converter.CompositeMessageConverterFactory;
import org.springframework.cloud.stream.converter.JavaToSerializedMessageConverter;
import org.springframework.cloud.stream.converter.JsonToPojoMessageConverter;
import org.springframework.cloud.stream.converter.JsonToTupleMessageConverter;
import org.springframework.cloud.stream.converter.MessageConverterUtils;
import org.springframework.cloud.stream.converter.PojoToJsonMessageConverter;
import org.springframework.cloud.stream.converter.PojoToStringMessageConverter;
import org.springframework.cloud.stream.converter.SerializedToJavaMessageConverter;
import org.springframework.cloud.stream.converter.StringToByteArrayMessageConverter;
import org.springframework.cloud.stream.converter.TupleToJsonMessageConverter;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.integration.channel.AbstractMessageChannel;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.util.Assert;
import org.springframework.util.MimeType;
import org.springframework.util.StringUtils;

/**
 * Class that configures message converters per channel binding.
 *
 * @author Ilayaperumal Gopinathan
 */
public class MessageConvertersConfigurer implements ApplicationContextAware, SmartInitializingSingleton {

	private static final String CUSTOM_MSG_CONVERTERS_BEAN_NAME = "customMessageConverters";

	@Autowired
	private ChannelBindingServiceProperties channelBindingServiceProperties;

	private ApplicationContext applicationContext;

	private CompositeMessageConverterFactory messageConverterFactory;

	@Override
	public void afterSingletonsInstantiated() {
		Collection<AbstractFromMessageConverter> messageConverters = new ArrayList<AbstractFromMessageConverter>();
		if (applicationContext.containsBean(CUSTOM_MSG_CONVERTERS_BEAN_NAME)) {
			messageConverters.addAll(
					(Collection<AbstractFromMessageConverter>) applicationContext.getBean(CUSTOM_MSG_CONVERTERS_BEAN_NAME));
		}
		messageConverters.add(new JsonToTupleMessageConverter());
		messageConverters.add(new TupleToJsonMessageConverter());
		messageConverters.add(new JsonToPojoMessageConverter());
		messageConverters.add(new PojoToJsonMessageConverter());
		messageConverters.add(new ByteArrayToStringMessageConverter());
		messageConverters.add(new StringToByteArrayMessageConverter());
		messageConverters.add(new PojoToStringMessageConverter());
		messageConverters.add(new JavaToSerializedMessageConverter());
		messageConverters.add(new SerializedToJavaMessageConverter());
		this.messageConverterFactory = new CompositeMessageConverterFactory(messageConverters);
	}

	public void setApplicationContext(ApplicationContext applicationContext) {
		this.applicationContext = applicationContext;
	}

	/**
	 * Setup data-type and message converters for the given message channel.
	 *
	 * @param channel message channel to set the data-type and message converters
	 * @param channelName the channel name
	 */
	public void configureMessageConverters(Object channel, String channelName) {
		AbstractMessageChannel messageChannel = null;
		try {
			messageChannel = getMessageChannel(channel);
		}
		catch (Exception e) {
			throw new IllegalStateException("Could not get the message channel to configure message converters" + e);
		}
		BindingProperties bindingProperties = channelBindingServiceProperties.getBindings().get(channelName);
		if (bindingProperties != null) {
			String contentType = bindingProperties.getContentType();
			if (StringUtils.hasText(contentType)) {
				MimeType mimeType = MessageConverterUtils.getMimeType(contentType);
				MessageConverter messageConverter = messageConverterFactory.newInstance(mimeType);
				Class<?> dataType = MessageConverterUtils.getJavaTypeForContentType(mimeType,
						Thread.currentThread().getContextClassLoader());
				messageChannel.setDatatypes(dataType);
				messageChannel.setMessageConverter(messageConverter);
			}
		}
	}

	private AbstractMessageChannel getMessageChannel(Object channel) throws Exception {
		if (AopUtils.isJdkDynamicProxy(channel)) {
			return (AbstractMessageChannel) (((Advised) channel).getTargetSource().getTarget());
		}
		Assert.isAssignable(AbstractMessageChannel.class, channel.getClass());
		return (AbstractMessageChannel) channel;
	}
}
