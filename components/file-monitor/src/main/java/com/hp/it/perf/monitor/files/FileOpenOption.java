package com.hp.it.perf.monitor.files;

public interface FileOpenOption {

	public boolean openOnTail();

	public boolean lazyOpen();

	public boolean monitor();

}
