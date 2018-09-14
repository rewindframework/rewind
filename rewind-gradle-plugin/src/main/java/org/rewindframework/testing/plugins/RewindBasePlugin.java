package org.rewindframework.testing.plugins;

import com.github.jengelman.gradle.plugins.shadow.internal.DependencyFilter;
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar;
import org.rewindframework.testing.RewindFrameworkExtension;
import org.rewindframework.testing.tasks.RewindTest;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.testing.Test;
import org.gradle.api.tasks.testing.TestReport;
import org.gradle.language.base.plugins.LifecycleBasePlugin;

import java.io.File;
import java.util.Arrays;
import java.util.stream.Collectors;

public class RewindBasePlugin implements Plugin<Project> {
    // TODO: The concept between Rewind test execution (running the test locally and running them remotely) and test definition should be separated. The testing framework is an implementation detail of the framework (PyTest could be used for example).
    @Override
    public void apply(Project project) {
        project.getPluginManager().apply("groovy-base");

        RewindFrameworkExtension rewind = project.getObjects().newInstance(RewindFrameworkExtension.class, project);
        project.getExtensions().add("rewind", rewind);

        rewind.getTests().all(name -> {
            SourceSetContainer sourceSets = project.getConvention().getPlugin(JavaPluginConvention.class).getSourceSets();
            SourceSet sourceSet = sourceSets.create(name, it -> it.setRuntimeClasspath(it.getRuntimeClasspath().add(it.getOutput()).add(it.getCompileClasspath())));


            Test test = project.getTasks().create(sourceSet.getName(), Test.class, task -> {
                task.setGroup(LifecycleBasePlugin.VERIFICATION_GROUP);
                task.setTestClassesDirs(sourceSet.getOutput().getClassesDirs());
                task.setClasspath(sourceSet.getRuntimeClasspath());
            });

            project.getTasks().getByName("check").dependsOn(test);

            Jar jar = project.getTasks().create(sourceSet.getName() + "Jar", ShadowJar.class, (ShadowJar task) -> {
                task.setClassifier("tests");

                task.from(sourceSet.getOutput());
                task.setConfigurations(Arrays.asList(project.getConfigurations().getByName(sourceSet.getName() + "RuntimeClasspath")));
                task.dependencies((DependencyFilter filter) -> {
                    for (Dependency d : project.getConfigurations().getByName(sourceSet.getName() + "Implementation").getDependencies()) {
                        if (!(d instanceof ProjectDependency)) {
                            filter.exclude(filter.dependency(d));
                        }
                    }
                });
            });

            RewindTest remoteTest = project.getTasks().create(sourceSet.getName() + "Rewind", RewindTest.class, (RewindTest task) -> {
                task.setGroup(LifecycleBasePlugin.VERIFICATION_GROUP);
                task.dependsOn(jar);
                task.getHostname().set("localhost");
                task.getPort().set(5672);
                task.getTestJar().set(jar.getArchivePath());
                task.getBinaryResultsDirectory().set(project.getLayout().getBuildDirectory().dir("test-results/" + sourceSet.getName() + "Rewind/binary"));
                task.getDependencies().set(
                    project.provider(
                        () -> project.getConfigurations().getByName(sourceSet.getName() + "Implementation").getDependencies().stream()
                            .filter(((Dependency dependency) -> !(dependency instanceof ProjectDependency)))
                            .collect(Collectors.toList())
                    ));
                task.getRepositories().set(project.getRepositories());
                task.finalizedBy(sourceSet.getName() + "RewindReport");
            });

            project.getTasks().create(sourceSet.getName() + "RewindReport", TestReport.class, (TestReport task) -> {
                task.setTestResultDirs(Arrays.asList(remoteTest.getBinaryResultsDirectory().getAsFile().get()));
                task.setDestinationDir(new File(project.getBuildDir(), "reports/tests/" + sourceSet.getName() + "Rewind"));
            });
        });
    }
}
