package com.hp.it.perf.monitor.files.nio;

import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.util.List;

import com.hp.it.perf.monitor.files.FileInstance;

interface FileKeyDetector {

	static class WatchEventKeys {
		public final WatchEvent<?> event;
		public FileKey previousFileKey;
		public FileKey currentFileKey;
		public Path previousPath;
		public Path currentPath;
		FileInstance previousInstance;
		FileInstance currentInstance;

		public WatchEventKeys(WatchEvent<?> event, FileKey preFileKey,
				Path prevPath, FileKey currFileKey, Path currPath) {
			this.event = event;
			this.previousFileKey = preFileKey;
			this.currentFileKey = currFileKey;
			this.previousPath = prevPath;
			this.currentPath = currPath;
		}
	}

	public FileKey detectFileKey(Path path);

	public List<WatchEventKeys> detectWatchEvents(List<WatchEvent<?>> events);

}
