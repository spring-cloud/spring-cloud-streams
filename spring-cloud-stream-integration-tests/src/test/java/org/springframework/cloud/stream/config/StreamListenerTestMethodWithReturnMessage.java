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
package org.springframework.cloud.stream.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.annotation.Input;
import org.springframework.cloud.stream.annotation.Output;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.cloud.stream.messaging.Processor;
import org.springframework.cloud.stream.test.binder.MessageCollector;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.SendTo;

/**
 * @author Marius Bogoevici
 * @author Ilayaperumal Gopinathan
 */
@RunWith(Parameterized.class)
public class StreamListenerTestMethodWithReturnMessage {

	private Class<?> configClass;

	public StreamListenerTestMethodWithReturnMessage(Class<?> configClass) {
		this.configClass = configClass;
	}

	@Parameterized.Parameters
	public static Collection InputConfigs() {
		return Arrays.asList(new Class[] {TestPojoWithMessageReturn1.class, TestPojoWithMessageReturn2.class,
				TestPojoWithMessageReturn3.class, TestPojoWithMessageReturn4.class});
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testReturnMessage() throws Exception {
		ConfigurableApplicationContext context = SpringApplication
				.run(this.configClass, "--server.port=0");
		MessageCollector collector = context.getBean(MessageCollector.class);
		Processor processor = context.getBean(Processor.class);
		String id = UUID.randomUUID().toString();
		processor.input()
				.send(MessageBuilder.withPayload("{\"bar\":\"barbar" + id + "\"}")
						.setHeader("contentType", "application/json").build());
		TestPojoWithMessageReturn testPojoWithMessageReturn = context
				.getBean(TestPojoWithMessageReturn.class);
		assertThat(testPojoWithMessageReturn.receivedPojos).hasSize(1);
		assertThat(testPojoWithMessageReturn.receivedPojos.get(0)).hasFieldOrPropertyWithValue("bar", "barbar" + id);
		Message<BazPojo> message = (Message<BazPojo>) collector
				.forChannel(processor.output()).poll(1, TimeUnit.SECONDS);
		assertThat(message).isNotNull();
		assertThat(message.getPayload().getQux()).isEqualTo("barbar" + id);
		context.close();
	}

	@EnableBinding(Processor.class)
	@EnableAutoConfiguration
	public static class TestPojoWithMessageReturn1 extends TestPojoWithMessageReturn {

		@StreamListener(Processor.INPUT)
		@SendTo(Processor.OUTPUT)
		public Message<?> receive(FooPojo fooPojo) {
			this.receivedPojos.add(fooPojo);
			BazPojo bazPojo = new BazPojo();
			bazPojo.setQux(fooPojo.getBar());
			return MessageBuilder.withPayload(bazPojo).setHeader("foo", "bar").build();
		}
	}

	@EnableBinding(Processor.class)
	@EnableAutoConfiguration
	public static class TestPojoWithMessageReturn2 extends TestPojoWithMessageReturn {

		@StreamListener(Processor.INPUT)
		@Output(Processor.OUTPUT)
		public Message<?> receive(FooPojo fooPojo) {
			this.receivedPojos.add(fooPojo);
			BazPojo bazPojo = new BazPojo();
			bazPojo.setQux(fooPojo.getBar());
			return MessageBuilder.withPayload(bazPojo).setHeader("foo", "bar").build();
		}
	}

	@EnableBinding(Processor.class)
	@EnableAutoConfiguration
	public static class TestPojoWithMessageReturn3 extends TestPojoWithMessageReturn {

		@StreamListener
		@Output(Processor.OUTPUT)
		public Message<?> receive(@Input(Processor.INPUT) FooPojo fooPojo) {
			this.receivedPojos.add(fooPojo);
			BazPojo bazPojo = new BazPojo();
			bazPojo.setQux(fooPojo.getBar());
			return MessageBuilder.withPayload(bazPojo).setHeader("foo", "bar").build();
		}
	}

	@EnableBinding(Processor.class)
	@EnableAutoConfiguration
	public static class TestPojoWithMessageReturn4 extends TestPojoWithMessageReturn {

		@StreamListener
		@SendTo(Processor.OUTPUT)
		public Message<?> receive(@Input(Processor.INPUT) FooPojo fooPojo) {
			this.receivedPojos.add(fooPojo);
			BazPojo bazPojo = new BazPojo();
			bazPojo.setQux(fooPojo.getBar());
			return MessageBuilder.withPayload(bazPojo).setHeader("foo", "bar").build();
		}
	}

	public static class TestPojoWithMessageReturn {

		List<FooPojo> receivedPojos = new ArrayList<>();
	}

	public static class FooPojo {

		private String bar;

		public String getBar() {
			return this.bar;
		}

		public void setBar(String bar) {
			this.bar = bar;
		}
	}

	public static class BazPojo {

		private String qux;

		public String getQux() {
			return this.qux;
		}

		public void setQux(String qux) {
			this.qux = qux;
		}
	}

}
