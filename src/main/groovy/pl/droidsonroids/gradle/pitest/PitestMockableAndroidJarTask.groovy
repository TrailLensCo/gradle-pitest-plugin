/* Copyright (c) 2012 Marcin Zajączkowski
 * All rights reserved.
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
package pl.droidsonroids.gradle.pitest

import com.android.builder.testing.MockableJarGenerator
import groovy.transform.CompileDynamic
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * AGP-9-only: sdkDirectory, compileSdkVersion, and returnDefaultValues are injected as task
 * inputs (populated by PitestPlugin from `androidComponents.sdkComponents.sdkDirectory` and
 * `CommonExtension`). The legacy `project.android.sdkDirectory` accessor was removed from the
 * `android { }` DSL in AGP 9.0.
 */
@CompileDynamic
abstract class PitestMockableAndroidJarTask extends DefaultTask {

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract DirectoryProperty getSdkDirectory()

    @Input
    abstract Property<String> getCompileSdkVersion()

    @Input
    abstract Property<Boolean> getReturnDefaultValues()

    @OutputFile
    File getOutputJar() {
        String suffix = returnDefaultValues.getOrElse(false) ? "-default-values" : ""
        String outputJarFilename = "pitest-${compileSdkVersion.getOrElse('')}${suffix}.jar"
        return new File(project.buildDir, outputJarFilename)
    }

    @TaskAction
    @SuppressWarnings("BuilderMethodWithSideEffects")
    protected void createMockableAndroidJar() {
        File outputJarFile = outputJar
        if (!outputJarFile.parentFile.mkdirs() && !outputJarFile.parentFile.isDirectory()) {
            throw new IOException("Could not create directory at ${outputJarFile.parentFile}")
        }

        if (outputJarFile.isFile()) {
            outputJarFile.delete()
        }

        File inputJar = new File("${sdkDirectory.get().asFile}/platforms/${compileSdkVersion.get()}/android.jar")
        MockableJarGenerator generator = new MockableJarGenerator(returnDefaultValues.getOrElse(false))
        generator.createMockableJar(inputJar, outputJarFile)
    }

}
