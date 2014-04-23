export MAVEN_OPTS="$MAVEN_OPTS -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=4000"
export MAVEN_OPTS="$MAVEN_OPTS -Djava.library.path=/lib64:/usr/local/lib/jni"
export MAVEN_OPTS="$MAVEN_OPTS -XX:MaxPermSize=512m"
export JAVA_HOME="/usr/lib/jvm/java-1.7.0-openjdk.x86_64"

mvn jetty:run -Djetty.port=8084
