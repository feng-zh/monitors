package com.hp.it.perf.monitor.filemonitor;

import java.io.File;

public interface FileMonitorService {

	FileMonitorKey register(File file, FileMonitorMode... modes);

}
