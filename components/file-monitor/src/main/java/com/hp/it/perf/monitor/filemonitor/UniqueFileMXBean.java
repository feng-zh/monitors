package com.hp.it.perf.monitor.filemonitor;

import java.io.IOException;
import java.util.List;


public interface UniqueFileMXBean {

	public String getFileName();

	public List<FileContentInfo> getFileContentInfos(boolean realtime)
			throws IOException;
	
	public int getReadLineCount();
	
	public long getReadByteCount();

	public void close() throws IOException;
}