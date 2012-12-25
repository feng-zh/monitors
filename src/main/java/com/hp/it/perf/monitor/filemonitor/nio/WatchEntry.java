package com.hp.it.perf.monitor.filemonitor.nio;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hp.it.perf.monitor.filemonitor.FileKey;
import com.hp.it.perf.monitor.filemonitor.FileMonitorEvent;
import com.hp.it.perf.monitor.filemonitor.FileMonitorKey;
import com.hp.it.perf.monitor.filemonitor.FileMonitorMode;
import com.sun.nio.file.SensitivityWatchEventModifier;

@SuppressWarnings("restriction")
class WatchEntry {

	private static Logger log = LoggerFactory.getLogger(WatchEntry.class);

	private FileKey fileKey;

	private WatchKey watchKey;

	private Map<FileMonitorWatchKeyImpl, FileMonitorConverter> monitors;

	private WatchService watchService;

	private Path watchPath;

	private Map<FileKey, Set<FileMonitorWatchKeyImpl>> keyMapping;

	private PathKeyResolver currentPathKeyResolver;

	private Map<FileKey, AtomicLong> tickNumbers;

	private AtomicLong folderTick;

	private static interface FileMonitorConverter {

		public FileMonitorEvent convert(WatchEvent<?> event,
				FileKey eventFileKey);

	}

	public static final WatchEvent.Kind<Path> ENTRY_RENAME_TO = new RenameWatchEventKind<Path>(
			"ENTRY_RENAME_TO", Path.class);

	public static final WatchEvent.Kind<Path> ENTRY_RENAME_FROM = new RenameWatchEventKind<Path>(
			"ENTRY_RENAME_FROM", Path.class);

	private static class RenameWatchEventKind<T> implements WatchEvent.Kind<T> {
		private final String name;
		private final Class<T> type;

		RenameWatchEventKind(String name, Class<T> type) {
			this.name = name;
			this.type = type;
		}

		@Override
		public String name() {
			return name;
		}

		@Override
		public Class<T> type() {
			return type;
		}

		@Override
		public String toString() {
			return name;
		}
	}

	private static class DelegateWatchEvent implements WatchEvent<Path> {

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

	public WatchEntry(Path path, FileKey fileKey) throws IOException {
		watchPath = path;
		this.fileKey = fileKey;
		currentPathKeyResolver = new PathKeyResolver(watchPath);
	}

	public synchronized void close() {
		watchKey.cancel();
		for (FileMonitorWatchKeyImpl monitor : new HashSet<FileMonitorWatchKeyImpl>(
				monitors.keySet())) {
			monitor.close();
		}
		monitors.clear();
	}

	Path getPath() {
		return watchPath;
	}

