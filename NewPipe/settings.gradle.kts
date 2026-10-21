/*
 * SPDX-FileCopyrightText: 2025 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
rootProject.name = "NewPipe"

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    versionCatalogs {
        create("newplayerLibs") {
            from(files("third-party/NewPlayer/gradle/libs.versions.toml"))
        }
    }
    repositories {
        google()
        mavenCentral()
        maven(url = "https://jitpack.io")
        maven(url = "https://repo.clojars.org")
    }
}
include(":app") // androidApp
include(":desktopApp")
include("shared")
include(":newplayer-local")
project(":newplayer-local").projectDir = file("third-party/NewPlayer")

// Use a local copy of NewPipe Extractor by uncommenting the lines below.
// We assume, that NewPipe and NewPipe Extractor have the same parent directory.
// If this is not the case, please change the path in includeBuild().

//    includeBuild("../NewPipeExtractor") {
//        dependencySubstitution {
//            substitute(module("com.github.TeamNewPipe:NewPipeExtractor"))
//                .using(project(":extractor"))
//        }
//    }

// Physical build selection: the implementation and its Extractor fork are absent when false.
val authSetting = providers.gradleProperty("slooopipe.withAuth").getOrElse("false")
require(authSetting == "true" || authSetting == "false") { "slooopipe.withAuth must be true or false" }
include(":youtube-auth-api")
if (authSetting == "true") {
    include(":youtube-auth-pipepipe")
    includeBuild("third-party/NewPipeExtractor-auth") {
        dependencySubstitution {
            substitute(module("com.github.TeamNewPipe:NewPipeExtractor")).using(project(":extractor"))
        }
    }
}
