package com.hp.it.perf.monitor.files.nio;

import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hp.it.perf.monitor.files.FileInstance;
import com.hp.it.perf.monitor.files.FileInstanceChangeListener.FileChangeOption;
import com.hp.it.perf.monitor.files.nio.FileKeyDetector.WatchEventKeys;

class MonitorFolderEntry {

	private static Logger log = LoggerFactory
			.getLogger(MonitorFolderEntry.class);

	private MonitorFileFolder folder;

	private FileKeyDetector fileKeyDetector;

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
		Map<Path, WatchEventKeys> pendingRenameFromEvents = new HashMap<Path, FileKeyDetector.WatchEventKeys>();
		Map<Path, WatchEventKeys> pendingRenameToEvents = new HashMap<Path, FileKeyDetector.WatchEventKeys>();
		for (int i = 0, n = newEvents.size(); i < n; i++) {
			WatchEventKeys e = newEvents.get(i);
			log.trace("NEW Event - {}({})[({}){} -> ({}){}]", new Object[] {
					e.event.kind(), e.event.context(), e.previousPath,
					e.previousFileKey, e.currentPath, e.currentFileKey });
			FileInstance oldFileInstance = keyMapping.get(e.previousFileKey);
			FileInstance newFileInstance = keyMapping.get(e.currentFileKey);
			Kind<?> kind = e.event.kind();
			if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
				// modify
				e.currentInstance = newFileInstance;
			} else if (kind == StandardWatchEventKinds.ENTRY_DELETE
					|| kind == ENTRY_RENAME_FROM) {
				// delete or rename from
				keyMapping.remove(e.previousFileKey);
				e.previousInstance = oldFileInstance;
				if (kind == ENTRY_RENAME_FROM) {
					pendingRenameFromEvents.put(e.currentPath, e);
				}
			} else if (kind == StandardWatchEventKinds.ENTRY_CREATE
					|| kind == ENTRY_RENAME_TO) {
				// create or rename to
				e.currentInstance = folder.makeFileInstance(e.currentPath
						.toFile());
				keyMapping.put(e.currentFileKey, e.currentInstance);
				if (kind == ENTRY_RENAME_TO) {
					pendingRenameToEvents.put(e.previousPath, e);
				} else {
					// set read offset in start
					MonitorFileStream.saveReadOffset(e.currentInstance, 0L);
				}
			}
		}
		for (int i = 0, n = newEvents.size(); i < n; i++) {
			WatchEventKeys e = newEvents.get(i);
			Kind<?> kind = e.event.kind();
			if (kind == ENTRY_RENAME_FROM) {
				pendingRenameToEvents.get(e.previousPath).previousInstance = e.previousInstance;
			} else if (kind == ENTRY_RENAME_TO) {
				pendingRenameFromEvents.get(e.currentPath).currentInstance = e.currentInstance;
			}
		}
		for (int i = 0, n = newEvents.size(); i < n; i++) {
			WatchEventKeys e = newEvents.get(i);
			FileInstance oldFileInstance = e.previousInstance;
			FileInstance newFileInstance = e.currentInstance;
			log.trace(
					"Process {}({}) Event - [({}){} -> ({}){}] => [{} -> {}]",
					new Object[] { e.event.kind(), e.event.context(),
							e.previousPath, e.previousFileKey, e.currentPath,
							e.currentFileKey, oldFileInstance, newFileInstance });
			if (e.event.kind() == StandardWatchEventKinds.ENTRY_MODIFY) {
				// no-op;
				log.trace("dispatch file {} content change event",
						newFileInstance);
				folder.onContentChanged(newFileInstance);
			} else {
				boolean renamed = false;
				if (e.event.kind() == StandardWatchEventKinds.ENTRY_DELETE
						|| (renamed = (e.event.kind() == ENTRY_RENAME_FROM))) {
					// remove or rename from
					log.trace("remove {} file instance key mapping: {} -> {}",
							renamed ? "renamed" : "deleted", e.previousFileKey,
							oldFileInstance);
					if (renamed) {
						log.trace("dispatch rename file {} event (from {})",
								newFileInstance, oldFileInstance);
					} else {
						log.trace("dispatch delete file {} event",
								oldFileInstance);
					}
					folder.onFileInstanceDeleted(oldFileInstance,
							renamed ? new FileChangeOption(newFileInstance)
									: new FileChangeOption());
				} else if (e.event.kind() == StandardWatchEventKinds.ENTRY_CREATE
						|| (renamed = (e.event.kind() == ENTRY_RENAME_TO))) {
					log.trace("add {} file instance key mapping: {} -> {}",
							renamed ? "renaming" : "creating",
							e.currentFileKey, newFileInstance);
					if (renamed) {
						log.trace("dispatch rename file {} event (from {})",
								newFileInstance, oldFileInstance);
					} else {
						log.trace("dispatch create file {} event",
								newFileInstance);
					}
					folder.onFileInstanceCreated(newFileInstance,
							renamed ? new FileChangeOption(oldFileInstance)
									: new FileChangeOption());
					log.trace(
							"try to dispatch content change event for new file {}",
							newFileInstance);
					folder.onContentChanged(newFileInstance);
				}
			}
		}
	}

	@Override
	public String toString() {
		return String.format("MonitorFolderEntry [folder=%s]",
				folder.getFolder());
	}

}
