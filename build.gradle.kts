// NOTE: de.undercouch.download is pinned (not latest.release) to match the
// root build's catalog pin (gradle/libs.versions.toml: gradle-download-task).
// A floating version here conflicts with the root plugin classpath when
// WebClient builds as a subproject (:WebClient:jar).
import java.util.Properties

plugins {
    // java/war FIRST: the gwt plugin touches the javadoc/sourceSets model in
    // its apply, which needs java already applied (plugin order matters).
    java
    war
    id("de.undercouch.download") version "5.7.0"
    id("de.esoco.gwt") version "1.3.1"
    id("org.sonarqube") version "7.3.1.8318"
}

repositories {
    mavenCentral()
}

sonar {
    properties {
        property("sonar.projectKey", "optyfr-org_JRomManager-WebClient")
        property("sonar.organization", "optyfr-org")
        property("sonar.host.url", "https://sonarcloud.io")
        property("sonar.sourceEncoding", "utf-8")
        property("sonar.log.level", "WARNING")
        property("sonar.verbose", "false")
        property("sonar.scm.provider", "git")
        property("sonar.coverage.exclusions", "**/*.java")
    }
}

val wcProps = Properties()
file("ver.properties").inputStream().use { stream -> wcProps.load(stream) }

val scversion = wcProps.getProperty("SmartGWT-Version")
val scdate = wcProps.getProperty("SmartGWT-Date")
val gwtversion = wcProps.getProperty("GWT-Version")

