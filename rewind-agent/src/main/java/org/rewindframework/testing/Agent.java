/*
 * Copyright 2018 the original author or authors.
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

package org.rewindframework.testing;

import com.rabbitmq.client.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class Agent {
    private static Logger LOGGER = LogManager.getLogger(Agent.class);

    private final Configuration configuration;

    public Agent(Configuration configuration) {
        this.configuration = configuration;
    }

    private static Connection newConnection(String host) throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(host);

        return factory.newConnection();
    }

    private static Channel newChannelToQueue(Connection connection, String queueName) throws IOException {
        Channel channel = connection.createChannel();
        channel.queueDeclare(queueName, false, false, false, null);

        return channel;
    }

    public void start() throws IOException, TimeoutException {
        Connection connection = newConnection(configuration.getHost());
        Channel channel = newChannelToQueue(connection, configuration.getQueueName());
        LOGGER.info("Waiting for messages. To exit press CTRL+C");

        Consumer consumer = new RequestConsumer(channel, connection, configuration.getWorkerDirectory(), configuration.getTestProperties());
        channel.basicConsume(configuration.getQueueName(), true, consumer);
    }


}
