package com.flutteride.codweva

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter

/**
 * GradleWrapperWriter — يضمن وجود ملفات Gradle Wrapper في المشروع.
 *
 * v3.0 — Gradle 8.14 (متوافق مع Flutter 3.47.2):
 *   - Flutter 3.47.2 يتطلب Gradle 8.14 على الأقل
 *   - تم تحديث distributionUrl من 8.7 → 8.14
 *
 * v2.0 — يدعم نوعي المشاريع:
 *   - Flutter: android/ هو الجذر (gradle wrapper يوضع هناك)
 *   - Java:    الجذر مباشرة (gradle wrapper يوضع في جذر المشروع)
 *
 * يستخدم النسخة الرسمية الكاملة من gradlew (POSIX-compliant).
 */
object GradleWrapperWriter {

    private const val ASSETS_WRAPPER_DIR = "gradle_wrapper"
    private const val JAR_NAME = "gradle-wrapper.jar"
    private const val MIN_JAR_SIZE = 10_000L

    /** ⭐ v3.0: إصدار Gradle المطلوب لـ Flutter 3.47.2 */
    private const val GRADLE_VERSION = "8.14"

    // ============================================================
    // الدالة الرئيسية
    // ============================================================
    @JvmStatic
    fun ensureGradleWrapper(context: Context?, projectDir: File?) {
        if (context == null || projectDir == null) return
        if (!projectDir.isDirectory) return

        // ⭐ اكتشاف نوع المشروع تلقائياً
        val isFlutter = isFlutterProject(projectDir)
        val gradleRoot: File = if (isFlutter) {
            // Flutter: android/ هو الجذر لـ Gradle
            val androidDir = File(projectDir, "android")
            if (!androidDir.isDirectory) return
            androidDir
        } else {
            // Java: الجذر مباشرة
            projectDir
        }

        val wrapperDir = File(gradleRoot, "gradle/wrapper")
        if (!wrapperDir.exists() && !wrapperDir.mkdirs()) return

        // 1) نسخ gradle-wrapper.jar
        val jarFile = File(wrapperDir, JAR_NAME)
        if (!isValidJar(jarFile)) {
            copyAssetToFile(context, "$ASSETS_WRAPPER_DIR/$JAR_NAME", jarFile)
        }

        // 2) كتابة gradle-wrapper.properties
        val propsFile = File(wrapperDir, "gradle-wrapper.properties")
        writeTextFileQuietly(propsFile, buildGradleWrapperProperties())

        // 3) كتابة gradlew
        val gradlewFile = File(gradleRoot, "gradlew")
        if (!gradlewFile.exists() || gradlewFile.length() < 100) {
            writeTextFileQuietly(gradlewFile, buildGradlew())
            try {
                gradlewFile.setExecutable(true, false)
            } catch (_: Exception) {
            }
        }

        // 4) كتابة gradlew.bat
        val gradlewBatFile = File(gradleRoot, "gradlew.bat")
        if (!gradlewBatFile.exists() || gradlewBatFile.length() < 100) {
            writeTextFileQuietly(gradlewBatFile, buildGradlewBat())
        }
    }

    // ============================================================
    // اكتشاف نوع المشروع
    // ============================================================
    private fun isFlutterProject(projectDir: File): Boolean {
        // Flutter: pubspec.yaml موجود في الجذر
        if (File(projectDir, "pubspec.yaml").isFile) return true

        // Flutter: مجلد android/ موجود مع build.gradle
        val androidDir = File(projectDir, "android")
        if (androidDir.isDirectory && File(androidDir, "build.gradle").isFile) {
            // تأكد أنه ليس Java (Java قد يستخدم android/ كاسم حزمة)
            if (!File(projectDir, "app/module.toml").isFile) {
                return true
            }
        }

        return false
    }

    // ============================================================
    // أدوات مساعدة
    // ============================================================
    private fun isValidJar(jarFile: File?): Boolean {
        if (jarFile == null || !jarFile.exists() || !jarFile.isFile) return false
        return jarFile.length() >= MIN_JAR_SIZE
    }

    private fun copyAssetToFile(context: Context, assetPath: String?, dest: File?) {
        if (assetPath == null || dest == null) return

        try {
            val parent = dest.parentFile
            if (parent != null && !parent.exists() && !parent.mkdirs()) return

            context.assets.open(assetPath).use { input ->
                FileOutputStream(dest).use { output ->
                    val buffer = ByteArray(8192)
                    var count: Int
                    while (input.read(buffer).also { count = it } != -1) {
                        output.write(buffer, 0, count)
                    }
                    output.flush()
                }
            }
        } catch (_: Exception) {
            try {
                if (dest.exists()) dest.delete()
            } catch (_: Exception) {
            }
        }
    }

