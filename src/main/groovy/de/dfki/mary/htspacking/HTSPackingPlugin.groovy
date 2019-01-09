package de.dfki.mary.htspacking

import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.LogLevel
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.MavenPlugin
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.bundling.Zip

import de.dfki.mary.htspacking.task.*
import de.dfki.mary.htspacking.*

import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import groovy.xml.*

class HTSPackingPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        project.plugins.apply JavaPlugin
        project.plugins.apply MavenPlugin

        project.sourceCompatibility = JavaVersion.VERSION_1_8

        project.ext {
            basename = project.name

            // HTS wrapper
            utils_dir = "$project.buildDir/tmp/utils"
            template_dir = "$project.buildDir/tmp/templates"
        }

        /**
         *  CMP generation task
         *
         */
        project.task('generateCMP', type: GenerateCMPTask) {
            description "Generate CMP coefficients necessary for the HMM training using HTS"
            dependsOn "configurationPacking"
            cmp_directory = project.file("$project.buildDir/cmp")
        }

        /**
         *  FFO generation task
         *
         */
        project.task('generateFFO', type: GenerateFFOTask) {
            description "Generate FFO coefficients necessary for the HMM training using HTS"
            dependsOn "configurationPacking"
            ffo_dir = new File("$project.buildDir/ffo")
        }
    }

}
