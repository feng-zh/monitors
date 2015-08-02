package com.hp.it.perf.monitor.files;

public interface FileInstanceChangeAware {

	public void addFileInstanceChangeListener(
			FileInstanceChangeListener listener);

	public void removeFileInstanceChangeListener(
			FileInstanceChangeListener listener);

}