configurations.all {
    resolutionStrategy {
        dependencySubstitution {
            substitute(module("com.google.gwt:gwt")).using(module("org.gwtproject:gwt:$gwtversion"))
            substitute(module("com.google.gwt:gwt-user")).using(module("org.gwtproject:gwt-user:$gwtversion"))
            substitute(module("com.google.gwt:gwt-dev")).using(module("org.gwtproject:gwt-dev:$gwtversion"))
            substitute(module("com.google.gwt:gwt-codeserver")).using(module("org.gwtproject:gwt-codeserver:$gwtversion"))
            substitute(module("com.google.gwt:gwt-servlet")).using(module("org.gwtproject:gwt-servlet:$gwtversion"))
        }
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

apply(plugin = "eclipse") // kept as apply(): eclipse has no plugins{} alias here and must stay after java

extensions.configure<org.gradle.plugins.ide.eclipse.model.EclipseModel>("eclipse") {
    classpath {
        isDownloadJavadoc = true
        isDownloadSources = true
        containers("com.gwtplugins.gwt.eclipse.core.GWT_CONTAINER", "org.eclipse.buildship.core.gradleclasspathcontainer")
        // defaultOutputDir = file('war/WEB-INF/classes')
    }
}

// de.undercouch.download already applied via plugins {} above (needed for the Download task type).


tasks.register<de.undercouch.gradle.tasks.download.Download>("downloadSmartGWT") {
    src(
        listOf(
            "https://www.smartclient.com/builds/SmartGWT/${scversion}p/LGPL/${scdate}/smartgwt.jar",
            "https://www.smartclient.com/builds/SmartGWT/${scversion}p/LGPL/${scdate}/smartgwt-skins.jar"
        )
    )
    dest("$buildDir/smartgwt-${scversion}p")
    onlyIfModified(true)
}

tasks.register<de.undercouch.gradle.tasks.download.Download>("dlSmartGWT") {
    src("https://www.smartclient.com/builds/SmartGWT/${scversion}p/LGPL/${scdate}/smartgwt-${scversion}p.zip")
    dest(buildDir)
    onlyIfModified(true)
}

tasks.register<Copy>("extractSmartGWT") {
    dependsOn("dlSmartGWT")
    from(zipTree("$buildDir/smartgwt-${scversion}p.zip")) {
        include("**/smartgwt.jar")
        include("**/smartgwt-skins.jar")
        includeEmptyDirs = false
    }
    into(buildDir)
}

tasks.register("SmartGWT") {
    dependsOn("extractSmartGWT")
    outputs.file("$buildDir/smartgwt-${scversion}p/smartgwt.jar")
}

tasks.register("SmartGWTSkins") {
    dependsOn("extractSmartGWT")
    outputs.file("$buildDir/smartgwt-${scversion}p/smartgwt-skins.jar")
}

// war + de.esoco.gwt already applied via plugins {} above.

dependencies {
    "compileOnly"("org.projectlombok:lombok:1.18.46")
    "annotationProcessor"("org.projectlombok:lombok:1.18.46")
    // NOTE: files(tasks.named(...)) wires the task outputs (the downloaded
    // SmartGWT jars); files("SmartGWT") would be a literal path.
    "implementation"(files(tasks.named("SmartGWT")))
    "implementation"(files(tasks.named("SmartGWTSkins")))
}

sourceSets {
    named("main") {
        java {
            setSrcDirs(listOf("src/main/java"))
        }
    }
}

// NOTE: GwtExtension/CompilerOption mix private fields, String/enum setter
// overloads and File getters with String setters — call setters explicitly;
// a Kotlin property assignment would pick the wrong overload or fail.
gwt {
    module("jrm.webui.Client")
    /** GWT version */
    gwtVersion = "$gwtversion"
    /** Add the gwt-servlet lib */
    setGwtServletLib(false)
    /** Add the gwt-elemental lib */
    setGwtElementalLib(false)
    /** Add Google plugin config (only if plugin 'eclipse' is enabled) */
    setGwtPluginEclipse(true) //    minHeapSize = "512M"
    compile {
        /** The level of logging detail (ERROR, WARN, INFO, TRACE,
         * DEBUG, SPAM, ALL) */
        setLogLevel("INFO")
        /** Compile a report that tells the "Story of Your Compile". */
        compileReport = false
        /** Compile quickly with minimal optimizations. */
        draftCompile = false
        /** Include assert statements in compiled output. */
        checkAssertions = false
        /** Script output style. (OBF, PRETTY, DETAILED)*/
        setStyle("OBF")
        /** Sets the optimization level used by the compiler.
         * 0=none 9=maximum. */
        optimize = 9
        /** Fail compilation if any input file contains an error. */
        failOnError = false
        /** Specifies Java source level. ("1.6", "1.7")*/
        sourceLevel = "auto"
        /** The number of local workers for compiling permutations. */
        localWorkers = 4
        /** The maximum memory to be used by local workers. */
        localWorkersMem = 2048
        /** Emit extra information allow chrome dev tools to display
         * Java identifiers in many places instead of JavaScript functions.
         * (NONE, ONLY_METHOD_NAME, ABBREVIATED, FULL)*/
        setMethodNameDisplayMode("NONE")
        /** Specifies JsInterop mode (NONE, JS, CLOSURE) */
        // jsInteropMode = "JS"
        /** Generate and export JsInterop (since GWT 2.8) */
        generateJsInteropExports = true

        /** shown all compile errors */
        strict = false

        setWar("$buildDir/gwt/release")

        /** Java args */
        val lombokJar = project.configurations["annotationProcessor"].filter { it.name.contains("lombok") }.singleFile
        setJavaArgs("-Dfile.encoding=UTF-8", "-javaagent:$lombokJar=ECJ")
        maxHeapSize = "1024m"
        minHeapSize = "512m"
        // maxPermSize="128m"
    }
}

tasks.named("gwtCompile") {
    doFirst {
        delete {
            delete("$buildDir/gwt/release")
        }
    }
}

tasks.named<War>("war") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from("war")
    rootSpec.exclude("**/jrm")
    rootSpec.exclude("**/*.jar")
    archiveBaseName.set("release")
    archiveVersion.set("")
    doFirst {
        delete {
            delete("war/smartgwt")
        }
        copy {
            from("$buildDir/gwt/release")
            into("war")
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        }
    }
}

tasks.named<Jar>("jar") {
    dependsOn("gwtCompile")
    exclude("/jrm")
    from("$buildDir/gwt/release") {
        into("webclient")
    }
    from("$projectDir/war/index.html") {
        into("webclient")
    }
    from("$projectDir/certs") {
        into("certs")
    }
}

tasks.named("build") {
    dependsOn("jar")
}

tasks.register<Zip>("webZip") {
    dependsOn("gwtCompile")
    from("$buildDir/gwt/out/smartgwt")
    into("webclient")
    archiveBaseName.set("release")
    archiveFileName.set("release.zip")
    destinationDirectory.set(file("$buildDir/distributions"))
}

tasks.named("build") {
    dependsOn("webZip")
}

tasks.named("jar") {
    (this as Jar).duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.register<JavaExec>("delombok") {
    val lombokJar = configurations["annotationProcessor"].filter { it.name.contains("lombok") }
    classpath = lombokJar
    mainClass.set("lombok.launch.Main")

    doFirst {
        args = listOf(
            "delombok",
            "src/main/java",
            "-d", "$buildDir/generated/delombok/src/main/java",
            "--classpath", (project.the<SourceSetContainer>()["main"].compileClasspath + lombokJar).asPath
        )
    }
}

// NOTE: inside task configuration blocks the receiver is the TASK (whose own
// extensions hold only `ext`). Always qualify catalog/sourceSets lookups with
// `project.` here.
tasks.named<Javadoc>("javadoc") {
    dependsOn("delombok")
    source(fileTree("$buildDir/generated/delombok/src/main/java") {
        include("**/*.java")
    })
    classpath = project.the<SourceSetContainer>()["main"].output.classesDirs + configurations["compileClasspath"]
    exclude("**/*.xml", "**/*.properties")
    (options as StandardJavadocDocletOptions).apply {
        encoding = "UTF-8"
        memberLevel = JavadocMemberLevel.PACKAGE
        outputLevel = JavadocOutputLevel.QUIET
        addBooleanOption("Xdoclint:none", true)
        addStringOption("Xmaxwarns", "0")
        links("https://docs.oracle.com/en/java/javase/${java.targetCompatibility}/docs/api/")
        links("https://smartclient.com/smartgwt-${scversion}/javadoc/")
        links("https://www.gwtproject.org/javadoc/latest/")
    }
}
