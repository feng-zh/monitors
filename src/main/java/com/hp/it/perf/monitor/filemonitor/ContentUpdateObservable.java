package com.hp.it.perf.monitor.filemonitor;

import java.util.Observable;
import java.util.concurrent.atomic.AtomicInteger;

public class ContentUpdateObservable extends Observable implements
		FileMonitorListener {

	private final FileContentProvider provider;

	private static AtomicInteger sequence = new AtomicInteger();

	private final ContentUpdateChecker updateChecker;

	private int index;

	public ContentUpdateObservable(FileContentProvider provider) {
		this.provider = provider;
		this.index = sequence.incrementAndGet();
		this.updateChecker = new ContentUpdateChecker(this);
		addObserver(updateChecker);
	}

	public FileContentProvider getProvider() {
		return provider;
	}

	@Override
	public void onChanged(FileMonitorEvent event) {
		setChanged();
		notifyObservers(event.getTickCount());
	}

	int getIndex() {
		return index;
	}

	public ContentUpdateChecker getUpdateChecker() {
		return updateChecker;
	}

}
