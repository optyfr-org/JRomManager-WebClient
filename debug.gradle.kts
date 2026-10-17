plugins {
    id("de.undercouch.download") version "5.7.0"
    id("de.esoco.gwt") version "1.3.1"
    id("org.sonarqube") version "7.5.0.8588"
}

repositories {
    mavenCentral()
}

sonar {
    properties {
        property("sonar.projectKey", "optyfr_JRomManager-WebClient")
        property("sonar.organization", "optyfr")
        property("sonar.host.url", "https://sonarcloud.io")
    }
}

val wcProps = java.util.Properties()
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

apply(plugin = "java")
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

apply(plugin = "eclipse")

extensions.configure<org.gradle.plugins.ide.eclipse.model.EclipseModel>("eclipse") {
    classpath {
        isDownloadJavadoc = true
        isDownloadSources = true
        containers("com.gwtplugins.gwt.eclipse.core.GWT_CONTAINER", "org.eclipse.buildship.core.gradleclasspathcontainer")
        // defaultOutputDir = file('war/WEB-INF/classes')
    }
}

apply(plugin = "de.undercouch.download")

tasks.register<de.undercouch.gradle.tasks.download.Download>("downloadSmartGWT") {
    src(
        listOf(
            "https://www.smartclient.com/builds/SmartGWT/${scversion}/LGPL/${scdate}/smartgwt.jar",
            "https://www.smartclient.com/builds/SmartGWT/${scversion}/LGPL/${scdate}/smartgwt-skins.jar"
        )
    )
    dest("$buildDir/smartgwt-${scversion}")
    onlyIfModified(true)
}

tasks.register<de.undercouch.gradle.tasks.download.Download>("dlSmartGWT") {
    src("https://www.smartclient.com/builds/SmartGWT/${scversion}/LGPL/${scdate}/smartgwt-${scversion}.zip")
    dest(buildDir)
    onlyIfModified(true)
}

tasks.register<Copy>("extractSmartGWT") {
    dependsOn("dlSmartGWT")
    from(zipTree("$buildDir/smartgwt-${scversion}.zip")) {
        include("**/smartgwt.jar")
        include("**/smartgwt-skins.jar")
        includeEmptyDirs = false
    }
    into(buildDir)
}

tasks.register("SmartGWT") {
    dependsOn("extractSmartGWT")
    outputs.file("$buildDir/smartgwt-${scversion}/smartgwt.jar")
}

tasks.register("SmartGWTSkins") {
    dependsOn("extractSmartGWT")
    outputs.file("$buildDir/smartgwt-${scversion}/smartgwt-skins.jar")
}

apply(plugin = "war")
apply(plugin = "de.esoco.gwt")

dependencies {
    "compileOnly"("org.projectlombok:lombok:1.18.46")
    "annotationProcessor"("org.projectlombok:lombok:1.18.46")
    // NOTE: files(tasks.named(...)) wires the task outputs (the downloaded
    // SmartGWT jars); files("SmartGWT") would be a literal path.
    "implementation"(files(tasks.named("SmartGWT")))
    "implementation"(files(tasks.named("SmartGWTSkins")))
    // implementation(group = "com.sksamuel.gwt", name = "gwt-websockets", version = "1.+")
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
        compileReport = true
        /** Compile quickly with minimal optimizations. */
        draftCompile = true
        /** Include assert statements in compiled output. */
        checkAssertions = false
        /** Script output style. (OBF, PRETTY, DETAILED)*/
        setStyle("DETAILED")
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
        setMethodNameDisplayMode("FULL")
        /** Specifies JsInterop mode (NONE, JS, CLOSURE) */
        // jsInteropMode = "JS"
        /** Generate and export JsInterop (since GWT 2.8) */
        generateJsInteropExports = true

        /** shown all compile errors */
        strict = false

        setWar("$buildDir/gwt/debug")

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
            delete("$buildDir/gwt/debug")
        }
    }
}

tasks.named<War>("war") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from("war")
    rootSpec.exclude("**/jrm")
    rootSpec.exclude("**/*.jar")
    archiveBaseName.set("debug")
    archiveVersion.set("")
    doFirst {
        delete {
            delete("war/smartgwt")
        }
        copy {
            from("$buildDir/gwt/debug")
            into("war")
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        }
    }
}

tasks.register<Zip>("webZip") {
    dependsOn("gwtCompile")
    from("$buildDir/gwt/out/smartgwt")
    into("webclient")
    archiveBaseName.set("debug")
    archiveFileName.set("debug.zip")
    destinationDirectory.set(file("$buildDir/distributions"))
}

tasks.named("build") {
    dependsOn("webZip")
}

tasks.named("jar") {
    (this as Jar).duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
