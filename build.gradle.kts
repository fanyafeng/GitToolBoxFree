plugins {
    java
}

group = "com.pom.gittoolbox"
version = "1.0.0"

repositories {
    maven { url = uri("https://maven.aliyun.com/repository/public") }
    mavenCentral()
}

val asLibDir = file("/Applications/Android Studio.app/Contents/lib")
val asGitLibDir = file("/Applications/Android Studio.app/Contents/plugins/vcs-git/lib")

dependencies {
    compileOnly(fileTree(asLibDir) { include("**/*.jar") })
    compileOnly(fileTree(asGitLibDir) { include("**/*.jar") })
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.isFork = true
    options.forkOptions.executable = "/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/javac"
}

val preparePluginDir by tasks.registering(Sync::class) {
    dependsOn(tasks.jar)
    into(layout.buildDirectory.dir("plugin/GitToolBoxFree"))
    from(tasks.jar) {
        into("lib")
    }
}

val buildPlugin by tasks.registering(Zip::class) {
    dependsOn(preparePluginDir)
    archiveBaseName.set("GitToolBoxFree")
    archiveVersion.set(project.version.toString())
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    from(layout.buildDirectory.dir("plugin"))
}

tasks.build {
    dependsOn(buildPlugin)
}
