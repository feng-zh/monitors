package com.hp.it.perf.monitor.filemonitor;

import java.io.File;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class FileMonitorWatchKeyImpl implements FileMonitorKey {

	private WatchKey watchKey;

	private List<FileMonitorListener> listeners = new CopyOnWriteArrayList<FileMonitorListener>();

	FileMonitorWatchKeyImpl(WatchKey watchKey) {
		this.watchKey = watchKey;
	}

	@Override
	public void close() {
		throw new UnsupportedOperationException();
	}

	@Override
	public long getLastUpdated() {
		throw new UnsupportedOperationException();
	}

	@Override
	public long getLength() {
		throw new UnsupportedOperationException();
	}

	@Override
	public File getCurrentFile() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void addMonitorListener(FileMonitorListener listener) {
		listeners.add(listener);
	}

	@Override
	public void removeMonitorListener(FileMonitorListener listener) {
		listeners.remove(listener);
	}

	void processEvent(List<WatchEvent<?>> events) {
		FileMonitorEvent event = new FileMonitorEvent(this);
		onChanged(event);
	}

	protected void onChanged(FileMonitorEvent event) {
		for (FileMonitorListener listener : listeners) {
			listener.onChanged(event);
		}
	}

}
