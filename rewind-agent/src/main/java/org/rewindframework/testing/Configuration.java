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

import org.apache.commons.cli.*;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class Configuration {
    private final CommandLine commandLine;

    private Configuration(CommandLine commandLine) {
        this.commandLine = commandLine;
    }

    public String getHost() {
        return commandLine.getOptionValue("host");
    }

    public String getQueueName() {
        return commandLine.getOptionValue("queue");
    }

    public File getWorkerDirectory() {
        return new File(commandLine.getOptionValue("workerDir"));
    }

    public Map<String, String> getTestProperties() {
        Map<String, String> result = new HashMap<>();
        for (String testProperty : commandLine.getOptionValues("testProperty")) {
            String[] tokens = StringUtils.split(testProperty, '=');
            assert tokens.length == 1;
            String key = tokens[0];
            String value = Arrays.stream(tokens).skip(1).reduce(String::concat).orElse(null);
            result.put(key, value);
        }

        return result;
    }

    public static Configuration from(String[] args) throws ParseException {
        Options options = new Options();
        options.addOption("host", "host", true, "Host to connect to");
        options.addOption("queue", "queue", true, "Queue to consume from");
        options.addOption("workerDir", "worker-dir", true, "Worker directory");
        options.addOption("testProperty", "test-prop",true, "Test properties");

        CommandLineParser parser = new DefaultParser();
        return new Configuration(parser.parse(options, args));
    }
}
