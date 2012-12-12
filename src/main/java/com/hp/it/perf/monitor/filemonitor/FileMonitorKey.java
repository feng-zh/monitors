package com.hp.it.perf.monitor.filemonitor;

import java.io.Closeable;
import java.io.File;

public interface FileMonitorKey extends Closeable {

	void close();

	long getLastUpdated();

	long getLength();

	File getCurrentFile();

	void addMonitorListener(FileMonitorListener fileMonitorListener);

	void removeMonitorListener(FileMonitorListener fileMonitorListener);

}
