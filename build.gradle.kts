/*
 * Copyright 2020 Russell Wolf
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.nexus.publish)
    alias(libs.plugins.kotlin.binaryCompatibilityValidator)
}

allprojects {
    group = "com.russhwolf"
}

nexusPublishing {
    repositories {
        sonatype()
    }
}

apiValidation {
    ignoredProjects.add("tests")
    @OptIn(kotlinx.validation.ExperimentalBCVApi::class)
    klib {
        enabled = true
    }
}


subprojects {
    plugins.withId("org.jetbrains.kotlin.multiplatform") {
        configure<org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension> {
            compilerOptions {
                val customLanguageVersion = findProperty("kotlin_language_version")
                println("Module: ${project.name}. Custom LanguageVersion: $customLanguageVersion")
                customLanguageVersion?.let {
                    languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.fromVersion(it.toString()))
                }

                val customApiVersion = findProperty("kotlin_api_version")
                println("Module: ${project.name}. Custom ApiVersion: $customApiVersion")
                customApiVersion?.let {
                    apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.fromVersion(it.toString()))
                }

                val customCompilerArguments = findProperty("kotlin_free_compiler_args")
                println("Module: ${project.name}. Custom FreeCompilerArgs: $customApiVersion")
                customCompilerArguments?.let {
                    val args: List<String> = it.toString().split(", ")
                    freeCompilerArgs.addAll(args)
                }
            }
        }
    }

    // !! infrastructure for builds as a Kotlin user project
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach kotlinCompilationTask@ {
        // ("unconditionally pass the -Xreport-all-warnings CLI option to the Kotlin compiler")
        // ("unconditionally pass the -Xrender-internal-diagnostic-names CLI option to the Kotlin compiler")
        compilerOptions {
            // output reported warnings even in the presence of reported errors
            freeCompilerArgs.add("-Xreport-all-warnings")
            logger.info("<KUP> ${this@kotlinCompilationTask.path} : added -Xreport-all-warnings")
            // output kotlin.git-searchable names of reported diagnostics
            freeCompilerArgs.add("-Xrender-internal-diagnostic-names")
            logger.info("<KUP> ${this@kotlinCompilationTask.path} : added -Xrender-internal-diagnostic-names")
        }
    }
    // !! infrastructure for builds as a Kotlin user project
    // ("download Robolectric android-all jars via cache-redirector instead of Maven Central")
    tasks.withType<Test>().configureEach {
        systemProperty("robolectric.dependency.repo.url", "https://cache-redirector.jetbrains.com/maven-central")
    }

// !! infrastructure for builds as a Kotlin user project
// ("implement a Gradle property for passing additional CLI options to the Kotlin compiler")
    val kotlinAdditionalCliOptions = providers.gradleProperty("kotlin_additional_cli_options")
        .map { if (it.isNotBlank()) it.split(" ") else emptyList() } // i sure hope that this level of parsing is in fact enough
        .orNull
    if (kotlinAdditionalCliOptions != null) {
        tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach kotlinCompilationTask@ {
            compilerOptions {
                freeCompilerArgs.addAll(kotlinAdditionalCliOptions)
                logger.info("<KUP> ${this@kotlinCompilationTask.path} : added ${kotlinAdditionalCliOptions.joinToString(" ")}")
            }
        }
    }

// !! infrastructure for builds as a Kotlin user project
// ("unconditionally disable the -Werror compiler CLI option")
    afterEvaluate {
        tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach kotlinCompilationTask@ {
            compilerOptions {
                allWarningsAsErrors.set(false)
                freeCompilerArgs.set(
                    freeCompilerArgs.getOrElse(emptyList()).filterNot { it == "-Werror" }
                )
                logger.info("<KUP> ${this@kotlinCompilationTask.path} : disabled -Werror")
            }
        }
    }
}