include("rewind-gradle-plugin")
include("rewind-core")
include("rewind-agent")

rootProject.name = "rewind"
nameBuildScriptsAfterProjectNames(rootProject.children)

fun nameBuildScriptsAfterProjectNames(projects: Set<ProjectDescriptor>) {
    for (prj in projects) {
        prj.buildFileName = prj.name.replace("rewind-", "") + ".gradle.kts"
        nameBuildScriptsAfterProjectNames(prj.children)
    }
}
