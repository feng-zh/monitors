package com.hp.it.perf.monitor.files;

import java.net.URL;

public interface FileMetadata {

	public String getName();

	public String getPath();

	public String getRealPath();

	public boolean isPackaged();

	public long getLastModifiedDate();

	public String getLength();

	public URL toURL();

}
