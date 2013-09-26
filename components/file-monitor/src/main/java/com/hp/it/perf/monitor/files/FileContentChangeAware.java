package com.hp.it.perf.monitor.files;

public interface FileContentChangeAware {

	public void addFileContentChangeListener(FileContentChangeListener listener);

	public void removeFileContentChangeListener(
			FileContentChangeListener listener);

}
