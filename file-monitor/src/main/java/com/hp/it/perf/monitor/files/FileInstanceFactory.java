package com.hp.it.perf.monitor.files;

import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;

public interface FileInstanceFactory extends Closeable {

	public FileInstance getFileInstance(String path) throws IOException,
			FileNotFoundException;

	public FileSet getFileSet(String path) throws IOException,
			FileNotFoundException;

	public void setFileClusterStrategy(FileClusterStrategy strategy);

	public void setGlobalFilenameFilter(FilenameFilter nameFilter);

	public FileStatistics getStatistics();

	public void close() throws IOException;

}
