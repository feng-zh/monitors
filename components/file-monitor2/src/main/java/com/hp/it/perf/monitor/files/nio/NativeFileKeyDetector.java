package com.hp.it.perf.monitor.files.nio;

import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

class NativeFileKeyDetector implements FileKeyDetector {

	private PathKeyResolver currentPathKeyResolver;
	private Path watchPath;

	NativeFileKeyDetector(Path watchPath) {
		this.watchPath = watchPath;
		this.currentPathKeyResolver = new PathKeyResolver(watchPath);
	}

	@Override
	public FileKey detectFileKey(Path path) {
		return currentPathKeyResolver.resolvePathKey(path);
	}

	// Handle following special cases
	// Poll Mode: Delete, Modify, Create (1,2 => 2',3)/Pair rename
	// Poll Mode: Delete, Create (1 => 2)/Rename
	// Poll Mode: Modify, Modify (1,2 => 1',2')/Rotate
	// Poll Mode: Delete, Modify (1,2 => 2')/Move
	// Native Mode: Delete; Create (1 => 2)
	// Native Mode: Modify; Delete; Create (1 => 2)
	// Native Mode: Delete; Create; Delete; Create (1,2 => 2',3)
	// Native Mode: Delete; Delete; Create; Delete; Create (1,2 => 1',2')/Rotate
	// Native Mode: Delete; Delete; Create (1,2 => 2')/Move
	@Override
	public List<WatchEventKeys> detectWatchEvents(List<WatchEvent<?>> events) {
		List<WatchEventKeys> processedEvents = new ArrayList<WatchEventKeys>(
				events.size());
		PathKeyResolver historyPathKeyResolver;
		int version;
		synchronized (this) {
			historyPathKeyResolver = currentPathKeyResolver;
			currentPathKeyResolver = new PathKeyResolver(historyPathKeyResolver);
			version = currentPathKeyResolver.updateVersion();
		}
		// pre-load path file key by impacted path
		for (int i = 0, n = events.size(); i < n; i++) {
			WatchEvent<?> event = events.get(i);
			if (!(event.context() instanceof Path)) {
				continue;
			}
			Path eventPath = watchPath.resolve((Path) event.context());
			currentPathKeyResolver.resolvePathKey(eventPath, version);
		}
		// Processing events
		for (int i = 0, n = events.size(); i < n; i++) {
			WatchEvent<?> event = events.get(i);
			if (!(event.context() instanceof Path)) {
				continue;
			}
			Path contextPath = (Path) event.context();
			Path eventPath = watchPath.resolve(contextPath);
			Kind<?> eventKind = event.kind();
			FileKey historyKey = historyPathKeyResolver.resolveCachedPathKey(
					eventPath, 0);
			FileKey currentKey = currentPathKeyResolver.resolvePathKey(
					eventPath, version);
			boolean pathNotExist = (currentKey == null);
			if (eventKind == StandardWatchEventKinds.ENTRY_DELETE) {
				// check if file real deleted or renamed
				if (pathNotExist
						&& historyPathKeyResolver.resolveCachedPath(historyKey,
								0) == null) {
					// not exist presently, keep this DELETE event
					processedEvents.add(new WatchEventKeys(event, historyKey,
							eventPath, null, null));
				} else {
					// check if history key exists (in case native part event)
					Path currentPath = currentPathKeyResolver.resolvePathByKey(
							historyKey, version);
					if (currentPath != null) {
						// exist
						processedEvents.add(new WatchEventKeys(
								new DelegateWatchEvent(
										MonitorFolderEntry.ENTRY_RENAME_FROM,
										event), historyKey, eventPath,
								historyKey, currentPath));
					} else {
						// not exist, keep DELETE event
						processedEvents.add(new WatchEventKeys(event,
								historyKey, eventPath, historyKey, null));
					}
				}
			} else if (eventKind == StandardWatchEventKinds.ENTRY_MODIFY) {
				// check if file still exist
				if (pathNotExist) {
					// native mode, keep it
					processedEvents.add(new WatchEventKeys(event, historyKey,
							eventPath, null, eventPath));
				} else if (historyKey != null && historyKey.equals(currentKey)) {
					// real modify, keep it
					processedEvents.add(new WatchEventKeys(event, historyKey,
							eventPath, currentKey, eventPath));
				} else {
					Path currentPath;
					if ((currentPath = currentPathKeyResolver.resolvePathByKey(
							historyKey, version)) != null) {
						// exist
						// this was renamed to other,
						processedEvents.add(new WatchEventKeys(
								new DelegateWatchEvent(
										MonitorFolderEntry.ENTRY_RENAME_FROM,
										event), historyKey, eventPath,
								historyKey, currentPath));
					} else {
						// not exist
						processedEvents.add(new WatchEventKeys(
								new DelegateWatchEvent(
										StandardWatchEventKinds.ENTRY_DELETE,
										event), historyKey, eventPath, null,
								null));
					}
					Path cachedPath;
					if ((cachedPath = historyPathKeyResolver.resolveCachedPath(
							currentKey, 0)) != null) {
						// and some renamed to this
						processedEvents.add(new WatchEventKeys(
								new DelegateWatchEvent(
										MonitorFolderEntry.ENTRY_RENAME_TO,
										event), currentKey, cachedPath,
								currentKey, eventPath));
					} else {
						processedEvents.add(new WatchEventKeys(
								new DelegateWatchEvent(
										StandardWatchEventKinds.ENTRY_CREATE,
										event), null, null, currentKey,
								eventPath));
					}
				}
			} else if (eventKind == StandardWatchEventKinds.ENTRY_CREATE) {
				Path cachedPath;
				// check if file still exist
				if (pathNotExist) {
					// native mode, keep it
					processedEvents.add(new WatchEventKeys(event, null, null,
							currentKey, eventPath));
				} else if ((cachedPath = historyPathKeyResolver
						.resolveCachedPath(currentKey, 0)) != null) {
					// previous exists
					processedEvents.add(new WatchEventKeys(
							new DelegateWatchEvent(
									MonitorFolderEntry.ENTRY_RENAME_TO, event),
							currentKey, cachedPath, currentKey, eventPath));
				} else {
					// new created
					processedEvents.add(new WatchEventKeys(event, null, null,
							currentKey, eventPath));
				}
			}
		}
		currentPathKeyResolver.updateVersion();
		postProcess(processedEvents);
		return processedEvents;
	}

	static void postProcess(List<WatchEventKeys> processedEvents) {
		Collections.sort(processedEvents, new Comparator<WatchEventKeys>() {

			@Override
			public int compare(WatchEventKeys w1, WatchEventKeys w2) {
				Kind<?> k1 = w1.event.kind();
				Kind<?> k2 = w2.event.kind();
				return Integer.compare(toOrdinal(k1), toOrdinal(k2));
			}

			private int toOrdinal(Kind<?> kind) {
				// delete, rename-from, rename-to, create, modify
				if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
					return 5;
				} else if (kind == MonitorFolderEntry.ENTRY_RENAME_TO) {
					return 3;
				} else if (kind == MonitorFolderEntry.ENTRY_RENAME_FROM) {
					return 2;
				} else if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
					return 4;
				} else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
					return 1;
				} else {
					return 0;
				}
			}
		});
	}
}