    private fun writeTextFileQuietly(file: File?, content: String?) {
        if (file == null || content == null) return
        try {
            val parent = file.parentFile
            if (parent != null && !parent.exists() && !parent.mkdirs()) return

            FileWriter(file).use { writer ->
                writer.write(content)
                writer.flush()
            }
        } catch (_: Exception) {
        }
    }

    // ============================================================
    // ⭐ v3.0: gradle-wrapper.properties — 8.14
    // ============================================================
    private fun buildGradleWrapperProperties(): String =
        "distributionBase=GRADLE_USER_HOME\n" +
        "distributionPath=wrapper/dists\n" +
        "distributionUrl=https\\://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip\n" +
        "networkTimeout=10000\n" +
        "validateDistributionUrl=true\n" +
        "zipStoreBase=GRADLE_USER_HOME\n" +
        "zipStorePath=wrapper/dists\n"

    /**
     * النسخة الرسمية الكاملة من gradlew (POSIX-compliant).
     * مصدر: https://github.com/gradle/gradle/blob/master/gradlew
     */
    private fun buildGradlew(): String {
        val sb = StringBuilder()

        sb.append("#!/bin/sh\n")
        sb.append("\n")
        sb.append("#\n")
        sb.append("# Copyright © 2015-2021 the original authors.\n")
        sb.append("#\n")
        sb.append("# Licensed under the Apache License, Version 2.0 (the \"License\");\n")
        sb.append("# you may not use this file except in compliance with the License.\n")
        sb.append("# You may obtain a copy of the License at\n")
        sb.append("#\n")
        sb.append("#      https://www.apache.org/licenses/LICENSE-2.0\n")
        sb.append("#\n")
        sb.append("# Unless required by applicable law or agreed to in writing, software\n")
        sb.append("# distributed under the License is distributed on an \"AS IS\" BASIS,\n")
        sb.append("# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\n")
        sb.append("# See the License for the specific language governing permissions and\n")
        sb.append("# limitations under the License.\n")
        sb.append("#\n")
        sb.append("# SPDX-License-Identifier: Apache-2.0\n")
        sb.append("#\n")
        sb.append("\n")
        sb.append("##############################################################################\n")
        sb.append("#\n")
        sb.append("#   Gradle start up script for POSIX generated by Gradle.\n")
        sb.append("#\n")
        sb.append("#   Important for running:\n")
        sb.append("#\n")
        sb.append("#   (1) You need a POSIX-compliant shell to run this script. If your /bin/sh is\n")
        sb.append("#       noncompliant, but you have some other compliant shell such as ksh or\n")
        sb.append("#       bash, then to run this script, type that shell name before the whole\n")
        sb.append("#       command line, like:\n")
        sb.append("#\n")
        sb.append("#           ksh Gradle\n")
        sb.append("#\n")
        sb.append("#       Busybox and similar reduced shells will NOT work, because this script\n")
        sb.append("#       requires all of these POSIX shell features:\n")
        sb.append("#         * functions;\n")
        sb.append("#         * expansions «\$var», «\${var}», «\${var:-default}», «\${var+SET}»,\n")
        sb.append("#           «\${var#prefix}», «\${var%suffix}», and «\$( cmd )»;\n")
        sb.append("#         * compound commands having a testable exit status, especially «case»;\n")
        sb.append("#         * various built-in commands including «command», «set», and «ulimit».\n")
        sb.append("#\n")
        sb.append("#   Important for patching:\n")
        sb.append("#\n")
        sb.append("#   (2) This script targets any POSIX shell, so it avoids extensions provided\n")
        sb.append("#       by Bash, Ksh, etc; in particular arrays are avoided.\n")
        sb.append("#\n")
        sb.append("#       The \"traditional\" practice of packing multiple parameters into a\n")
        sb.append("#       space-separated string is a well documented source of bugs and security\n")
        sb.append("#       problems, so this is (mostly) avoided, by progressively accumulating\n")
        sb.append("#       options in \"\$@\", and eventually passing that to Java.\n")
        sb.append("#\n")
        sb.append("#       Where the inherited environment variables (DEFAULT_JVM_OPTS, JAVA_OPTS,\n")
        sb.append("#       and GRADLE_OPTS) rely on word-splitting, this is performed explicitly;\n")
        sb.append("#       see the in-line comments for details.\n")
        sb.append("#\n")
        sb.append("#       There are tweaks for specific operating systems such as AIX, CygWin,\n")
        sb.append("#       Darwin, MinGW, and NonStop.\n")
        sb.append("#\n")
        sb.append("#   (3) This script is generated from the Groovy template\n")
        sb.append("#       https://github.com/gradle/gradle/blob/master/platforms/jvm/plugins-application/src/main/resources/org/gradle/api/internal/plugins/unixStartScript.txt\n")
        sb.append("#       within the Gradle project.\n")
        sb.append("#\n")
        sb.append("#       You can find Gradle at https://github.com/gradle/gradle/.\n")
        sb.append("#\n")
        sb.append("##############################################################################\n")
        sb.append("\n")
        sb.append("# Attempt to set APP_HOME\n")
        sb.append("\n")
        sb.append("# Resolve links: \$0 may be a link\n")
        sb.append("app_path=\$0\n")
        sb.append("\n")
        sb.append("# Need this for daisy-chained symlinks.\n")
        sb.append("while\n")
        sb.append("    APP_HOME=\${app_path%\"\${app_path##*/}\"}  # leaves a trailing /; empty if no leading path\n")
        sb.append("    [ -h \"\$app_path\" ]\n")
        sb.append("do\n")
        sb.append("    ls=\$( ls -ld \"\$app_path\" )\n")
        sb.append("    link=\${ls#*' -> '}\n")
        sb.append("    case \$link in             #(\n")
        sb.append("      /*)   app_path=\$link ;; #(\n")
        sb.append("      *)    app_path=\$APP_HOME\$link ;;\n")
        sb.append("    esac\n")
        sb.append("done\n")
        sb.append("\n")
        sb.append("# This is normally unused\n")
        sb.append("# shellcheck disable=SC2034\n")
        sb.append("APP_BASE_NAME=\${0##*/}\n")
        sb.append("# Discard cd standard output in case \$CDPATH is set (https://github.com/gradle/gradle/issues/25036)\n")
        sb.append("APP_HOME=\$( cd -P \"\${APP_HOME:-./}\" > /dev/null && printf '%s\\n' \"\$PWD\" ) || exit\n")
        sb.append("\n")
        sb.append("# Use the maximum available, or set MAX_FD != -1 to use that value.\n")
        sb.append("MAX_FD=maximum\n")
        sb.append("\n")
        sb.append("warn () {\n")
        sb.append("    echo \"\$*\"\n")
        sb.append("} >&2\n")
        sb.append("\n")
        sb.append("die () {\n")
        sb.append("    echo\n")
        sb.append("    echo \"\$*\"\n")
        sb.append("    echo\n")
        sb.append("    exit 1\n")
        sb.append("} >&2\n")
        sb.append("\n")
        sb.append("# OS specific support (must be 'true' or 'false').\n")
        sb.append("cygwin=false\n")
        sb.append("msys=false\n")
        sb.append("darwin=false\n")
        sb.append("nonstop=false\n")
        sb.append("case \"\$( uname )\" in                #(\n")
        sb.append("  CYGWIN* )         cygwin=true  ;; #(\n")
        sb.append("  Darwin* )         darwin=true  ;; #(\n")
        sb.append("  MSYS* | MINGW* )  msys=true    ;; #(\n")
        sb.append("  NONSTOP* )        nonstop=true ;;\n")
        sb.append("esac\n")
        sb.append("\n")
        sb.append("CLASSPATH=\\\"\\\"\n")
        sb.append("\n")
        sb.append("\n")
        sb.append("# Determine the Java command to use to start the JVM.\n")
        sb.append("if [ -n \"\$JAVA_HOME\" ] ; then\n")
        sb.append("    if [ -x \"\$JAVA_HOME/jre/sh/java\" ] ; then\n")
        sb.append("        # IBM's JDK on AIX uses strange locations for the executables\n")
        sb.append("        JAVACMD=\$JAVA_HOME/jre/sh/java\n")
        sb.append("    else\n")
        sb.append("        JAVACMD=\$JAVA_HOME/bin/java\n")
        sb.append("    fi\n")
        sb.append("    if [ ! -x \"\$JAVACMD\" ] ; then\n")
        sb.append("        die \"ERROR: JAVA_HOME is set to an invalid directory: \$JAVA_HOME\n")
        sb.append("\n")
        sb.append("Please set the JAVA_HOME variable in your environment to match the\n")
        sb.append("location of your Java installation.\"\n")
        sb.append("    fi\n")
        sb.append("else\n")
        sb.append("    JAVACMD=java\n")
        sb.append("    if ! command -v java >/dev/null 2>&1\n")
        sb.append("    then\n")
        sb.append("        die \"ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.\n")
        sb.append("\n")
        sb.append("Please set the JAVA_HOME variable in your environment to match the\n")
        sb.append("location of your Java installation.\"\n")
        sb.append("    fi\n")
        sb.append("fi\n")
        sb.append("\n")
        sb.append("# Increase the maximum file descriptors if we can.\n")
        sb.append("if ! \"\$cygwin\" && ! \"\$darwin\" && ! \"\$nonstop\" ; then\n")
        sb.append("    case \$MAX_FD in #(\n")
        sb.append("      max*)\n")
        sb.append("        # In POSIX sh, ulimit -H is undefined. That's why the result is checked to see if it worked.\n")
        sb.append("        # shellcheck disable=SC2039,SC3045\n")
        sb.append("        MAX_FD=\$( ulimit -H -n ) ||\n")
        sb.append("            warn \"Could not query maximum file descriptor limit\"\n")
        sb.append("    esac\n")
        sb.append("    case \$MAX_FD in  #(\n")
        sb.append("      '' | soft) :;; #(\n")
        sb.append("      *)\n")
        sb.append("        # In POSIX sh, ulimit -n is undefined. That's why the result is checked to see if it worked.\n")
        sb.append("        # shellcheck disable=SC2039,SC3045\n")
        sb.append("        ulimit -n \"\$MAX_FD\" ||\n")
        sb.append("            warn \"Could not set maximum file descriptor limit to \$MAX_FD\"\n")
        sb.append("    esac\n")
        sb.append("fi\n")
        sb.append("\n")
        sb.append("# Collect all arguments for the java command, stacking in reverse order:\n")
        sb.append("#   * args from the command line\n")
        sb.append("#   * the main class name\n")
        sb.append("#   * -classpath\n")
        sb.append("#   * -D...appname settings\n")
        sb.append("#   * --module-path (only if needed)\n")
        sb.append("#   * DEFAULT_JVM_OPTS, JAVA_OPTS, and GRADLE_OPTS environment variables.\n")
        sb.append("\n")
        sb.append("# For Cygwin or MSYS, switch paths to Windows format before running java\n")
        sb.append("if \"\$cygwin\" || \"\$msys\" ; then\n")
        sb.append("    APP_HOME=\$( cygpath --path --mixed \"\$APP_HOME\" )\n")
        sb.append("    CLASSPATH=\$( cygpath --path --mixed \"\$CLASSPATH\" )\n")
        sb.append("\n")
        sb.append("    JAVACMD=\$( cygpath --unix \"\$JAVACMD\" )\n")
        sb.append("\n")
        sb.append("    # Now convert the arguments - kludge to limit ourselves to /bin/sh\n")
        sb.append("    for arg do\n")
        sb.append("        if\n")
        sb.append("            case \$arg in                                #(\n")
        sb.append("              -*)   false ;;                            # don't mess with options #(\n")
        sb.append("              /?*)  t=\${arg#/} t=/\${t%%/*}              # looks like a POSIX filepath\n")
        sb.append("                    [ -e \"\$t\" ] ;;                      #(\n")
        sb.append("              *)    false ;;\n")
        sb.append("            esac\n")
        sb.append("        then\n")
        sb.append("            arg=\$( cygpath --path --ignore --mixed \"\$arg\" )\n")
        sb.append("        fi\n")
        sb.append("        # Roll the args list around exactly as many times as the number of\n")
        sb.append("        # args, so each arg winds up back in the position where it started, but\n")
        sb.append("        # possibly modified.\n")
        sb.append("        #\n")
        sb.append("        # NB: a `for` loop captures its iteration list before it begins, so\n")
        sb.append("        # changing the positional parameters here affects neither the number of\n")
        sb.append("        # iterations, nor the values presented in `arg`.\n")
        sb.append("        shift                   # remove old arg\n")
        sb.append("        set -- \"\$@\" \"\$arg\"      # push replacement arg\n")
        sb.append("    done\n")
        sb.append("fi\n")
        sb.append("\n")
        sb.append("\n")
        sb.append("# Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.\n")
        sb.append("DEFAULT_JVM_OPTS='\"-Xmx64m\" \"-Xms64m\"'\n")
        sb.append("\n")
        sb.append("# Collect all arguments for the java command:\n")
        sb.append("#   * DEFAULT_JVM_OPTS, JAVA_OPTS, and optsEnvironmentVar are not allowed to contain shell fragments,\n")
        sb.append("#     and any embedded shellness will be escaped.\n")
        sb.append("#   * For example: A user cannot expect \${Hostname} to be expanded, as it is an environment variable and will be\n")
        sb.append("#     treated as '\${Hostname}' itself on the command line.\n")
        sb.append("\n")
        sb.append("set -- \\\n")
        sb.append("        \"-Dorg.gradle.appname=\$APP_BASE_NAME\" \\\n")
        sb.append("        -classpath \"\$CLASSPATH\" \\\n")
        sb.append("        -jar \"\$APP_HOME/gradle/wrapper/gradle-wrapper.jar\" \\\n")
        sb.append("        \"\$@\"\n")
        sb.append("\n")
        sb.append("# Stop when \"xargs\" is not available.\n")
        sb.append("if ! command -v xargs >/dev/null 2>&1\n")
        sb.append("then\n")
        sb.append("    die \"xargs is not available\"\n")
        sb.append("fi\n")
        sb.append("\n")
        sb.append("# Use \"xargs\" to parse quoted args.\n")
        sb.append("#\n")
        sb.append("# With -n1 it outputs one arg per line, with the quotes and backslashes removed.\n")
        sb.append("#\n")
        sb.append("# In Bash we could simply go:\n")
        sb.append("#\n")
        sb.append("#   readarray ARGS < <( xargs -n1 <<<\"\$var\" ) &&\n")
        sb.append("#   set -- \"\${ARGS[@]}\" \"\$@\"\n")
        sb.append("#\n")
        sb.append("# but POSIX shell has neither arrays nor command substitution, so instead we\n")
        sb.append("# post-process each arg (as a line of input to sed) to backslash-escape any\n")
        sb.append("# character that might be a shell metacharacter, then use eval to reverse\n")
        sb.append("# that process (while maintaining the separation between arguments), and wrap\n")
        sb.append("# the whole thing up as a single \"set\" statement.\n")
        sb.append("#\n")
        sb.append("# This will of course break if any of these variables contains a newline or\n")
        sb.append("# an unmatched quote.\n")
        sb.append("#\n")
        sb.append("\n")
        sb.append("eval \"set -- \$(\n")
        sb.append("        printf '%s\\n' \"\$DEFAULT_JVM_OPTS \$JAVA_OPTS \$GRADLE_OPTS\" |\n")
        sb.append("        xargs -n1 |\n")
        sb.append("        sed ' s~[^-[:alnum:]+,./:=@_]~\\\\&~g; ' |\n")
        sb.append("        tr '\\n' ' '\n")
        sb.append("    )\" '\"\$@\"'\n")
        sb.append("\n")
        sb.append("exec \"\$JAVACMD\" \"\$@\"\n")

        return sb.toString()
    }

