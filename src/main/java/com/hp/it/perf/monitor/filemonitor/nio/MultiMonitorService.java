package com.hp.it.perf.monitor.filemonitor.nio;

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

import com.hp.it.perf.monitor.filemonitor.FileMonitorKey;
import com.hp.it.perf.monitor.filemonitor.FileMonitorMode;
import com.hp.it.perf.monitor.filemonitor.FileMonitorService;

public class MultiMonitorService implements FileMonitorService {

	private Map<FileStore, FileMonitorService> storeMonitors = new HashMap<FileStore, FileMonitorService>();

	private Constructor<?> pollingWatchConstructor;

	private Map<Path, FileStore> storeMapCache = new LinkedHashMap<Path, FileStore>() {

		private static final long serialVersionUID = 1L;

		@Override
		protected boolean removeEldestEntry(
				java.util.Map.Entry<Path, FileStore> eldest) {
			return size() > 256;
		}
	};

	@Override
	public FileMonitorKey singleRegister(File file, FileMonitorMode mode)
			throws IOException, IllegalStateException {
		return getFileMonitorServiceByPath(file.toPath(), false)
				.singleRegister(file, mode);
	}

	private synchronized FileMonitorService getFileMonitorServiceByPath(
			Path path, boolean folder) throws IOException {
		// quick check store for path
		FileStore store = storeMapCache.get(path);
		if (store == null) {
			Path realFolderPath;
			if (folder) {
				realFolderPath = path.toRealPath();
			} else {
				realFolderPath = path.toRealPath().getParent();
			}
			store = storeMapCache.get(realFolderPath);
			if (store == null) {
				store = Files.getFileStore(path);
				storeMapCache.put(realFolderPath, store);
			}
			storeMapCache.put(path, store);
		}
		// end find store
		FileMonitorService monitorService = storeMonitors.get(store);
		if (monitorService == null) {
			monitorService = new NioFileMonitorService(store.name(),
					createWatchService(store));
			storeMonitors.put(store, monitorService);
		}
		return monitorService;
	}

	private WatchService createWatchService(FileStore store) throws IOException {
		if ("fuse".equals(store.type())) {
			// make polling watch service as for fuse
			try {
				pollingWatchConstructor = Class.forName(
						"sun.nio.fs.PollingWatchService")
						.getDeclaredConstructor();
				pollingWatchConstructor.setAccessible(true);
				return (WatchService) pollingWatchConstructor.newInstance();
			} catch (Exception e) {
				// TODO log it
				e.printStackTrace();
			}
		}
		return FileSystems.getDefault().newWatchService();
	}

	@Override
	public FileMonitorKey folderRegister(File file, FileMonitorMode mode)
			throws IOException, IllegalStateException {
		return getFileMonitorServiceByPath(file.toPath(), true).folderRegister(
				file, mode);
	}

	@Override
	public void close() throws IOException {
		Set<Entry<FileStore, FileMonitorService>> entries;
		synchronized (this) {
			entries = new HashSet<Map.Entry<FileStore, FileMonitorService>>(
					storeMonitors.entrySet());
			storeMapCache.clear();
		}
		for (Entry<FileStore, FileMonitorService> entry : entries) {
			entry.getValue().close();
		}
	}

}
