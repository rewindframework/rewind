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

package org.rewindframework.testing.messages;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.List;

public class TestingRequest implements Serializable {
    public byte[] payload;
    public String id;
    public List<String> dependencies;
    public List<Repository> repositories;

    public static TestingRequest from(File testJar) {
        try {
            TestingRequest result = new TestingRequest();
            result.payload = FileUtils.readFileToByteArray(testJar);
            return result;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public TestingRequest withId(String id) {
        this.id = id;
        return this;
    }

    public TestingRequest withDependencies(List<String> dependencies) {
        this.dependencies = dependencies;
        return this;
    }

    public TestingRequest withRepositories(List<Repository> repositories) {
        this.repositories = repositories;
        return this;
    }

    public static class Repository implements Serializable {
        private final String name;
        private final String type;
        private URI url;

        public Repository(String name, String type) {
            this.name = name;
            this.type = type;
        }

        public static Repository maven(String name) {
            return new Repository(name, "maven");
        }

        public String getName() {
            return name;
        }

        public String getType() {
            return type;
        }

        public URI getUrl() {
            return url;
        }

        public void setUrl(URI url) {
            this.url = url;
        }
    }
}
