package com.hp.it.perf.monitor.filemonitor;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.util.List;
import java.util.Observer;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

public interface FileContentProvider extends Closeable {

	// wait operation
	public LineRecord readLine() throws IOException, InterruptedException;

	public LineRecord readLine(long timeout, TimeUnit unit) throws IOException,
			InterruptedException, EOFException;

	// no-wait operation
	// -1 if EOF
	public int readLines(Queue<LineRecord> list, int maxSize)
			throws IOException;

	public void init() throws IOException;

	public void close() throws IOException;

	public List<FileContentInfo> getFileContentInfos() throws IOException;

	public void addUpdateObserver(Observer observer);

	public void removeUpdateObserver(Observer observer);

	// // may not supported
	// public long skip(long bytes) throws IOException;
}
