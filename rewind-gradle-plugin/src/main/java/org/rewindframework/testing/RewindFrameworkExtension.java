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

import org.gradle.api.DomainObjectCollection;
import org.gradle.api.Project;
import org.gradle.api.internal.DefaultDomainObjectCollection;

import javax.inject.Inject;
import java.util.ArrayList;

// TODO: This code is shelved
public class RewindFrameworkExtension {
    private final DomainObjectCollection<String> tests;

    @Inject
    public RewindFrameworkExtension(Project project) {
        tests = project.getObjects().newInstance(TestCollection.class);
    }

    public DomainObjectCollection<String> getTests() {
        return tests;
    }

    public static class TestCollection extends DefaultDomainObjectCollection<String> {
        @Inject
        public TestCollection() {
            super(String.class, new ArrayList<>());
        }
    }
}
