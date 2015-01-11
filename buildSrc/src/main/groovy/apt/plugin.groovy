package apt
import org.gradle.api.*
import org.gradle.api.tasks.*
import org.gradle.api.artifacts.ProjectDependency
public class plugin implements Plugin<Project> {
    void apply(Project project) {
        project.apply plugin: 'apt'
        project.ext.artifactMaps = [:]

        project.ext.addAPT = { artifactMap ->
            project.dependencies {
                println artifactMap
                compile artifactMap
                apt artifactMap
            }
            project.addAPTReq(artifactMap)
        }
        project.ext.addAPTReqWComp = { artifactMap ->
            project.dependencies {
                compile artifactMap
            }
            project.addAPTReq(artifactMap)
        }
        project.ext.addAPTReq = { artifactMap ->
            project.artifactMaps << [(artifactMap.name): artifactMap]
        }
        project.task("copyInAPTThings") {
            mustRunAfter 'cleanCopyInAPTThings'
            description "Copies apt libraries to an appropriate directory for adding to Eclipse."
            ext.outputDir = "libs/apt"
            inputs.files(project.configurations.compile)
            outputs.dir(outputDir)
            doLast {
                project.copy {
                    def copythis = []
                    def artifacts = project.configurations.compile.resolvedConfiguration.resolvedArtifacts
                    .each {
                        println "${project.name}:${it.moduleVersion.id}"
                        if (project.artifactMaps.containsKey(it.name)) {
                            copythis << it.file
                        }
                    }
                    copythis.each {
                        from it
                    }
                    into outputDir
                }
            }
        }

        project.task("writeFactoryPathFile", dependsOn: 'copyInAPTThings') {
            mustRunAfter 'cleanWriteFactoryPathFile'
            description "Writes the factory path for Eclipse"
            ext.outputFile = ".factorypath"
            inputs.file(project.copyInAPTThings.outputs.getFiles().iterator().next())
            doLast {
                def cwd = project.buildDir.getAbsoluteFile().getParentFile().getAbsolutePath()
                def xml = ''
                inputs.getFiles().each { dir ->
                    dir.listFiles().each { file ->
                        def relToHere = file.toString().replace(cwd, "/${project.name}").replace('\\', '/')
                        xml = "${xml}    <factorypathentry kind=\"WKSPJAR\" id=\"${relToHere}\" enabled=\"true\" runInBatchMode=\"false\"/>\n"
                    }
                }
                xml = '<factorypath>\n' + xml + '</factorypath>'
                project.file(outputFile).withWriter { w ->
                    w.writeLine(xml)
                }
            }
        }
        
        project.task("cleanWriteFactoryPathFile", type: Delete) {
            description "Cleans writeFactoryPathFile"
            delete ".factorypath"
        }

        project.cleanEclipseClasspath.dependsOn(project.cleanWriteFactoryPathFile)
        project.cleanEclipseClasspath.dependsOn(project.cleanCopyInAPTThings)
        project.eclipseClasspath.dependsOn(project.writeFactoryPathFile)
    }
}
