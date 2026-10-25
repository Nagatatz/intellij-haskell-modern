import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.changelog")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")

    intellijPlatform {
        // 統合版 IntelliJ IDEA (2025.3 以降は IC/IU の区別なし)。LSP API は無償で利用可能。
        intellijIdea(providers.gradleProperty("platformVersion"))
        // 組み込み LSP クライアント API (com.intellij.modules.lsp) は ultimate モジュール側に含まれる
        bundledModule("intellij.platform.lsp")
        testFramework(TestFrameworkType.Platform)
        testBundledPlugins("com.intellij.modules.ultimate")
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            // LspIntegrationProvider / LspClientDescriptor (改名後 API) は 2026.1.4 以降
            sinceBuild = "261.26222"
        }
    }
    pluginVerification {
        ides {
            // recommended() は複数 IDE をダウンロードするため、ローカル検証では対象バージョンに限定
            create(IntelliJPlatformType.IntellijIdea, providers.gradleProperty("platformVersion"))
        }
    }
}
