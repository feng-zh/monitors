package com.hp.it.perf.monitor.filemonitor;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;

public interface FileMonitorService extends Closeable {

	public FileMonitorKey singleRegister(File file, FileMonitorMode mode)
			throws IOException, IllegalStateException;

	public FileMonitorKey folderRegister(File file, FileMonitorMode mode)
			throws IOException, IllegalStateException;
	
	public Object getKeyByFile(File file) throws IOException;

}
