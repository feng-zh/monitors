package example;

import java.io.File;
import java.util.EventObject;

public class FileMonitorEvent extends EventObject {

	private static final long serialVersionUID = 1L;

	private File monitorFile;

	private FileMonitorMode monitorMode;

	private File changedFile;

	public FileMonitorEvent(FileMonitorKey source) {
		super(source);
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
		return (FileMonitorKey) source;
	}

	protected void setChangedFile(File changedFile) {
		this.changedFile = changedFile;
	}

	protected void setMonitorFile(File monitorFile) {
		this.monitorFile = monitorFile;
	}

	protected void setMonitorMode(FileMonitorMode monitorMode) {
		this.monitorMode = monitorMode;
	}

}
