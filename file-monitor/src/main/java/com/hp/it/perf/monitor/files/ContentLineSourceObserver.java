package com.hp.it.perf.monitor.files;

public interface ContentLineSourceObserver {

	public void sourceFileCreated(FileInstance file, Object provider);

	public void sourceFileDeleted(FileInstance file, Object provider);

}
