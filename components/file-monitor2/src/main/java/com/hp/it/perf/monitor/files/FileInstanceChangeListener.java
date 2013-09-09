package com.hp.it.perf.monitor.files;

public interface FileInstanceChangeListener {

	public void onFileInstanceCreated(FileInstance instance);

	public void onFileInstanceDeleted(FileInstance instance);

	public void onFileInstanceRenamed(FileInstance oldInstance, FileInstance newInstance);

	public void onFileInstancePackaged(FileInstance oldInstance, FileInstance newInstance);

}
