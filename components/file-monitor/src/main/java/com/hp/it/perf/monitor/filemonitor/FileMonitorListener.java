package com.hp.it.perf.monitor.filemonitor;

import java.util.EventListener;

public interface FileMonitorListener extends EventListener {

	public void onChanged(FileMonitorEvent event);

}
