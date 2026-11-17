// 仓库顺序：国内镜像在前（访问更快），google() / mavenCentral() 保留为常规回退。
// SKYSIM_LOCAL_REPO 可指向本地 flatDir 形式的 Maven 仓库（可选，离线构建时用）。
pluginManagement {
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        val local = System.getenv("SKYSIM_LOCAL_REPO")
        if (local != null) maven { url = uri(local) }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        google()
        mavenCentral()
    }
}
rootProject.name = "SkySimulator"
include(":app")