	public synchronized void processEvent(List<WatchEvent<?>> events) {
		long lastUpdated = System.currentTimeMillis();
		folderTick.incrementAndGet();
		// filter events
		List<Map.Entry<WatchEvent<?>, FileKey>> newEvents = preprocessEvents(events);
		Map<FileMonitorWatchKeyImpl, BitSet> eventMasks = new LinkedHashMap<FileMonitorWatchKeyImpl, BitSet>();
		for (int i = 0, n = newEvents.size(); i < n; i++) {
			Map.Entry<WatchEvent<?>, FileKey> e = newEvents.get(i);
			FileKey eventFileKey = e.getValue();
			log.trace("NEW Event - {}:{}({})", new Object[] {
					e.getKey().kind(), e.getKey().context(), eventFileKey });
			if (eventFileKey != null) {
				// increase tick
				AtomicLong tickNumber = tickNumbers.get(eventFileKey);
				if (tickNumber == null) {
					tickNumber = new AtomicLong();
					tickNumbers.put(eventFileKey, tickNumber);
				}
				tickNumber.incrementAndGet();
				Set<FileMonitorWatchKeyImpl> keyList = keyMapping
						.get(eventFileKey);
				if (keyList != null) {
					for (FileMonitorWatchKeyImpl monitorKey : keyList) {
						BitSet bitSet = eventMasks.get(monitorKey);
						if (bitSet == null) {
							bitSet = new BitSet(n);
							eventMasks.put(monitorKey, bitSet);
						}
						bitSet.set(i);
					}
				}
				// else ignore this not registered file key
			}
			// always process events to monitor for current folder (at end)
			// so file update get first, then folder updates
			// TODO null as folder key mapping
			Set<FileMonitorWatchKeyImpl> folderKeyList = keyMapping.get(null);
			if (folderKeyList != null) {
				for (FileMonitorWatchKeyImpl monitorKey : folderKeyList) {
					BitSet bitSet = eventMasks.get(monitorKey);
					if (bitSet == null) {
						bitSet = new BitSet(n);
						eventMasks.put(monitorKey, bitSet);
					}
					bitSet.set(i);
				}
			}
		}
		// processing events to downstream
		for (Map.Entry<FileMonitorWatchKeyImpl, BitSet> entry : eventMasks
				.entrySet()) {
			FileMonitorWatchKeyImpl monitorKey = entry.getKey();
			BitSet bitset = entry.getValue();
			FileMonitorConverter converter = monitors.get(monitorKey);
			if (converter == null) {
				// removed in processing
				continue;
			}
			List<FileMonitorEvent> monitorEvents = new ArrayList<FileMonitorEvent>(
					bitset.cardinality());
			for (int i = bitset.nextSetBit(0); i >= 0; i = bitset
					.nextSetBit(i + 1)) {
				WatchEvent<?> event = newEvents.get(i).getKey();
				// prepare file key
				FileMonitorEvent monitorEvent = converter.convert(event,
						newEvents.get(i).getValue());
				log.trace(
						"{} on '{}' match {}({})? {}",
						new Object[] {
								event.kind(),
								event.context(),
								monitorKey.getMonitorMode(),
								monitorKey.getMonitorPath() == null ? watchPath
										: monitorKey.getMonitorPath(),
								monitorEvent != null });
				if (monitorEvent != null) {
					monitorEvents.add(monitorEvent);
				}
			}
			monitorKey.setLastUpdated(lastUpdated);
			monitorKey.processEvent(monitorEvents);
		}
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
	private List<Map.Entry<WatchEvent<?>, FileKey>> preprocessEvents(
			List<WatchEvent<?>> events) {
		Map<WatchEvent<?>, FileKey> processedEvents = new LinkedHashMap<WatchEvent<?>, FileKey>();
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
			Path eventPath = watchPath.resolve((Path) event.context());
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
					// not exist presently, keep this delete event
					processedEvents.put(event, historyKey);
				} else {
					// check if history key exists (in case native part event)
					if (currentPathKeyResolver.resolvePathByKey(historyKey,
							version) != null) {
						// exist
						processedEvents.put(new DelegateWatchEvent(
								ENTRY_RENAME_FROM, event), historyKey);
					} else {
						processedEvents.put(event, historyKey);
					}
				}
			} else if (eventKind == StandardWatchEventKinds.ENTRY_MODIFY) {
				// check if file still exist
				if (pathNotExist) {
					// native mode, keep it
					processedEvents.put(event, historyKey);
				} else if (historyKey != null && historyKey.equals(currentKey)) {
					// real modify, keep it
					processedEvents.put(event, currentKey);
				} else {
					if (currentPathKeyResolver.resolvePathByKey(historyKey,
							version) != null) {
						// exist
						// this was renamed to other,
						processedEvents.put(new DelegateWatchEvent(
								ENTRY_RENAME_FROM, event), historyKey);
					} else {
						// not exist
						processedEvents.put(new DelegateWatchEvent(
								StandardWatchEventKinds.ENTRY_DELETE, event),
								historyKey);
					}
					if (historyPathKeyResolver.resolveCachedPath(currentKey, 0) != null) {
						// and some renamed to this
						processedEvents.put(new DelegateWatchEvent(
								ENTRY_RENAME_TO, event), currentKey);
					} else {
						processedEvents.put(new DelegateWatchEvent(
								StandardWatchEventKinds.ENTRY_CREATE, event),
								currentKey);
					}
				}
			} else if (eventKind == StandardWatchEventKinds.ENTRY_CREATE) {
				// check if file still exist
				if (pathNotExist) {
					// native mode, keep it
					processedEvents.put(event, currentKey);
				} else if (historyPathKeyResolver.resolveCachedPath(currentKey,
						0) != null) {
					// previous exists
					processedEvents.put(new DelegateWatchEvent(ENTRY_RENAME_TO,
							event), currentKey);
				} else {
					// new created
					processedEvents.put(event, currentKey);
				}
			}
		}
		currentPathKeyResolver.updateVersion();
		return new ArrayList<Map.Entry<WatchEvent<?>, FileKey>>(
				processedEvents.entrySet());
	}

	FileKey getFileKey() {
		return fileKey;
	}

	public synchronized FileMonitorKey createMonitorKey(final Path path,
			final FileMonitorMode mode, Map<WatchKey, WatchEntry> watchKeys)
			throws IOException {
		if (monitors == null) {
			monitors = new HashMap<FileMonitorWatchKeyImpl, FileMonitorConverter>();
		}
		final FileKey monitorFileKey;
		if (path != null) {
			monitorFileKey = currentPathKeyResolver.resolvePathKey(path);
			if (monitorFileKey == null) {
				throw new IOException("no file key found for file " + path);
			}
		} else {
			monitorFileKey = null;
		}
		final FileMonitorWatchKeyImpl monitorKey = new FileMonitorWatchKeyImpl(
				this, path, monitorFileKey, mode);
		// init key mapping
		if (keyMapping == null) {
			keyMapping = new HashMap<FileKey, Set<FileMonitorWatchKeyImpl>>();
		}
		Set<FileMonitorWatchKeyImpl> keyList = keyMapping.get(monitorFileKey);
		if (keyList == null) {
			keyList = new HashSet<FileMonitorWatchKeyImpl>();
			keyMapping.put(monitorFileKey, keyList);
		}
		keyList.add(monitorKey);
		// init tick count
		if (folderTick == null) {
			folderTick = new AtomicLong();
		}
		if (tickNumbers == null) {
			tickNumbers = new HashMap<FileKey, AtomicLong>();
		}
		if (monitorFileKey != null) {
			tickNumbers.put(monitorFileKey, new AtomicLong());
		}
		final Kind<?> watchKind = toWatchKind(mode);
		monitors.put(monitorKey, new FileMonitorConverter() {

			private Path fileMonitorPath = path;

			private FileKey fileMonitorKey = monitorFileKey;

			private Kind<?> fileMonitorKind = watchKind;

			@Override
			public FileMonitorEvent convert(WatchEvent<?> event,
					FileKey eventFileKey) {
				if (event.kind() != fileMonitorKind) {
					return null;
				}
				Path eventRelativePath = (Path) event.context();
				if (eventRelativePath == null) {
					return null;
				}
				if (fileMonitorPath != null) {
					if (fileMonitorKind == StandardWatchEventKinds.ENTRY_DELETE) {
						if (eventFileKey == null
								|| !eventFileKey.equals(fileMonitorKey)) {
							return null;
						}
						// monitored file is deleted (at least in the folder)
					} else {
						if (!(eventFileKey == fileMonitorKey ? true
								: (eventFileKey != null && eventFileKey
										.equals(fileMonitorKey)))) {
							return null;
						}
					}
				}
				FileMonitorEvent monitorEvent = new FileMonitorEvent(
						WatchEntry.this, fileMonitorPath == null ? folderTick
								.get() : tickNumbers.get(eventFileKey).get());
				monitorEvent.setMonitorFile(watchPath.toFile());
				monitorEvent.setMode(mode);
				monitorEvent.setChangedFile(watchPath
						.resolve(eventRelativePath).toFile());
				monitorEvent.setChangedFileKey(eventFileKey);
				monitorEvent.setMonitorKey(monitorKey);
				return monitorEvent;
			}
		});
		if (watchKey == null) {
			watchKey = watchPath.register(watchService, new Kind<?>[] {
					StandardWatchEventKinds.ENTRY_CREATE,
					StandardWatchEventKinds.ENTRY_MODIFY,
					StandardWatchEventKinds.ENTRY_DELETE },
					SensitivityWatchEventModifier.HIGH);
			log.debug(
					"register nio watch service on path {} with create/modify/delete kinds",
					watchPath);
			watchKeys.put(watchKey, this);
		}
		return monitorKey;
	}

	private Kind<?> toWatchKind(FileMonitorMode mode) {
		switch (mode) {
		case MODIFY:
			return StandardWatchEventKinds.ENTRY_MODIFY;
		case CREATE:
			return StandardWatchEventKinds.ENTRY_CREATE;
		case DELETE:
			return StandardWatchEventKinds.ENTRY_DELETE;
		case RENAME:
			return ENTRY_RENAME_TO;
		default:
			throw new UnsupportedOperationException(mode.toString());
		}
	}

	WatchKey getWatchKey() {
		return watchKey;
	}

	void setWatchService(WatchService watchService) {
		this.watchService = watchService;
	}

	public synchronized void removeFileMonitorKey(
			FileMonitorWatchKeyImpl monitorKey) {
		monitors.remove(monitorKey);
		Set<FileMonitorWatchKeyImpl> keyList = keyMapping.get(monitorKey
				.getMonitorFileKey());
		if (keyList != null) {
			keyList.remove(monitorKey);
			if (keyList.isEmpty()) {
				keyMapping.remove(monitorKey.getMonitorFileKey());
			}
		}
		if (monitors.isEmpty()) {
			// nothing in watch
			close();
		}
	}

	@Override
	public String toString() {
		return String.format("WatchEntry (path=%s)", watchPath);
	}

}