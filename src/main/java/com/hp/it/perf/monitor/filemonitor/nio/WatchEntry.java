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
import com.hp.it.perf.monitor.filemonitor.nio.FileKeyDetector.WatchEventKeys;
import com.sun.nio.file.SensitivityWatchEventModifier;

@SuppressWarnings("restriction")
class WatchEntry {

	private static Logger log = LoggerFactory.getLogger(WatchEntry.class);

	private WatchKey watchKey;

	private Map<FileMonitorWatchKeyImpl, FileMonitorConverter> monitors;

	private WatchService watchService;

	private Path watchPath;

	private Path absoluteWatchPath;

	private Map<FileKey, Set<FileMonitorWatchKeyImpl>> keyMapping;

	private FileKeyDetector fileKeyDetector;

	private Map<FileKey, AtomicLong> tickNumbers;

	private Map<FileKey, FileKey> fileKeyHistory = new HashMap<FileKey, FileKey>();

	private AtomicLong folderTick;

	private boolean slowSensitivity = Boolean.getBoolean("monitor.nio.slow");

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

	public WatchEntry(Path path, FileKeyDetector keyDetector)
			throws IOException {
		watchPath = path;
		absoluteWatchPath = path.toAbsolutePath().normalize();
		this.fileKeyDetector = keyDetector;
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
		List<WatchEventKeys> newEvents = fileKeyDetector
				.detectWatchEvents(events);
		Map<FileMonitorWatchKeyImpl, BitSet> eventMasks = new LinkedHashMap<FileMonitorWatchKeyImpl, BitSet>();
		for (int i = 0, n = newEvents.size(); i < n; i++) {
			WatchEventKeys e = newEvents.get(i);
			FileKey eventFileKey = fileKeyHistory.remove(e.previousFileKey);
			if (e.previousFileKey == null) {
				eventFileKey = e.currentFileKey;
			}
			if (eventFileKey == null) {
				eventFileKey = e.previousFileKey;
			}
			if (eventFileKey != null && e.currentFileKey != null) {
				fileKeyHistory.put(e.currentFileKey, eventFileKey);
			}
			log.trace("NEW Event - {}:{}({}) [{} -> {}]", new Object[] {
					e.event.kind(), e.event.context(), eventFileKey,
					e.previousFileKey, e.currentFileKey });
			// set back for later processing
			e.currentFileKey = eventFileKey;
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
				WatchEventKeys eventKeys = newEvents.get(i);
				WatchEvent<?> event = eventKeys.event;
				// prepare file key
				FileMonitorEvent monitorEvent = converter.convert(
						eventKeys.event, eventKeys.currentFileKey);
				if (monitorEvent != null) {
					log.trace(
							"{} on '{}' match {}({})",
							new Object[] {
									event.kind(),
									event.context(),
									monitorKey.getMonitorMode(),
									monitorKey.getMonitorPath() == null ? watchPath
											: monitorKey.getMonitorPath() });
					monitorEvents.add(monitorEvent);
				}
			}
			monitorKey.setLastUpdated(lastUpdated);
			monitorKey.processEvent(monitorEvents);
		}
	}

	public synchronized FileMonitorKey createMonitorKey(final Path path,
			final FileMonitorMode mode, Map<WatchKey, WatchEntry> watchKeys)
			throws IOException {
		if (monitors == null) {
			monitors = new HashMap<FileMonitorWatchKeyImpl, FileMonitorConverter>();
		}
		final FileKey monitorFileKey;
		if (path != null) {
			monitorFileKey = fileKeyDetector.detectFileKey(path);
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
			fileKeyHistory.put(monitorFileKey, monitorFileKey);
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
					slowSensitivity ? SensitivityWatchEventModifier.MEDIUM
							: SensitivityWatchEventModifier.HIGH);
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
				for (Map.Entry<FileKey, FileKey> entry : fileKeyHistory
						.entrySet()) {
					if (entry.getValue().equals(monitorKey.getMonitorFileKey())) {
						fileKeyHistory.remove(entry.getKey());
						break;
					}
				}
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

	Path getRealPath() {
		return absoluteWatchPath;
	}

}