import java.net.URI

plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.maven.publish)
  alias(libs.plugins.dokka)
}

val libraryVersion = providers.fileContents(rootProject.layout.projectDirectory.file("VERSION.txt"))
  .asText
  .map(String::trim)

android {
  namespace = "me.gorani.launchingview"
  compileSdk = 36

  defaultConfig {
    minSdk = 23
    consumerProguardFiles("consumer-rules.pro")
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  buildFeatures {
    compose = true
  }

  testOptions {
    unitTests.isReturnDefaultValues = true
    unitTests.isIncludeAndroidResources = true
  }
}

dependencies {
  api(libs.launching.service)
  api(platform(libs.androidx.compose.bom))
  api(libs.androidx.compose.runtime)
  implementation(libs.androidx.compose.foundation)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.kotlinx.coroutines.android)

  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
}

dokka {
  dokkaPublications.html {
    moduleName.set("LaunchingView Android")
    moduleVersion.set(libraryVersion)
    outputDirectory.set(layout.buildDirectory.dir("dokka/html"))
    failOnWarning.set(true)
    includes.from(rootProject.file("README.md"))
  }

  dokkaSourceSets.configureEach {
    sourceLink {
      localDirectory.set(file("src/main/kotlin"))
      remoteUrl.set(
        URI(
          "https://github.com/swift-man/LaunchingView-Android/" +
            "blob/main/launching-view/src/main/kotlin",
        ),
      )
      remoteLineSuffix.set("#L")
    }
  }
}

mavenPublishing {
  coordinates(
    groupId = "me.gorani",
    artifactId = "launching-view",
    version = libraryVersion.get(),
  )

  publishToMavenCentral()

  if (providers.environmentVariable("ORG_GRADLE_PROJECT_signingInMemoryKey").isPresent) {
    signAllPublications()
  }

  pom {
    name.set("LaunchingView Android")
    description.set("Jetpack Compose launch gate that shows update, notice, and blocking screens from LaunchingService.")
    inceptionYear.set("2026")
    url.set("https://github.com/swift-man/LaunchingView-Android")

    licenses {
      license {
        name.set("MIT License")
        url.set("https://opensource.org/licenses/MIT")
        distribution.set("repo")
      }
    }

    developers {
      developer {
        id.set("swift-man")
        name.set("swift-man")
        url.set("https://github.com/swift-man")
      }
    }

    scm {
      url.set("https://github.com/swift-man/LaunchingView-Android")
      connection.set("scm:git:git://github.com/swift-man/LaunchingView-Android.git")
      developerConnection.set("scm:git:ssh://git@github.com/swift-man/LaunchingView-Android.git")
    }
  }
}
