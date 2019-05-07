import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
  idea
  java
  id("org.springframework.boot") version Globals.Spring.springBootVersion
  id("io.franzbecker.gradle-lombok") version Globals.Gradle.Plugin.lombokVersion
  id("com.github.ben-manes.versions") version Globals.Gradle.Plugin.versionsVersion
  id("io.spring.dependency-management") version Globals.Gradle.Plugin.dependencyManagementVersion
}

group = Globals.Project.groupId
version = Globals.Project.version

repositories {
  mavenCentral()
  maven(url = "https://repo.spring.io/milestone")
  maven(url = "https://repo.spring.io/snapshot")
}

java {
  sourceCompatibility = Globals.javaVersion
  targetCompatibility = Globals.javaVersion
}

lombok {
  version = Globals.lombokVersion
}

dependencies {
  implementation("io.r2dbc:r2dbc-h2:${Globals.R2dbc.r2dbcH2Version}")
  implementation("io.r2dbc:r2dbc-spi:${Globals.R2dbc.r2dbcSpiVersion}")
  implementation("org.springframework.data:spring-data-r2dbc:${Globals.Spring.springDataR2dbcVersion}")
  implementation("org.springframework.fu:spring-fu-jafu:${Globals.Spring.springFuJafuVersion}")

  implementation(platform("org.springframework.boot:spring-boot-starter-parent:${Globals.Spring.springBootVersion}"))
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-starter-json")
  testImplementation("org.springframework.boot:spring-boot-starter-test")
  //annotationProcessor(platform("org.springframework.boot:spring-boot-starter-parent:${Globals.Spring.springBootVersion}"))
  annotationProcessor("org.projectlombok:lombok")
  testCompileOnly("org.projectlombok:lombok")
  implementation("io.vavr:vavr:${Globals.vavrVersion}")
}

tasks {
  withType(Wrapper::class.java) {
    gradleVersion = Globals.Gradle.wrapperVersion
    distributionType = Wrapper.DistributionType.BIN
  }

  withType(BootJar::class.java) {
    launchScript()
  }

  named("clean") {
    doLast {
      delete(
          buildDir,
          "$projectDir/out"
      )
    }
  }
}

defaultTasks("clean", "build")
