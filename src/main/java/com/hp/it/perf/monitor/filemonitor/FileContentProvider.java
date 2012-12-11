package com.hp.it.perf.monitor.filemonitor;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

import example.FileContentInfo;

public interface FileContentProvider extends Closeable {

	// wait operation
	public LineRecord readLine() throws IOException, InterruptedException;

	public LineRecord readLine(long timeout, TimeUnit unit) throws IOException,
			InterruptedException, EOFException;

	// no-wait operation
	// -1 if EOF
	public int readLines(Queue<LineRecord> list) throws IOException;

	public void close() throws IOException;

	public List<FileContentInfo> getFileContentInfos() throws IOException;

}
