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

package org.rewindframework.agent;

import com.rabbitmq.client.*;
import org.rewindframework.messages.TestingAbort;
import org.rewindframework.messages.TestingRequest;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Map;

public class RequestConsumer extends DefaultConsumer {
    private static final Logger LOGGER = LogManager.getLogger(RequestConsumer.class);
    private final Connection connection;
    private final File workerDirectory;
    private final Map<String, String> testProperties;

    RequestConsumer(Channel channel, Connection connection, File workerDirectory, Map<String, String> testProperties) {
        super(channel);
        this.connection = connection;
        this.workerDirectory = workerDirectory;
        this.testProperties = testProperties;
    }

    @Override
    public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
        LOGGER.debug(String.format("Delivery received with consumer tag '%s' and a payload of %d bytes", consumerTag, body.length));
        try {
            // 1) Deserialize the message
            TestingRequest request = SerializationUtils.deserialize(body);

            LOGGER.debug(String.format("Testing request deserialized: test JAR is %d bytes, id is '%s', repository count is %d, and dependency count is %d", request.payload.length, request.id, request.repositories.size(), request.dependencies.size()));

            // Responder
            Responder responder = new Responder(request.id, connection);

            // Connect the logger to this running test responder
            try {
                AgentLogAppender.responder = responder;

                LOGGER.info(String.format("Request '%s' received", request.id));
                try {
                    // Process request
                    RequestFactory.newRequest(request.id, responder).useWorkerDirectory(new File(workerDirectory, "rewind-test")).useCacheDirectory(new File(workerDirectory, "cache")).withTestProperties(testProperties).execute(request.payload, request.dependencies, request.repositories).publishResults();

                    LOGGER.debug("Delivery processed successfully");
                } catch (InterruptedException e) {
                    LOGGER.error("Interrupt exception", e);
                } catch (URISyntaxException e) {
                    LOGGER.error("URI syntax exception", e);
                }
            } catch (Throwable e) {
                responder.notify(new TestingAbort().withId(request.id));
                throw e;
            } finally {
                AgentLogAppender.responder = null;
            }
        } catch (Throwable e) {
            LOGGER.error("Exception during setting appender", e);
            throw e;
        }
    }
}
