# Deepdive build script

To make the app without having to modify the deepdive build script, 
create the file "/.gradle/keystore.properties". In this file add the
following four lines:

```
DD_keyAlias=deepdive
DD_storeFile=/Users/... path to your app keystore
DD_storePassword= ... your keystore password
DD_keyPassword= --- your app key password
```

The advantage to this approach is that your private app details are not managed
by git and will not be exposed. If you choose to use another approach that is fine
just do not publish your private app passwords to git.


## Detailed build script
Previously the app had a detailed build script. It was simplified by using
a single command to load and compile all of the jar files under /libs. 
The historical build script is captured here.

```
implementation fileTree(include: ['*.jar'], dir: 'libs')
```

## Historical build script
```
buildscript {
    repositories {
        maven { url 'https://maven.fabric.io/public' }
    }

    dependencies {
        // The Fabric Gradle plugin uses an open ended version to react
        // quickly to Android tooling updates
        classpath 'io.fabric.tools:gradle:1.+'
    }
}

apply plugin: 'com.android.application'
apply plugin: 'idea' //Add to see source code instead of decompiled jar code
apply plugin: 'io.fabric'

repositories {
    maven { url 'https://maven.fabric.io/public' }
    maven { url "https://jitpack.io" }
}

def versionMajor = 0
def versionMinor = 9
def versionPatch = 7
def versionBuild = 1

// Load keystore
def keystorePropertiesPath = System.getProperty("user.home")+"/.gradle/keystore.properties"
def keystoreProperties = new Properties()
try {
    keystoreProperties.load(new FileInputStream(keystorePropertiesPath))
} catch (FileNotFoundException exception) {
    keystoreProperties.setProperty('DD_keyAlias','dummy')
    keystoreProperties.setProperty('DD_storeFile','dummy')
    keystoreProperties.setProperty('DD_storePassword','dummy')
    keystoreProperties.setProperty('DD_keyPassword','dummy')
}

android {
    signingConfigs {
        release {
            keyAlias keystoreProperties['DD_keyAlias']
            storeFile file(keystoreProperties['DD_storeFile'])
            storePassword keystoreProperties['DD_storePassword']
            keyPassword keystoreProperties['DD_keyPassword']
        }
        debug {
            // Disable fabric build ID generation for debug builds
            ext.enableCrashlytics = false
        }
    }
    compileSdkVersion 28
    buildToolsVersion "28.0.3"

    defaultConfig {
        applicationId "com.nuvolect.deepdive"
        minSdkVersion 21
        targetSdkVersion 28

        versionCode versionMajor * 10000 + versionMinor * 1000 + versionPatch * 100 + versionBuild
        versionName "${versionMajor}.${versionMinor}.${versionPatch}"
        multiDexEnabled true

        buildConfigField "long", "BUILD_TIMESTAMP", System.currentTimeMillis() + "L"

        testInstrumentationRunner 'android.support.test.runner.AndroidJUnitRunner'
    }
    buildTypes {
        debug {
            debuggable true
            applicationIdSuffix ".debug"
            versionNameSuffix "-debug"
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.pro'
            signingConfig signingConfigs.debug
            manifestPlaceholders = [authorities:"com.nuvolect.deepdive.fileprovider"]
        }
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.pro'
            signingConfig signingConfigs.release
            manifestPlaceholders = [authorities:"com.nuvolect.deepdive.fileprovider-release"]
        }
    }
    packagingOptions {
        exclude 'META-INF/LICENSE.txt'
        exclude 'META-INF/LICENSE'
        exclude 'META-INF/license.txt'
        exclude 'META-INF/NOTICE.txt'
        exclude 'META-INF/NOTICE'
        exclude 'META-INF/notice.txt'
    }
    lintOptions {
        abortOnError false
    }
}

dependencies {
    implementation fileTree(include: ['*.jar'], dir: 'libs')

    // Required for local unit tests (JUnit 4 framework)
    implementation 'junit:junit:4.12'
    testImplementation 'junit:junit:4.12'

    // Required for instrumented tests
    testImplementation 'com.android.support:support-annotations:28.0.0'
    implementation 'com.android.support.test:runner:1.0.2'
    testImplementation 'com.android.support.test:runner:1.0.2'
    testImplementation 'com.android.support.test:rules:1.0.2'
    implementation 'com.android.support:appcompat-v7:28.0.0'
    implementation 'com.android.support:support-v4:28.0.0'
    implementation 'com.android.support:support-v13:28.0.0'
    implementation 'com.android.support:design:28.0.0'

    //    compile files('libs/libGoogleAnalyticsServices.jar')
    implementation 'com.squareup.okio:okio:1.6.0'
    implementation 'com.squareup.okhttp:okhttp:2.6.0'
    implementation 'com.squareup.okhttp:okhttp-urlconnection:2.6.0'
    implementation 'com.github.clans:fab:1.6.4'

    //    compile 'info.guardianproject.iocipher:IOCipherStandalone:0.4'
    implementation 'net.zetetic:android-database-sqlcipher:3.5.4'
    implementation 'info.guardianproject.iocipher:IOCipher:0.4'

    // APACHE COMMONS LIBRARIES
    implementation 'commons-cli:commons-cli:1.3.1'
    implementation 'commons-io:commons-io:2.5'
    implementation 'org.apache.commons:commons-lang3:3.4'

    // MULTI-DEX SUPPORT FOR PRE LOLLIPOP
    implementation 'com.android.support:multidex:1.0.3'

    // DEX2JAR
    implementation 'com.google.guava:guava:20.0'

    //    implementation 'com.google.guava:guava:24.1-jre'// Version has 10 errors, will not compile
    implementation 'asm:asm-all:3.3.1'
    implementation 'org.antlr:antlr:3.5.2'

    //    compile files('libs/dex-ir-1.12.jar')
    //    compile files('libs/dex-reader-1.15.jar')
    //    compile files('libs/dex-tools-0.0.9.15.jar')
    //    compile files('libs/dex-translator-0.0.9.15.jar')
    //    compile files('libs/jsr305-1.3.9.jar')

    // CLASS FILE READER - JAVA DECOMPILER
    //    compile files('libs/cfr_0_120.jar')

    // JaDX
    //    compile files('libs/dx-1.10.jar')
    //    compile files('libs/android-5.1-clst-core.jar')
    implementation 'org.slf4j:slf4j-api:1.7.25'
    implementation 'uk.com.robust-it:cloning:1.9.2'
    implementation 'com.intellij:annotations:12.0@jar'

    // APK PARSER AND BINARY XML DECODER
    //        compile 'net.dongliu:apk-parser:2.1.7'
    api 'com.jaredrummler:apk-parser:1.0.2'

    // Fernflower
    //    compile files('libs/fernflower.jar')
    // CRASHLYTICS BUG REPORTING SDK
    // https://www.fabric.io/kits/android/crashlytics/install
    //    compile('com.crashlytics.sdk.android:crashlytics:2.6.5@aar') {
    //        transitive = true;
    //    }

    // APACHE LUCENE file search

    //    compile group: 'org.apache.lucene', name: 'lucene-core', version: '4.2.1'
    //    compile files('libs/lucene-core-5.3.0-mobile-2.jar')
    //    compile files('libs/lucene-join-5.3.0-mobile-2.jar')
    //    compile files('libs/lucene-queryparser-5.3.0-mobile-2.jar')
    //    compile files('libs/lucene-memory-5.3.0-mobile-2.jar')
    //    compile files('libs/lucene-suggest-5.3.0-mobile-2.jar')
    //    compile files('libs/lucene-analyzers-common-5.3.0-mobile-2.jar')
    //    compile files('libs/lucene-queries-5.3.0-mobile-2.jar')
    //    compile files('libs/lucene-misc-5.3.0-mobile-2.jar')
    //    compile files('libs/lucene-sandbox-5.3.0-mobile-2.jar')
    //    compile files('libs/lucene-highlighter-5.3.0-mobile-2.jar')

    // For headsupdev license manager
    // https://mvnrepository.com/artifact/javax.xml.bind/jaxb-api
    //    compile group: 'javax.xml.bind', name: 'jaxb-api', version: '2.2.6'
    // https://mvnrepository.com/artifact/javax.xml.bind/jaxb-api
    //    compile group: 'javax.xml.bind', name: 'jaxb-api', version: '2.3.0-b170201.1204'
    implementation 'commons-codec:commons-codec:1.10'

    implementation 'com.madgag.spongycastle:bcpkix-jdk15on:1.58.0.0'
    implementation 'com.madgag.spongycastle:bcpg-jdk15on:1.58.0.0'
}
//Add to see source code instead of decompiled jar code
idea {
    module {
        downloadJavadoc = true
        downloadSources = true
    }
}

```