/*
 * Copyright 2017 the original author or authors.
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

package org.springframework.cloud.stream.binder.integration;

import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessagingException;

/**
 * Implementation of binder endpoint that represents the target destination
 * (e.g., destination which receives messages sent to Processor.OUTPUT)
 * <br>
 * You can interact with it by calling {@link #receive()} operation.
 *
 * @author Oleg Zhurakousky
 *
 */
public class TargetDestination extends AbstractDestination {

	private Queue<Message<?>> messages;

	/**
	 * Allows to receive {@link Message}s that have been received by a {@link TargetDestination}.
	 */
	public Message<?> receive() {
		return messages.poll();
	}

	@Override
	void afterChannelIsSet() {
		this.messages = new ArrayBlockingQueue<>(1000);
		this.getChannel().subscribe(new MessageHandler() {

			@Override
			public void handleMessage(Message<?> message) throws MessagingException {
				messages.offer(message);
			}
		});
	}
}
