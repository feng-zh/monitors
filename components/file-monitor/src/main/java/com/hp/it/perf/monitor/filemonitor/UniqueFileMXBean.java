package com.hp.it.perf.monitor.filemonitor;

import java.io.IOException;
import java.util.List;


public interface UniqueFileMXBean {

	public String getFileName();

	public List<FileContentInfo> getFileContentInfos(boolean realtime)
			throws IOException;
	
	public int getReadLineCount();
	
	public long getReadByteCount();
	
	public void setCompressMode(boolean mode);
	
	public boolean isCompressMode();
	
	public void setNotificationEnabled(boolean enabled);
	
	public boolean isNotificationEnabled();
	
	public void close() throws IOException;
}
