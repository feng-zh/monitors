package com.hp.it.perf.monitor.filemonitor;

import java.io.File;
import java.util.EventObject;

public class FileMonitorEvent extends EventObject {

	private static final long serialVersionUID = 1L;

	private File monitorFile;

	private FileMonitorMode monitorMode;

	private File changedFile;

	private FileKey changedFileKey;

	private FileMonitorKey monitorKey;

	private final long tickNumber;

	public FileMonitorEvent(Object source, long tickNumber) {
		super(source);
		this.tickNumber = tickNumber;
	}

	public void setMonitorKey(FileMonitorKey monitorKey) {
		this.monitorKey = monitorKey;
	}

	// may null (deleted)
	// may different name (renamed)
	public File getChangedFile() {
		return changedFile;
	}

	public FileMonitorMode getMode() {
		return monitorMode;
	}

	public File getMonitorFile() {
		return monitorFile;
	}

	public FileMonitorKey getMonitorKey() {
		return monitorKey;
	}

	public void setChangedFile(File changedFile) {
		this.changedFile = changedFile;
	}

	public void setMonitorFile(File monitorFile) {
		this.monitorFile = monitorFile;
	}

	public void setMode(FileMonitorMode monitorMode) {
		this.monitorMode = monitorMode;
	}

	public FileKey getChangedFileKey() {
		return changedFileKey;
	}

	public void setChangedFileKey(FileKey changedFileKey) {
		this.changedFileKey = changedFileKey;
	}

	public long getTickNumber() {
		return tickNumber;
	}

}
