package com.hp.it.perf.monitor.filemonitor;

import java.io.IOException;
import java.util.List;


public interface CompositeContentProviderMXBean {

	public String[] getFileNames();

	public List<FileContentInfo> getFileContentInfos(boolean realtime)
			throws IOException;

	public void close() throws IOException;
}
