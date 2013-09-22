#!/bin/sh

PWD=`pwd`
case $0 in
/* )    MST_BIN_DIR=$0;;
./* )   MST_BIN_DIR=$PWD/${0##./};;
* )     MST_BIN_DIR=$PWD/$0;;
esac
MST_BIN_DIR=${MST_BIN_DIR%/*}
echo "MST_BIN_DIR=${MST_BIN_DIR}"

classpath=$MST_BIN_DIR/patch

if [ -e "$MST_BIN_DIR/patch" ]; then
for file in "`ls $MST_BIN_DIR/patch/*.jar`"; do
        classpath=$classpath:$file
done
fi

for file in $MST_BIN_DIR/lib/*.jar; do
        classpath=$classpath:$file
done

export CLASSPATH=$classpath

echo "CLASSPATH: $CLASSPATH"

echo "JAVA_OPTS: $JAVA_OPTS"

/usr/java/latest/bin/java -Dmonitor.nio.slow=true $JAVA_OPTS com.hp.it.perf.monitor.files.hub.FilesHubMain "$@"
