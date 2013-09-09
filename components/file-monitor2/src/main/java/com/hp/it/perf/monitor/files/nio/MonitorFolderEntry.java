package com.hp.it.perf.monitor.files.nio;

import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hp.it.perf.monitor.files.FileInstance;
import com.hp.it.perf.monitor.files.nio.FileKeyDetector.WatchEventKeys;

class MonitorFolderEntry {

	private static Logger log = LoggerFactory
			.getLogger(MonitorFolderEntry.class);

	private MonitorFileFolder folder;

	private FileKeyDetector fileKeyDetector;

	private Map<FileKey, FileKey> fileKeyHistory = new HashMap<FileKey, FileKey>();

	private Map<FileKey, FileInstance> keyMapping = new HashMap<FileKey, FileInstance>();

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

	public MonitorFolderEntry(MonitorFileFolder folder,
			FileKeyDetector fileKeyDetector) {
		this.folder = folder;
		this.fileKeyDetector = fileKeyDetector;
		for (FileInstance file : folder.listInstances()) {
			registerFileInstance(file);
		}
		log.trace("register {} file instance in folder {}", keyMapping.size(),
				folder.getFolder());
	}

	private void registerFileInstance(FileInstance file) {
		Path path = ((MonitorFileInstance) file).getFile().toPath();
		FileKey fileKey = fileKeyDetector.detectFileKey(path);
		if (fileKey == null) {
			throw new IllegalArgumentException("no file key found for file "
					+ path);
		}
		log.trace("register file instance {} with file key {}", file, fileKey);
		keyMapping.put(fileKey, file);
	}

	public synchronized void processEvent(List<WatchEvent<?>> events) {
		// filter events
		List<WatchEventKeys> newEvents = fileKeyDetector
				.detectWatchEvents(events);
		Map<FileInstance, BitSet> eventList = new LinkedHashMap<FileInstance, BitSet>();
		Map<FileKey, FileKey> updatedHistory = new HashMap<FileKey, FileKey>();
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
				updatedHistory.put(e.currentFileKey, eventFileKey);
			}
			log.trace("NEW Event - {}:{}({}) [{} -> {}]", new Object[] {
					e.event.kind(), e.event.context(), eventFileKey,
					e.previousFileKey, e.currentFileKey });
			// set back for later processing
			e.currentFileKey = eventFileKey;
			if (eventFileKey != null) {
				FileInstance fileInstance = keyMapping.get(eventFileKey);
				if (fileInstance == null) {
					// new file key in mapping
					fileInstance = folder
							.getOrCreateFileInstance(((Path) e.event.context())
									.toFile());
					log.trace("add new file instance key mapping: {} -> {}",
							eventFileKey, fileInstance);
					keyMapping.put(eventFileKey, fileInstance);
				} else {
					if (e.event.kind() == StandardWatchEventKinds.ENTRY_MODIFY) {
						// no-op;
					} else if (e.event.kind() == StandardWatchEventKinds.ENTRY_DELETE) {
						keyMapping.remove(eventFileKey);
						log.trace(
								"remove deleted file instance key mapping: {} -> {}",
								eventFileKey, fileInstance);
					} else if (e.event.kind() == ENTRY_RENAME_FROM) {
						keyMapping.remove(eventFileKey);
						log.trace(
								"remove renamed from file instance key mapping: {} -> {}",
								eventFileKey, fileInstance);
					}
				}
				BitSet bitSet = eventList.get(fileInstance);
				if (bitSet == null) {
					bitSet = new BitSet(n);
					eventList.put(fileInstance, bitSet);
				}
				bitSet.set(i);
			}
		}
		fileKeyHistory.putAll(updatedHistory);
		// processing events to downstream
		for (Map.Entry<FileInstance, BitSet> entry : eventList.entrySet()) {
			FileInstance fileInstance = entry.getKey();
			BitSet bitset = entry.getValue();
			// TODO removed in progress
			List<WatchEventKeys> monitorEvents = new ArrayList<WatchEventKeys>(
					bitset.cardinality());
			for (int i = bitset.nextSetBit(0); i >= 0; i = bitset
					.nextSetBit(i + 1)) {
				WatchEventKeys eventKeys = newEvents.get(i);
				WatchEvent<?> event = eventKeys.event;
				// prepare file key
				if (event != null) {
					log.trace("{} on '{}' {} ({})", new Object[] {
							event.kind(), event.context(),
							event == null ? "not match" : "*match*",
							fileInstance.getName() });
					monitorEvents.add(eventKeys);
				}
			}
			processEvent(fileInstance, monitorEvents);
		}
	}

	private void processEvent(FileInstance instance,
			List<WatchEventKeys> monitorEvents) {
		// TODO rename or delete to update keyMapping
		for (WatchEventKeys eventKey : monitorEvents) {
			WatchEvent<?> event = eventKey.event;
			if (event.kind() == StandardWatchEventKinds.ENTRY_MODIFY) {
				log.trace("dispatch file {} content change event", instance);
				folder.onContentChanged(instance);
			} else if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
				log.trace("dispatch file {} created event", instance);
				folder.onFileInstanceCreated(instance);
			} else if (event.kind() == StandardWatchEventKinds.ENTRY_DELETE) {
				log.trace("dispatch file {} deleted event", instance);
				folder.onFileInstanceDeleted(instance);
			} else if (event.kind() == ENTRY_RENAME_TO) {
				FileInstance newInstance = folder
						.getOrCreateFileInstance(eventKey.currentFileKey
								.getPath().toFile());
				log.trace("dispatch file {} rename to {} event", instance,
						newInstance);
				folder.onFileInstanceRenamed(instance, newInstance);
			} else if (event.kind() == ENTRY_RENAME_FROM) {
				// TODO
				log.trace("not dispatch file {} rename to {} event", instance,
						eventKey.currentFileKey.getPath());
			} else {
				// TODO
				throw new IllegalStateException(event.toString());
			}
		}
	}

	@Override
	public String toString() {
		return String.format("MonitorFolderEntry [folder=%s]",
				folder.getFolder());
	}

}
