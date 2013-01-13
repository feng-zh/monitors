package com.hp.it.perf.monitor.filemonitor;

import java.io.IOException;
import java.util.List;

public interface FolderContentProviderMXBean {

	public String[] getFileNames();

	public String getFolderName();

	public List<FileContentInfo> getFileContentInfos(boolean realtime)
			throws IOException;
	
	public int getReadLineCount();
	
	public long getReadByteCount();

	public void close() throws IOException;

}
