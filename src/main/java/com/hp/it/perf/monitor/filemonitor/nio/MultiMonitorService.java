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
import java.util.Map;

import com.hp.it.perf.monitor.filemonitor.FileMonitorKey;
import com.hp.it.perf.monitor.filemonitor.FileMonitorMode;
import com.hp.it.perf.monitor.filemonitor.FileMonitorService;

public class MultiMonitorService implements FileMonitorService {

	private Map<FileStore, FileMonitorService> storeMonitors = new HashMap<FileStore, FileMonitorService>();

	private Constructor<?> pollingWatchConstructor;

	@Override
	public FileMonitorKey singleRegister(File file, FileMonitorMode mode)
			throws IOException, IllegalStateException {
		return getFileMonitorServiceByPath(file.toPath()).singleRegister(file,
				mode);
	}

	private synchronized FileMonitorService getFileMonitorServiceByPath(
			Path path) throws IOException {
		FileStore store = Files.getFileStore(path);
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
		return getFileMonitorServiceByPath(file.toPath()).folderRegister(file,
				mode);
	}

}
