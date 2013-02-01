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

	public final static int EOF = -1;

	public final static int QUEUE_FULL = -2;

	// wait operation
	public LineRecord readLine() throws IOException, InterruptedException;

	public LineRecord readLine(long timeout, TimeUnit unit) throws IOException,
			InterruptedException, EOFException;

	// no-wait operation
	// >0 not full with increased size
	// -1(EOF) if EOF
	// -2(QUEUE_FULL) if FULL
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
	public List<FileContentInfo> getFileContentInfos(boolean realtime,
			boolean actived) throws IOException;

}
