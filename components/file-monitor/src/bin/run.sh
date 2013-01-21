#!/bin/sh

classpath=patch

if [ -e "patch" ]; then
for file in "`ls patch/*.jar`"; do
	classpath=$classpath:$file
done
fi

for file in lib/*.jar; do
	classpath=$classpath:$file
done

echo "CLASSPATH: $classpath"

/usr/java/latest/bin/java -classpath $classpath -Dmonitor.nio.slow=true com.hp.it.perf.monitor.filemonitor.example.FileMonitorMain "$@"
