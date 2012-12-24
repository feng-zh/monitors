package com.hp.it.perf.monitor.filemonitor.nio;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.hp.it.perf.monitor.filemonitor.FileKey;
import com.hp.it.perf.monitor.filemonitor.FileMonitorEvent;
import com.hp.it.perf.monitor.filemonitor.FileMonitorKey;
import com.hp.it.perf.monitor.filemonitor.FileMonitorListener;
import com.hp.it.perf.monitor.filemonitor.FileMonitorMode;

class FileMonitorWatchKeyImpl implements FileMonitorKey {

	private List<FileMonitorListener> listeners = new CopyOnWriteArrayList<FileMonitorListener>();

	private WatchEntry watchEntry;

	private Path path;

	private FileKey fileKey;

	private FileMonitorMode mode;

	private long lastUpdated;

	FileMonitorWatchKeyImpl(WatchEntry watchEntry, Path path, FileKey fileKey,
			FileMonitorMode mode) {
		this.watchEntry = watchEntry;
		this.path = path;
		this.mode = mode;
		this.fileKey = fileKey;
	}

	Path getMonitorPath() {
		return path;
	}

	FileMonitorMode getMonitorMode() {
		return mode;
	}

	FileKey getMonitorFileKey() {
		return fileKey;
	}

	void setLastUpdated(long lastUpdated) {
		this.lastUpdated = lastUpdated;
	}

	@Override
	public void close() {
		watchEntry.removeFileMonitorKey(this);
	}

	@Override
	public long getLastUpdated() {
		return lastUpdated;
	}

	@Override
	public void addMonitorListener(FileMonitorListener listener) {
		listeners.add(listener);
	}

	@Override
	public void removeMonitorListener(FileMonitorListener listener) {
		listeners.remove(listener);
	}

	void processEvent(List<FileMonitorEvent> events) {
		for (FileMonitorEvent event : events) {
			onChanged(event);
		}
	}

	protected void onChanged(FileMonitorEvent event) {
		for (FileMonitorListener listener : listeners) {
			listener.onChanged(event);
		}
	}

}
