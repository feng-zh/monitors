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

	private PathKeyResolver pathKeyResolver = new PathKeyResolver();

	private Map<FileKey, Set<FileMonitorWatchKeyImpl>> keyMapping;

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

	private static class RenameWatchEvent implements WatchEvent<Path> {

		private Kind<Path> kind;
		private WatchEvent<?> event;

		public RenameWatchEvent(Kind<Path> kind, WatchEvent<?> event) {
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

	public WatchEntry(Path path) throws IOException {
		watchPath = path;
		fileKey = pathKeyResolver.resolvePathKey(path);
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
		PathKeyResolver currentResolver = new PathKeyResolver(pathKeyResolver);
		folderTick.incrementAndGet();
		FileKey[] eventFileKeyList = new FileKey[events.size()];
		// use bit set to save memory allocation and unnecessary checking
		// if not rename possible
		BitSet deleteMask = new BitSet(events.size());
		BitSet createMask = new BitSet(events.size());
		Map<FileMonitorWatchKeyImpl, BitSet> eventMasks = new LinkedHashMap<FileMonitorWatchKeyImpl, BitSet>();
		// pre-processing events
		for (int i = 0, n = events.size(); i < n; i++) {
			WatchEvent<?> event = events.get(i);
			Path eventRelativePath = (Path) event.context();
			if (eventRelativePath == null) {
				continue;
			}
			Path eventPath = watchPath.resolve(eventRelativePath);
			Kind<?> eventKind = event.kind();
			FileKey fileKey;
			if (eventKind == StandardWatchEventKinds.ENTRY_DELETE) {
				fileKey = currentResolver.getKeyForDeleted(eventPath);
				eventFileKeyList[i] = fileKey;
				if (fileKey != null) {
					deleteMask.set(i);
				}
			} else if (eventKind == StandardWatchEventKinds.ENTRY_CREATE) {
				fileKey = currentResolver.resolvePathKey(eventPath);
				eventFileKeyList[i] = fileKey;
				if (fileKey != null) {
					createMask.set(i);
				}
			} else if (eventKind == StandardWatchEventKinds.ENTRY_MODIFY) {
				fileKey = currentResolver.fastResolvePathKey(eventPath);
				eventFileKeyList[i] = fileKey;
			} else {
				continue;
			}
			// increase tick
			AtomicLong tickNumber = tickNumbers.get(fileKey);
			if (tickNumber == null) {
				// TODO warning
				tickNumber = new AtomicLong();
				tickNumbers.put(fileKey, tickNumber);
			}
			tickNumber.incrementAndGet();
			Set<FileMonitorWatchKeyImpl> keyList = keyMapping.get(fileKey);
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
		// check if rename processing (create and delete)
		if (deleteMask.cardinality() != 0 && createMask.cardinality() != 0) {
			Map<FileKey, Integer> fileKeyMap = new HashMap<FileKey, Integer>();
			for (int i = deleteMask.nextSetBit(0); i >= 0; i = deleteMask
					.nextSetBit(i + 1)) {
				fileKeyMap.put(eventFileKeyList[i], i);
			}
			for (int i = createMask.nextSetBit(0); i >= 0; i = createMask
					.nextSetBit(i + 1)) {
				FileKey fileKey = eventFileKeyList[i];
				Integer deleteEventIndex = fileKeyMap.remove(fileKey);
				if (deleteEventIndex != null) {
					// remove delete event
					events.set(
							deleteEventIndex.intValue(),
							new RenameWatchEvent(ENTRY_RENAME_FROM, events
									.get(deleteEventIndex.intValue())));
					events.set(
							i,
							new RenameWatchEvent(ENTRY_RENAME_TO, events.get(i)));
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
				WatchEvent<?> event = events.get(i);
				// prepare file key
				FileMonitorEvent monitorEvent = converter.convert(event,
						eventFileKeyList[i]);
				log.trace(
						"{} on '{}' match {}({})? {}",
						new Object[] { event.kind(), event.context(),
								monitorKey.getMonitorMode(),
								monitorKey.getMonitorPath(),
								monitorEvent != null });
				if (monitorEvent != null) {
					monitorEvents.add(monitorEvent);
				}
			}
			monitorKey.setLastUpdated(lastUpdated);
			monitorKey.processEvent(monitorEvents);
		}
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
			monitorFileKey = pathKeyResolver.resolvePathKey(path);
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