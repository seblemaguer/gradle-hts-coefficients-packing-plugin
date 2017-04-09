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

import static groovyx.gpars.GParsPool.runForkJoin
import static groovyx.gpars.GParsPool.withPool

import de.dfki.mary.htspacking.*

import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import groovy.xml.*

class HTSPackingPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        project.plugins.apply JavaPlugin
        project.plugins.apply MavenPlugin

        project.sourceCompatibility = JavaVersion.VERSION_1_7

        project.ext {
            basename = project.name

            // HTS wrapper
            utils_dir = "$project.buildDir/tmp/utils"
            template_dir = "$project.buildDir/tmp/templates"
        }

        project.afterEvaluate {

            project.task('configuration') {

                // User configuration
                ext.user_configuration = project.configurationPacking.hasProperty("user_configuration") ? project.configurationPacking.user_configuration : null
                ext.config_file = project.configurationPacking.hasProperty("config_file") ? project.configurationPacking.config_file : null
                ext.trained_files = new HashMap()

                // Nb processes
                ext.nb_proc = project.configurationPacking.hasProperty("nb_proc") ? project.configurationPacking.nb_proc : 1

            }


            /**
             * CMP generation task
             */
            project.task('generateCMP') {
                dependsOn "configuration"
                outputs.files "$project.buildDir/cmp" + project.basename + ".cmp"


                doLast {

                    (new File("$project.buildDir/cmp")).mkdirs()
                    def extToDir = new Hashtable<String, String>()
                    extToDir.put("cmp".toString(), "$project.buildDir/cmp".toString())

                    project.configuration.user_configuration.models.cmp.streams.each  { stream ->
                        def kind = stream.kind
                        extToDir.put(kind.toLowerCase().toString(), stream.coeffDir.toString())
                    }

                    def extractor = new ExtractCMP(project.configuration.config_file.toString())
                    extractor.setDirectories(extToDir)
                    extractor.extract("$project.basename")
                }
            }

            /**
             * FFO generation task
             */
            project.task('generateFFO') {
                dependsOn "configuration"
                outputs.files "$project.buildDir/ffo" + project.basename + ".ffo"


                doLast {

                    (new File("$project.buildDir/ffo")).mkdirs()

                    def extToDir = new Hashtable<String, String>()
                    extToDir.put("ffo".toString(), "$project.buildDir/ffo".toString())

                    project.configuration.user_configuration.models.ffo.streams.each  { stream ->
                        def kind = stream.kind
                        extToDir.put(kind.toLowerCase().toString(), stream.coeffDir.toString())
                    }

                    def extractor = new ExtractFFO(project.configuration.config_file.toString())
                    extractor.setDirectories(extToDir)
                    extractor.extract("$project.basename")
                }
            }

            project.task('pack') {
                if (project.configuration.user_configuration.models.cmp) {
                    dependsOn "generateCMP"
                }
                if (project.configuration.user_configuration.models.ffo) {
                    dependsOn "generateFFO"
                }
            }
        }
    }

}
