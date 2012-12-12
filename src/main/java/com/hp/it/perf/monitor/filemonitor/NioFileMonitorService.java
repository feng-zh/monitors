package com.hp.it.perf.monitor.filemonitor;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import com.sun.nio.file.SensitivityWatchEventModifier;

public class NioFileMonitorService implements Runnable, FileMonitorService {

	private WatchService watchService;
	private Map<WatchKey, FileMonitorWatchKeyImpl> watchKeys = new IdentityHashMap<WatchKey, FileMonitorWatchKeyImpl>();

	private ExecutorService eventProcess = Executors
			.newSingleThreadExecutor(new ThreadFactory() {

				@Override
				public Thread newThread(Runnable r) {
					Thread thread = Executors.defaultThreadFactory().newThread(
							r);
					thread.setDaemon(true);
					return thread;
				}
			});

	public NioFileMonitorService() throws IOException {
		watchService = FileSystems.getDefault().newWatchService();
		eventProcess.submit(this);
	}

	@Override
	public FileMonitorKey singleRegister(File file, FileMonitorMode mode)
			throws IOException, IllegalStateException {
		Path path = file.toPath();
		FileStore store = Files.getFileStore(path);
		if (mode == FileMonitorMode.CHANGE) {
			synchronized (this) {
				WatchKey watchKey = path
						.toAbsolutePath()
						.getParent()
						.register(
								watchService,
								new WatchEvent.Kind<?>[] { StandardWatchEventKinds.ENTRY_MODIFY },
								SensitivityWatchEventModifier.HIGH);
				FileMonitorWatchKeyImpl monitorKey = new FileMonitorWatchKeyImpl(
						watchKey);
				watchKeys.put(watchKey, monitorKey);
				return monitorKey;
			}
		}
		throw new UnsupportedOperationException();
	}

	@Override
	public FileMonitorKey folderRegister(File file, FileMonitorMode mode)
			throws IOException, IllegalStateException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void run() {
		while (true) {
			WatchKey key;
			try {
				key = watchService.take();
			} catch (InterruptedException e) {
				System.err.println("intrrupted");
				break;
			}
			if (!key.isValid()) {
				continue;
			}
			List<WatchEvent<?>> events = key.pollEvents();
			System.out.println("got events: " + events);
			FileMonitorWatchKeyImpl monitorKey;
			synchronized (this) {
				monitorKey = watchKeys.get(key);
			}
			if (monitorKey != null) {
				monitorKey.processEvent(events);
			}
			if (!key.reset()) {
				monitorKey.close();
			}

		}
	}
}
