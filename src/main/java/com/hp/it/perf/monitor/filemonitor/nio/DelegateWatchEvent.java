package com.hp.it.perf.monitor.filemonitor.nio;

import java.nio.file.Path;
import java.nio.file.WatchEvent;

class DelegateWatchEvent implements WatchEvent<Path> {

	private Kind<Path> kind;
	private WatchEvent<?> event;

	public DelegateWatchEvent(Kind<Path> kind, WatchEvent<?> event) {
		this.kind = kind;
		this.event = event;
	}

	@Override
	public Kind<Path> kind() {
		return kind;
	}

	@Override
	public int count() {
		return event.count();
	}

	@Override
	public Path context() {
		return (Path) event.context();
	}

}