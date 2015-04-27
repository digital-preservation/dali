#
# Copyright (c) 2013, The National Archives <digitalpreservation@nationalarchives.gov.uk>
# http://www.nationalarchives.gov.uk
#
# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at http://mozilla.org/MPL/2.0/.
#

export MAVEN_OPTS="$MAVEN_OPTS -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=9000"
export MAVEN_OPTS="$MAVEN_OPTS -Djava.library.path=/lib64:/usr/local/lib/jni"
export MAVEN_OPTS="$MAVEN_OPTS -XX:MaxPermSize=512m"
export JAVA_HOME="/usr/lib/jvm/java-1.7.0-openjdk.x86_64"

mvn jetty:run -Djetty.port=8084 -Dconfig.resource=/development.conf
