package com.hp.it.perf.monitor.files;

public interface FileInstance extends FileInstanceChangeAware {

	public String getName();

	public FileCluster getFileCluster();

	// may not related to file set if not support indexing (like web)
	public FileSet getFileSet();

	public FileMetadata getMetadata(boolean refresh);

	// for easy tracking
	// if property is null, remove it
	public void putClientProperty(Object key, Object property);

	public Object getClientProperty(Object key);

}