    private fun buildGradlewBat(): String {
        val sb = StringBuilder()
        sb.append("@rem\r\n")
        sb.append("@rem Copyright 2015 the original author or authors.\r\n")
        sb.append("@rem\r\n")
        sb.append("@rem Licensed under the Apache License, Version 2.0 (the \"License\");\r\n")
        sb.append("@rem you may not use this file except in compliance with the License.\r\n")
        sb.append("@rem You may obtain a copy of the License at\r\n")
        sb.append("@rem\r\n")
        sb.append("@rem      https://www.apache.org/licenses/LICENSE-2.0\r\n")
        sb.append("@rem\r\n")
        sb.append("@rem Unless required by applicable law or agreed to in writing, software\r\n")
        sb.append("@rem distributed under the License is distributed on an \"AS IS\" BASIS,\r\n")
        sb.append("@rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\r\n")
        sb.append("@rem See the License for the specific language governing permissions and\r\n")
        sb.append("@rem limitations under the License.\r\n")
        sb.append("@rem\r\n")
        sb.append("@rem SPDX-License-Identifier: Apache-2.0\r\n")
        sb.append("@rem\r\n")
        sb.append("\r\n")
        sb.append("@if \"%DEBUG%\"==\"\" @echo off\r\n")
        sb.append("@rem ##########################################################################\r\n")
        sb.append("@rem\r\n")
        sb.append("@rem  Gradle startup script for Windows\r\n")
        sb.append("@rem\r\n")
        sb.append("@rem ##########################################################################\r\n")
        sb.append("\r\n")
        sb.append("@rem Set local scope for the variables with windows NT shell\r\n")
        sb.append("if \"%OS%\"==\"Windows_NT\" setlocal\r\n")
        sb.append("\r\n")
        sb.append("set DIRNAME=%~dp0\r\n")
        sb.append("if \"%DIRNAME%\"==\"\" set DIRNAME=.\r\n")
        sb.append("@rem This is normally unused\r\n")
        sb.append("set APP_BASE_NAME=%~n0\r\n")
        sb.append("set APP_HOME=%DIRNAME%\r\n")
        sb.append("\r\n")
        sb.append("@rem Resolve any \".\" and \"..\" in APP_HOME to make it shorter.\r\n")
        sb.append("for %%i in (\"%APP_HOME%\") do set APP_HOME=%%~fi\r\n")
        sb.append("\r\n")
        sb.append("@rem Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.\r\n")
        sb.append("set DEFAULT_JVM_OPTS=\"-Xmx64m\" \"-Xms64m\"\r\n")
        sb.append("\r\n")
        sb.append("@rem Find java.exe\r\n")
        sb.append("if defined JAVA_HOME goto findJavaFromJavaHome\r\n")
        sb.append("\r\n")
        sb.append("set JAVA_EXE=java.exe\r\n")
        sb.append("%JAVA_EXE% -version >NUL 2>&1\r\n")
        sb.append("if %ERRORLEVEL% equ 0 goto execute\r\n")
        sb.append("\r\n")
        sb.append("echo. 1>&2\r\n")
        sb.append("echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH. 1>&2\r\n")
        sb.append("echo. 1>&2\r\n")
        sb.append("echo Please set the JAVA_HOME variable in your environment to match the 1>&2\r\n")
        sb.append("echo location of your Java installation. 1>&2\r\n")
        sb.append("\r\n")
        sb.append("goto fail\r\n")
        sb.append("\r\n")
        sb.append(":findJavaFromJavaHome\r\n")
        sb.append("set JAVA_HOME=%JAVA_HOME:\"=%\r\n")
        sb.append("set JAVA_EXE=%JAVA_HOME%/bin/java.exe\r\n")
        sb.append("\r\n")
        sb.append("if exist \"%JAVA_EXE%\" goto execute\r\n")
        sb.append("\r\n")
        sb.append("echo. 1>&2\r\n")
        sb.append("echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME% 1>&2\r\n")
        sb.append("echo. 1>&2\r\n")
        sb.append("echo Please set the JAVA_HOME variable in your environment to match the 1>&2\r\n")
        sb.append("echo location of your Java installation. 1>&2\r\n")
        sb.append("\r\n")
        sb.append("goto fail\r\n")
        sb.append("\r\n")
        sb.append(":execute\r\n")
        sb.append("@rem Setup the command line\r\n")
        sb.append("\r\n")
        sb.append("set CLASSPATH=\r\n")
        sb.append("\r\n")
        sb.append("@rem Execute Gradle\r\n")
        sb.append("\"%JAVA_EXE%\" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% \"-Dorg.gradle.appname=%APP_BASE_NAME%\" -classpath \"%CLASSPATH%\" -jar \"%APP_HOME%\\gradle\\wrapper\\gradle-wrapper.jar\" %*\r\n")
        sb.append("\r\n")
        sb.append(":end\r\n")
        sb.append("@rem End local scope for the variables with windows NT shell\r\n")
        sb.append("if %ERRORLEVEL% equ 0 goto mainEnd\r\n")
        sb.append("\r\n")
        sb.append(":fail\r\n")
        sb.append("rem Set variable GRADLE_EXIT_CONSOLE if you need the _script_ return code instead of\r\n")
        sb.append("rem the _cmd.exe /c_ return code!\r\n")
        sb.append("set EXIT_CODE=%ERRORLEVEL%\r\n")
        sb.append("if %EXIT_CODE% equ 0 set EXIT_CODE=1\r\n")
        sb.append("if not \"\"==\"%GRADLE_EXIT_CONSOLE%\" exit %EXIT_CODE%\r\n")
        sb.append("exit /b %EXIT_CODE%\r\n")
        sb.append("\r\n")
        sb.append(":mainEnd\r\n")
        sb.append("if \"%OS%\"==\"Windows_NT\" endlocal\r\n")
        sb.append("\r\n")
        sb.append(":omega\r\n")
        return sb.toString()
    }
}