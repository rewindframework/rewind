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

import org.rewindframework.testing.messages.TestingRequest;
import org.rewindframework.testing.messages.TestingResponse;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class RequestFactory {
    private static final Logger LOGGER = LogManager.getLogger(RequestFactory.class);

    public static RequestProcessor newRequest(String id, Responder responder) {
        LOGGER.info(String.format("Received new request id '%s'", id));
        return new RequestProcessor(id, responder);
    }

    public static class RequestProcessor {
        private static final Logger LOGGER = LogManager.getLogger(RequestProcessor.class);
        private static final String BINARY_RESULTS_DIRECTORY = "build/test-results/test/binary";

        private final String id;
        private final Responder responder;
        private final Map<String, String> testProperties = new HashMap<>();
        private File workerDirectory;
        private File cacheDirectory;
        private Optional<Boolean> result = Optional.empty();

        private RequestProcessor(String id, Responder responder) {
            this.id = id;
            this.responder = responder;
        }

        public RequestProcessor useWorkerDirectory(File workerDirectory) {
            if (workerDirectory.exists()) {
                try {
                    FileUtils.deleteDirectory(workerDirectory);
                } catch (IOException ex) {
                    LOGGER.warn(String.format("Unable to clean worker directory '%s'", workerDirectory.getAbsolutePath()), ex);
                }
            }
            if (!workerDirectory.exists()) {
                try {
                    FileUtils.forceMkdir(workerDirectory);
                } catch (IOException ex) {
                    throw new UncheckedIOException(String.format("Unable to create worker directory '%s'", workerDirectory.getAbsolutePath()), ex);
                }
            }

            LOGGER.info(String.format("Using worker directory '%s'", workerDirectory.getAbsolutePath()));
            this.workerDirectory = workerDirectory;

            return this;
        }

        public RequestProcessor execute(byte[] payload, List<String> notations, List<TestingRequest.Repository> repositories) throws InterruptedException, IOException, URISyntaxException {
            // 1) Wrap gradle
            File gradleWrapperDirectory = seedGradleWrapper(cacheDirectory, "4.9");

            // 2) Drop test app to disk
            File testJar = new File(workerDirectory, "test.jar");
            LOGGER.debug(String.format("Dropping testing app at '%s'", testJar.getAbsolutePath()));
            FileUtils.writeByteArrayToFile(testJar, payload);

            // 3) Drop build script to disk
            // TODO: move file into embedded in jar and we just drop it
            // TODO: Use class in here to publish heart beat to the listening build
            File buildFile = new File(workerDirectory, "build.gradle");

            LOGGER.debug(String.format("Dropping build.gradle file at '%s'", buildFile.getAbsolutePath()));
            File thisJar = new File(RequestFactory.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            try (PrintWriter out = new PrintWriter(new FileOutputStream(buildFile))) {
                out.println("buildscript {");
                out.println("    dependencies {");
                out.println("        classpath files('" + thisJar.getAbsolutePath() + "')");
                out.println("    }");
                out.println("}");
                out.println("configurations {");
                out.println("    testImplementation");
                out.println("}");
                out.println("dependencies {");
                for (String notation : notations) {
                    out.println("    testImplementation '" + notation + "'");
                }
                out.println("    testImplementation files('" + testJar.getAbsolutePath() + "')");
                out.println("}");
                out.println("repositories {");
                for (TestingRequest.Repository repository : repositories) {
                    out.println("    " + repository.getType() + " {");
                    out.println("        name = '" + repository.getName() + "'");
                    out.println("        url = '" + repository.getUrl().toString() + "'");
                    out.println("    }");
                }
                out.println("}");
                out.println("task test(type: Test) {");
                out.println("    classpath = configurations.testImplementation");
                out.println("    testClassesDirs = zipTree('" + testJar.getAbsolutePath() + "').matching { PatternFilterable p -> p.include('io/boogie/**') }");
                out.println("    binResultsDir = file(\"" + BINARY_RESULTS_DIRECTORY + "\")");
                out.println("    reports.junitXml.enabled = false");
                out.println("    reports.junitXml.destination = file(\"$buildDir/reports/tests/test\")");
                out.println("    reports.html.enabled = false");
                out.println("    reports.html.destination = file(\"$buildDir/reports/tests/test\")");
                for (Map.Entry<String, String> testProperty : testProperties.entrySet()) {
                    out.println("    systemProperty('" + testProperty.getKey() + "', " + (testProperty.getValue() == null ? "null" : "'" + testProperty.getValue() + "'") + ")");
                }
                out.println("}");
            }

            // 4) exec
            // TODO: Capture output and errorput and log it
            // TODO: See if you can fetch Gradle instead of relying on preinstalled Gradle
            LOGGER.debug(String.format("Executing './gradlew test' in '%s'", workerDirectory.getAbsolutePath()));
            Process process = new ProcessBuilder().command("sh", new File(gradleWrapperDirectory, "gradlew").getAbsolutePath(), "test").directory(workerDirectory).redirectErrorStream(true).start();
            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            Thread outStreamThread = new Thread(() -> {
                try {
                    IOUtils.copy(process.getInputStream(), stdout);
                } catch (IOException e) {
                    LOGGER.fatal("Unable to process stdout", e);
                }
            });
            outStreamThread.start();

            // TODO: Have timeout
            LOGGER.debug("Waiting Gradle...");
            process.waitFor();
            outStreamThread.join();
            result = Optional.of(process.exitValue() == 0);
            LOGGER.debug(stdout.toString());

            return this;
        }

        private static File seedGradleWrapper(File cacheDirectory, String gradleVersion) throws IOException, InterruptedException {
            File gradleDirectory = new File(cacheDirectory, gradleVersion);
            if (new File(gradleDirectory, ".provisioned").exists()) {
                LOGGER.debug(String.format("Gradle version '%s' already seeded at '%s'", gradleVersion, gradleDirectory.getAbsolutePath()));
                return gradleDirectory;
            }

            gradleDirectory.mkdirs();

            LOGGER.debug(String.format("Executing './gradle wrapper --gradle-version %s' in '%s'", gradleVersion, gradleDirectory.getAbsolutePath()));
            Process process = new ProcessBuilder().command("gradle", "wrapper", "--gradle-version", gradleVersion).directory(gradleDirectory).redirectErrorStream(true).start();
            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            Thread outStreamThread = new Thread(() -> {
                try {
                    IOUtils.copy(process.getInputStream(), stdout);
                } catch (IOException e) {
                    LOGGER.fatal("Unable to process stdout", e);
                }
            });
            outStreamThread.start();

            // TODO: Have timeout
            LOGGER.debug("Waiting for wrapper generation...");
            process.waitFor();
            outStreamThread.join();
            LOGGER.debug(stdout.toString());

            new File(gradleDirectory, ".provisioned").createNewFile();
            return gradleDirectory;
        }

        public RequestProcessor publishResults() {
            // 5) ... publish result binary
            assert result.isPresent();
            responder.notify(TestingResponse.from(new File(workerDirectory, BINARY_RESULTS_DIRECTORY)).withResult(result.get()));
            return this;
        }

        public RequestProcessor useCacheDirectory(File cacheDirectory) {
            if (!cacheDirectory.exists()) {
                try {
                    FileUtils.forceMkdir(cacheDirectory);
                } catch (IOException ex) {
                    throw new UncheckedIOException(String.format("Unable to create cache directory '%s'", cacheDirectory.getAbsolutePath()), ex);
                }
            }

            this.cacheDirectory = cacheDirectory;
            return this;
        }

        public RequestProcessor withTestProperties(Map<String, String> testProperties) {
            this.testProperties.putAll(testProperties);
            return this;
        }
    }
}
