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

echo "CLASSPATH: $classpath"

/usr/java/latest/bin/java -classpath $classpath -Dmonitor.nio.slow=true com.hp.it.perf.monitor.filemonitor.example.FileMonitorMain "$@"
