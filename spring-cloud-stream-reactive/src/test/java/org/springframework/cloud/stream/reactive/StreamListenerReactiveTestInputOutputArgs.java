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

package org.springframework.cloud.stream.reactive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.util.Arrays;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import reactor.core.publisher.Flux;
import rx.Observable;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.annotation.Input;
import org.springframework.cloud.stream.annotation.Output;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.cloud.stream.messaging.Processor;
import org.springframework.cloud.stream.test.binder.MessageCollector;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

/**
 * @author Marius Bogoevici
 * @author Ilayaperumal Gopinathan
 */
@RunWith(Parameterized.class)
public class StreamListenerReactiveTestInputOutputArgs {

	private Class<?> configClass;

	public StreamListenerReactiveTestInputOutputArgs(Class<?> configClass) {
		this.configClass = configClass;
	}

	@Parameterized.Parameters
	public static Collection InputConfigs() {
		return Arrays.asList(new Class[] {ReactorTestInputOutputArgs1.class, RxJava1TestInputOutputArgs1.class});
	}

	@Test
	public void testInputOutputArgs() throws Exception {
		ConfigurableApplicationContext context = SpringApplication.run(this.configClass, "--server.port=0");
		sendMessageAndValidate(context);
		context.close();
	}

	@Test
	public void testIncorrectUsage1() {
		try {
			SpringApplication.run(ReactorTestInputOutputArgs2.class, "--server.port=0");
			fail("IllegalArgumentException should have been thrown");
		}
		catch (Exception e) {
			assertThat(e.getMessage()).contains("@Input annotation should be specified as method parameter instead of " +
					"StreamListener value when specifying @Output/@SendTo annotation");
		}
	}

	@Test
	public void testIncorrectUsage2() {
		try {
			SpringApplication.run(RxJava1TestInputOutputArgs2.class, "--server.port=0");
			fail("IllegalArgumentException should have been thrown");
		}
		catch (Exception e) {
			assertThat(e.getMessage()).contains("@Input annotation should be specified as method parameter instead of " +
					"StreamListener value when specifying @Output/@SendTo annotation");
		}
	}

	private static void sendMessageAndValidate(ConfigurableApplicationContext context) throws InterruptedException {
		@SuppressWarnings("unchecked")
		Processor processor = context.getBean(Processor.class);
		String sentPayload = "hello " + UUID.randomUUID().toString();
		processor.input().send(MessageBuilder.withPayload(sentPayload).setHeader("contentType", "text/plain").build());
		MessageCollector messageCollector = context.getBean(MessageCollector.class);
		Message<?> result = messageCollector.forChannel(processor.output()).poll(1000, TimeUnit.MILLISECONDS);
		assertThat(result).isNotNull();
		assertThat(result.getPayload()).isEqualTo(sentPayload.toUpperCase());
	}

	@EnableBinding(Processor.class)
	@EnableAutoConfiguration
	public static class ReactorTestInputOutputArgs1 {

		@StreamListener
		public void receive(@Input(Processor.INPUT) Flux<String> input, @Output(Processor.OUTPUT) FluxSender output) {
			output.send(input.map(m -> m.toUpperCase()));
		}
	}

	@EnableBinding(Processor.class)
	@EnableAutoConfiguration
	public static class ReactorTestInputOutputArgs2 {

		@StreamListener(Processor.INPUT)
		public void receive(Flux<String> input, @Output(Processor.OUTPUT) FluxSender output) {
			output.send(input.map(m -> m.toUpperCase()));
		}
	}

	@EnableBinding(Processor.class)
	@EnableAutoConfiguration
	public static class RxJava1TestInputOutputArgs1 {

		@StreamListener
		public void receive(@Input(Processor.INPUT) Observable<String> input, @Output(Processor.OUTPUT) ObservableSender output) {
			output.send(input.map(m -> m.toUpperCase()));
		}
	}

	@EnableBinding(Processor.class)
	@EnableAutoConfiguration
	public static class RxJava1TestInputOutputArgs2 {

		@StreamListener(Processor.INPUT)
		public void receive(Observable<String> input, @Output(Processor.OUTPUT) ObservableSender output) {
			output.send(input.map(m -> m.toUpperCase()));
		}
	}
}
