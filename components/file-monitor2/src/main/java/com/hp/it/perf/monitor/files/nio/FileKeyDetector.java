package com.hp.it.perf.monitor.files.nio;

import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.util.List;

interface FileKeyDetector {

	static class WatchEventKeys {
		public final WatchEvent<?> event;
		public FileKey previousFileKey;
		public FileKey currentFileKey;

		public WatchEventKeys(WatchEvent<?> event, FileKey preFileKey,
				FileKey currFileKey) {
			this.event = event;
			this.previousFileKey = preFileKey;
			this.currentFileKey = currFileKey;
		}
	}

	public FileKey detectFileKey(Path path);

	public List<WatchEventKeys> detectWatchEvents(List<WatchEvent<?>> events);

}
