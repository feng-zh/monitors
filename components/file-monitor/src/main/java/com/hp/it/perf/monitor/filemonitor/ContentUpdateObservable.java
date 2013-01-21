package com.hp.it.perf.monitor.filemonitor;

import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.atomic.AtomicInteger;

public class ContentUpdateObservable extends Observable implements
		FileMonitorListener, Observer {

	private final FileContentProvider provider;

	private static AtomicInteger sequence = new AtomicInteger();

	private int index;

	public ContentUpdateObservable(FileContentProvider provider) {
		this.provider = provider;
		this.index = sequence.incrementAndGet();
	}

	public FileContentProvider getProvider() {
		return provider;
	}

	@Override
	public void onChanged(FileMonitorEvent event) {
		setChanged();
		notifyObservers(event.getTickNumber());
	}

	int getIndex() {
		return index;
	}

	@Override
	public void update(Observable o, Object arg) {
		if (o == this) {
			throw new IllegalStateException("cycle observer dectected: " + o);
		}
		setChanged();
		notifyObservers(arg);
	}

}
