package org.rewindframework.testing.tasks;

import com.rabbitmq.client.*;
import org.rewindframework.testing.messages.LogResponse;
import org.rewindframework.testing.messages.TestingAbort;
import org.rewindframework.testing.messages.TestingRequest;
import org.rewindframework.testing.messages.TestingResponse;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.log4j.Level;
import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.TaskAction;

import java.io.*;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

public class RewindTest extends DefaultTask {
    private final RegularFileProperty testJar = newInputFile();
    private final DirectoryProperty binaryResultsDirectory = newOutputDirectory();
    private final static String QUEUE_NAME = "hello";  // TODO: Configure message queue name using task property
    private final Property<String> hostname = getProject().getObjects().property(String.class);
    private final Property<Integer> port = getProject().getObjects().property(Integer.class);
    private final ListProperty<Dependency> dependencies = getProject().getObjects().listProperty(Dependency.class);
    private final ListProperty<ArtifactRepository> repositories = getProject().getObjects().listProperty(ArtifactRepository.class);
    private final Property<String> username = getProject().getObjects().property(String.class);
    private final Property<String> password = getProject().getObjects().property(String.class);

    // TODO: Make configurable
    private static final Duration TIMEOUT = Duration.ofMinutes(10);

//    private final ConfigurableFileCollection testResource

    public RegularFileProperty getTestJar() {
        return testJar;
    }

    public DirectoryProperty getBinaryResultsDirectory() {
        return binaryResultsDirectory;
    }

    public Property<String> getHostname() {
        return hostname;
    }

    public Property<Integer> getPort() {
        return port;
    }

    public ListProperty<Dependency> getDependencies() {
        return dependencies;
    }

    public ListProperty<ArtifactRepository> getRepositories() {
        return repositories;
    }

    public Property<String> getUsername() {
        return username;
    }

    public Property<String> getPassword() {
        return password;
    }

    // TODO: Try and detect cancel build to cancel the message in the queue... not super important
    // TODO: Use task logger to write to the console
    // TODO: create utility class to create ssh pipe: http://www.jcraft.com/jsch/examples/PortForwardingL.java.html
    //       ssh -p <port> -L 5672:localhost:5672 <user>@<address>
    // See https://stackoverflow.com/a/25135706/9328099 for creating user
    @TaskAction
    private void doTesting() {
        try {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(hostname.get());
            factory.setPort(port.get());

            if (username.isPresent()) {
                factory.setUsername(username.get());
            }
            if (password.isPresent()) {
                factory.setPassword(password.get());
            }
//            factory.setVirtualHost("vhost");
            try (Connection connection = factory.newConnection()) {
                // TODO: model better the id to be implementation agnostic
                String id = UUID.randomUUID().toString();
                getLogger().debug(String.format("Rewind test id '%s'", id));
                try (Channel channel2 = connection.createChannel()) {
                    channel2.exchangeDeclare(id, "fanout");
                    String queueName = channel2.queueDeclare().getQueue();
                    channel2.queueBind(queueName, id, "");


                    try (Channel channel = connection.createChannel()) {

                        channel.queueDeclare(QUEUE_NAME, false, false, false, null);

                        TestingRequest configuration = TestingRequest.from(testJar.get().getAsFile()).withId(id).withDependencies(toDepedencies(dependencies.get())).withRepositories(toRepositories(repositories.get()));

                        AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder()
                            .expiration(String.valueOf(TIMEOUT.toMillis()))
                            .build();
                        channel.basicPublish("", QUEUE_NAME, properties, SerializationUtils.serialize(configuration));
                    }

                    Semaphore s = new Semaphore(0);
                    TestResult testResult = new TestResult();
                    Consumer consumer = new DefaultConsumer(channel2) {
                        @Override
                        public void handleDelivery(String consumerTag, Envelope envelope,
                                                   AMQP.BasicProperties properties, byte[] body) throws IOException {

                            // TODO: Figure out why the following code is giving a ClassNotFoundException
//                    TestingResponse result = (TestingResponse)SerializationUtils.<Object>deserialize(body);
//                    FileUtils.writeByteArrayToFile(binaryResultsDirectory.file("results.bin").get().getAsFile(), result.resultsBin);
//                    FileUtils.writeByteArrayToFile(binaryResultsDirectory.file("output.bin").get().getAsFile(), result.outputBin);
//                    FileUtils.writeByteArrayToFile(binaryResultsDirectory.file("output.bin.idx").get().getAsFile(), result.outputBinIdx);

                            try (ObjectInputStream objectSteam = new ObjectInputStream(new ByteArrayInputStream(body))) {
                                Object obj = objectSteam.readObject();
                                if (obj instanceof LogResponse) {
                                    LogResponse log = (LogResponse) obj;
                                    getLogger().log(toLogLevel(Level.toLevel(log.level)), log.message);
                                } else if (obj instanceof String) {
                                    System.out.println(obj.toString());
                                } else if (obj instanceof TestingResponse) {
                                    TestingResponse result = (TestingResponse) obj;
                                    result.to(binaryResultsDirectory.get().getAsFile());
                                    testResult.value = result.testSucceed;
                                    s.release();
                                } else if (obj instanceof TestingAbort) {
                                    // TODO: fail the task
                                    s.release();
                                } else {
                                    System.out.println("Unexpected object returned");
                                }
                            } catch (ClassNotFoundException e) {
                                e.printStackTrace();
                            }
                        }

                        private LogLevel toLogLevel(Level level) {
                            if (Objects.equals(level, Level.DEBUG)) {
                                return LogLevel.DEBUG;
                            } else if (Objects.equals(level, Level.INFO)) {
                                return LogLevel.INFO;
                            } else if (Objects.equals(level, Level.ERROR)) {
                                return LogLevel.ERROR;
                            } else if (Objects.equals(level, Level.FATAL)) {
                                return LogLevel.ERROR;
                            } else if (Objects.equals(level, Level.OFF)) {
                                return LogLevel.QUIET;
                            } else if (Objects.equals(level, Level.WARN)) {
                                return LogLevel.WARN;
                            } else {
                                return LogLevel.INFO;
                            }
                        }
                    };
                    channel2.basicConsume(queueName, true, consumer);

                    // TODO: Timeout.../heart beat
                    s.tryAcquire(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS); // For now we assume 10min as the hard limit

                    if (!testResult.value) {
                        throw new RuntimeException("Test failed");
                    }
                }
            }



        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        } catch (TimeoutException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private List<TestingRequest.Repository> toRepositories(List<ArtifactRepository> repositories) {
        return repositories.stream().map(RewindTest::toRepository).collect(Collectors.toList());
    }

    private static TestingRequest.Repository toRepository(ArtifactRepository repository) {
        if (repository instanceof MavenArtifactRepository) {
            TestingRequest.Repository result = TestingRequest.Repository.maven(repository.getName());
            result.setUrl(((MavenArtifactRepository) repository).getUrl());
            return result;
        }
        throw new IllegalArgumentException();
    }

    private static List<String> toDepedencies(List<Dependency> dependencies) {
        return dependencies.stream().map(RewindTest::toDependencyNotation).collect(Collectors.toList());
    }

    private static String toDependencyNotation(Dependency dependency) {
        assert !(dependency instanceof ProjectDependency);
        return dependency.getGroup() + ":" + dependency.getName() + ":" + dependency.getVersion();
    }

    private static class TestResult {
        boolean value = false;
    }
}
