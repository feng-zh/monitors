package com.hp.it.perf.monitor.files;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

public interface ContentLineStream extends Closeable {

	// null if no
	public ContentLine poll() throws IOException;

	// blocking operation
	public ContentLine take() throws IOException, InterruptedException;

	public ContentLine poll(long timeout, TimeUnit unit) throws IOException,
			InterruptedException, EOFException;

	// -1 if EOF
	public int drainTo(Collection<? super ContentLine> list, int maxSize)
			throws IOException;

	public void close() throws IOException;

}
