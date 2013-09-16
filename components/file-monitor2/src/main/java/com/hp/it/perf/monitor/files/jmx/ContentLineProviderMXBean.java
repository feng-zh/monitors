package com.hp.it.perf.monitor.files.jmx;

import java.io.IOException;

public interface ContentLineProviderMXBean {

	public String[] getFileNames();

	public int getReadLineCount();

	public long getReadByteCount();

	public void close() throws IOException;

	public void setCompressMode(boolean mode);

	public boolean isCompressMode();

	public void setNotificationEnabled(boolean enabled);

	public boolean isNotificationEnabled();

}
