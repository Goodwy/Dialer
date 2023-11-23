import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.konan.properties.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ksp)
    base
}

base {
    archivesName.set("dialer")
}

val keystorePropertiesFile: File = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}
val properties = Properties().apply {
    load(rootProject.file("local.properties").reader())
}

android {
    compileSdk = project.libs.versions.app.build.compileSDKVersion.get().toInt()

    defaultConfig {
        applicationId = libs.versions.app.version.appId.get()
        minSdk = project.libs.versions.app.build.minimumSDK.get().toInt()
        targetSdk = project.libs.versions.app.build.targetSDK.get().toInt()
        versionName = project.libs.versions.app.version.versionName.get()
        versionCode = project.libs.versions.app.version.versionCode.get().toInt()
        buildConfigField("String", "GOOGLE_PLAY_LICENSING_KEY", "\"${properties["GOOGLE_PLAY_LICENSE_KEY"]}\"")
        buildConfigField("String", "PRODUCT_ID_X1", "\"${properties["PRODUCT_ID_X1"]}\"")
        buildConfigField("String", "PRODUCT_ID_X2", "\"${properties["PRODUCT_ID_X2"]}\"")
        buildConfigField("String", "PRODUCT_ID_X3", "\"${properties["PRODUCT_ID_X3"]}\"")
        buildConfigField("String", "SUBSCRIPTION_ID_X1", "\"${properties["SUBSCRIPTION_ID_X1"]}\"")
        buildConfigField("String", "SUBSCRIPTION_ID_X2", "\"${properties["SUBSCRIPTION_ID_X2"]}\"")
        buildConfigField("String", "SUBSCRIPTION_ID_X3", "\"${properties["SUBSCRIPTION_ID_X3"]}\"")
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            register("release") {
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
            }
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    flavorDimensions.add("variants")
    productFlavors {
        register("core")
        register("fdroid")
        register("prepaid")
    }

    sourceSets {
        getByName("main").java.srcDirs("src/main/kotlin")
    }

    compileOptions {
        val currentJavaVersionFromLibs = JavaVersion.valueOf(libs.versions.app.build.javaVersion.get().toString())
        sourceCompatibility = currentJavaVersionFromLibs
        targetCompatibility = currentJavaVersionFromLibs
    }

    tasks.withType<KotlinCompile> {
        kotlinOptions.jvmTarget = project.libs.versions.app.build.kotlinJVMTarget.get()
    }

    namespace = libs.versions.app.version.appId.get()

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    //implementation(libs.simple.tools.commons)
    implementation(libs.indicator.fast.scroll)
    implementation(libs.autofit.text.view)
    implementation(libs.kotlinx.serialization.json)

    //Goodwy
    implementation(libs.goodwy.commons)
    implementation(libs.shortcut.badger)
    implementation(libs.googlecode.libphonenumber)
    implementation(libs.googlecode.geocoder)
    implementation(libs.rustore.client)
    implementation(libs.behavio.rule)
    implementation(libs.rx.animation)
    implementation(libs.rx.java)
    //timer
    implementation(libs.eventbus)
    implementation(libs.bundles.lifecycle)
    ksp(libs.androidx.room.compiler)

//    implementation files('libs/commons-debug.aar')
//    implementation 'com.github.tibbi:IndicatorFastScroll:4524cd0b61'
//    implementation 'me.grantland:autofittextview:0.2.1'
//    implementation "org.jetbrains.kotlinx:kotlinx-serialization-json:1.4.1"
//
//    implementation 'com.googlecode.libphonenumber:libphonenumber:8.12.49'
//    implementation 'com.googlecode.libphonenumber:geocoder:2.185'
//    implementation 'me.leolin:ShortcutBadger:1.1.22@aar'
//    implementation 'com.mikhaellopez:rxanimation:2.1.0'
//    implementation 'com.anjlab.android.iab.v3:library:2.0.3'
//    implementation 'com.github.Liverm0r:BehavioRule:1.0.1'
//
//    api 'com.google.android.material:material:1.9.0'
//    api 'com.google.code.gson:gson:2.9.1'
//    api 'com.github.duolingo:rtl-viewpager:940f12724f'
//
//    api 'com.github.bumptech.glide:glide:4.15.1'
//    annotationProcessor 'com.github.bumptech.glide:compiler:4.15.1'
//    implementation "androidx.core:core-ktx:1.8.0"
//    implementation "org.jetbrains.kotlin:kotlin-stdlib-jdk7:$kotlin_version"
//
//    implementation 'androidx.room:room-runtime:2.5.2'
//    kapt 'androidx.room:room-compiler:2.5.2'
//    implementation 'joda-time:joda-time:2.11.0'
//
//    //timer
//    implementation 'org.greenrobot:eventbus:3.3.1'
//    implementation 'androidx.lifecycle:lifecycle-extensions:2.2.0'
}
