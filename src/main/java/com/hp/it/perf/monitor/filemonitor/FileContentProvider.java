package com.hp.it.perf.monitor.filemonitor;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.util.List;
import java.util.Observer;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public interface FileContentProvider extends Closeable {

	public final static AtomicLong providerIdSeed = new AtomicLong();

	// wait operation
	public LineRecord readLine() throws IOException, InterruptedException;

	public LineRecord readLine(long timeout, TimeUnit unit) throws IOException,
			InterruptedException, EOFException;

	// no-wait operation
	// -1 if EOF
	public int readLines(Queue<LineRecord> list, int maxSize)
			throws IOException;

	// life-cycle
	public void init() throws IOException;

	public void close() throws IOException;

	// change listener
	public void addUpdateObserver(Observer observer);

	public void removeUpdateObserver(Observer observer);

	// information
	// include providerId, original name, last event, current name, file key
	public List<FileContentInfo> getFileContentInfos(boolean realtime)
			throws IOException;

}
