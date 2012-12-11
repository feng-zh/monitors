package com.hp.it.perf.monitor.filemonitor;

import java.io.Closeable;
import java.io.File;
import java.util.concurrent.TimeUnit;

public interface FileMonitorKey extends Closeable {

	long getTickCount();

	void await(long tickCount) throws InterruptedException;

	// true if get result, false if timeout
	boolean await(long tickCount, long timeout, TimeUnit unit)
			throws InterruptedException;

	void close();

	long getLastUpdated();

	long getLength();

	File getCurrentFile();

	void addMonitorListener(FileMonitorListener fileMonitorListener);

	void removeMonitorListener(FileMonitorListener fileMonitorListener);

}
