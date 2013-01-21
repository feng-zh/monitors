package com.hp.it.perf.monitor.filemonitor;

import java.io.Closeable;

public interface FileMonitorKey extends Closeable {

	void close();

	long getLastUpdated();

	void addMonitorListener(FileMonitorListener fileMonitorListener);

	void removeMonitorListener(FileMonitorListener fileMonitorListener);

}
