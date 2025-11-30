#!/usr/bin/env sh

export GRADLE_USER_HOME="$HOME/.gradle"

# Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
DEFAULT_JVM_OPTS="-Dfile.encoding=UTF-8"

# Find java.exe if it exists for windows support
if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/bin/java.exe" ] ; then
        # Windows JDK
        JAVACMD="$JAVA_HOME/bin/java.exe"
    else
        # Non-windows JDK
        JAVACMD="$JAVA_HOME/bin/java"
    fi
    if [ ! -x "$JAVACMD" ] ; then
        die "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME\n\nPlease set the JAVA_HOME variable in your environment to match the\nlocation of your Java installation."
    fi
else
    JAVACMD="java"
    which java >/dev/null 2>&1 || die "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.\n\nPlease set the JAVA_HOME variable in your environment to match the\nlocation of your Java installation."
fi

exec "$JAVACMD" $DEFAULT_JVM_OPTS "-Dorg.gradle.appname=gradlew" -classpath "$(dirname "$0")/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"