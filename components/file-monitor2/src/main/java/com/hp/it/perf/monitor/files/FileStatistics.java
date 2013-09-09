package com.hp.it.perf.monitor.files;

public interface FileStatistics {

	public interface Count {
		public int get();
	}

	public Count fileInstanceCount();

	public Count fileSetCount();

	public Count fileClusterCount();

	public Count ioReaderCount();

	public Count ioResourceCount();

}
