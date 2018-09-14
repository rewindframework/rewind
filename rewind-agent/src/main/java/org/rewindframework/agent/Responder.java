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

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import org.apache.commons.lang3.SerializationUtils;

import java.io.IOException;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.util.concurrent.TimeoutException;

public class Responder {
    private final String id;
    private final Connection connection;

    public Responder(String id, Connection connection) {
        this.id = id;
        this.connection = connection;
    }

    public void notify(Serializable message) {
        try (Channel channel = connection.createChannel()) {
            channel.exchangeDeclare(id, "fanout");
            channel.basicPublish(id, "", null, SerializationUtils.serialize(message));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (TimeoutException e) {
            throw new RuntimeException(e);
        }
    }
}
