package com.hp.it.perf.monitor.files.nio;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.file.FileStore;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchService;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class MultiMonitorFileService implements Closeable {

	private static final Logger log = LoggerFactory
			.getLogger(MultiMonitorFileService.class);

	private Map<FileStore, MonitorFileService> storeMonitors = new HashMap<FileStore, MonitorFileService>();

	private Constructor<?> pollingWatchConstructor;

	private Map<Path, FileStore> storeMapCache = new LinkedHashMap<Path, FileStore>() {

		private static final long serialVersionUID = 1L;

		@Override
		protected boolean removeEldestEntry(
				java.util.Map.Entry<Path, FileStore> eldest) {
			// TODO constant
			return size() > 256;
		}
	};

	private synchronized MonitorFileService getFileMonitorServiceByPath(
			Path path) throws IOException {
		// quick check store for path
		FileStore store = storeMapCache.get(path);
		if (store == null) {
			Path realFolderPath;
			realFolderPath = path.toRealPath();
			store = storeMapCache.get(realFolderPath);
			if (store == null) {
				store = Files.getFileStore(path);
				storeMapCache.put(realFolderPath, store);
			}
			storeMapCache.put(path, store);
		}
		// end find store
		MonitorFileService monitorService = storeMonitors.get(store);
		if (monitorService == null) {
			WatchService watchService = createWatchService(store);
			monitorService = new MonitorFileService(store.name(), watchService);
			log.info("create watch service for store [({}){}]: {}",
					new Object[] { store.type(), store.name(),
							watchService.getClass().getName() });
			if (isFuseType(store)) {
				monitorService
						.setKeyDetectorFactory(new FileKeyDetectorFactory() {

							@Override
							public FileKeyDetector create(Path basePath) {
								return new ContentBasedFileKeyDetector(basePath);
							}
						});
			}
			storeMonitors.put(store, monitorService);
		}
		return monitorService;
	}

	private WatchService createWatchService(FileStore store) throws IOException {
		if (isFuseType(store)) {
			// make polling watch service as for fuse
			try {
				pollingWatchConstructor = Class.forName(
						"sun.nio.fs.PollingWatchService")
						.getDeclaredConstructor();
				pollingWatchConstructor.setAccessible(true);
				return (WatchService) pollingWatchConstructor.newInstance();
			} catch (Exception e) {
				log.warn("cannot create polling watch service on store "
						+ store.name(), e);
			}
		}
		return FileSystems.getDefault().newWatchService();
	}

	@Override
	public void close() throws IOException {
		Set<Entry<FileStore, MonitorFileService>> entries;
		synchronized (this) {
			entries = new HashSet<Map.Entry<FileStore, MonitorFileService>>(
					storeMonitors.entrySet());
			storeMapCache.clear();
		}
		for (Entry<FileStore, MonitorFileService> entry : entries) {
			entry.getValue().close();
		}
	}

	protected boolean isFuseType(FileStore store) {
		return store.type().startsWith("fuse");
	}

	public MonitorFileService getMonitorService(File folder) throws IOException {
		return getFileMonitorServiceByPath(folder.toPath());
	}

}
