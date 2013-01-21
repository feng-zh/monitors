package com.hp.it.perf.monitor.filemonitor.nio;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hp.it.perf.monitor.filemonitor.FileMonitorKey;
import com.hp.it.perf.monitor.filemonitor.FileMonitorMode;
import com.hp.it.perf.monitor.filemonitor.FileMonitorService;

public class NioFileMonitorService implements Runnable, FileMonitorService {

	private WatchService watchService;

	private Map<WatchKey, WatchEntry> watchKeys = Collections
			.synchronizedMap(new IdentityHashMap<WatchKey, WatchEntry>());

	private ConcurrentMap<Path, WatchEntry> watchEntrys = new ConcurrentHashMap<Path, WatchEntry>();

	private String watchEntryName = "default";

	private static Logger log = LoggerFactory
			.getLogger(NioFileMonitorService.class);

	private ExecutorService eventProcess;

	private Semaphore startGuard = new Semaphore(0);

	private FileKeyDetectorFactory keyDetectorFactory = new FileKeyDetectorFactory() {

		@Override
		public FileKeyDetector create(Path basePath) {
			return new NativeFileKeyDetector(basePath);
		}
	};

	public NioFileMonitorService() throws IOException {
		watchService = FileSystems.getDefault().newWatchService();
		init();
	}

	NioFileMonitorService(String name, WatchService watchService)
			throws IOException {
		this.watchService = watchService;
		watchEntryName = name;
		init();
		log.debug("init nio monitor service on {}", watchEntryName);
	}

	public void setKeyDetectorFactory(FileKeyDetectorFactory keyDetectorFactory) {
		this.keyDetectorFactory = keyDetectorFactory;
	}

	private void init() {
		eventProcess = Executors.newSingleThreadExecutor(new ThreadFactory() {
			@Override
			public Thread newThread(Runnable r) {
				Thread thread = Executors.defaultThreadFactory().newThread(r);
				thread.setName("NIO File Monitor [" + watchEntryName + "]");
				thread.setDaemon(true);
				return thread;
			}
		});
		eventProcess.submit(this);
		startGuard.acquireUninterruptibly();
	}

	@Override
	public FileMonitorKey singleRegister(File file, FileMonitorMode mode)
			throws IOException, IllegalStateException {
		Path path = file.toPath();
		Path parentPath = path.getParent();
		if (parentPath == null) {
			parentPath = Paths.get(".");// current work folder
		}
		WatchEntry newWatchEntry = prepareWatchEntry(parentPath);
		// find if related watch for the path is registered
		WatchEntry watchEntry = watchEntrys.putIfAbsent(
				newWatchEntry.getRealPath(), newWatchEntry);
		if (watchEntry == null) {
			watchEntry = newWatchEntry;
		}
		FileMonitorKey monitorKey = watchEntry.createMonitorKey(path, mode,
				watchKeys);
		return monitorKey;
	}

	private WatchEntry prepareWatchEntry(Path path) throws IOException {
		WatchEntry entry = new WatchEntry(path, keyDetectorFactory.create(path));
		entry.setWatchService(watchService);
		return entry;
	}

	@Override
	public FileMonitorKey folderRegister(File folder, FileMonitorMode mode)
			throws IOException, IllegalStateException {
		if (!folder.isDirectory()) {
			throw new IOException(folder + " is not a folder");
		}
		Path path = folder.toPath();
		WatchEntry newWatchEntry = prepareWatchEntry(path);
		// find if related watch for the path is registered
		WatchEntry watchEntry = watchEntrys.putIfAbsent(
				newWatchEntry.getRealPath(), newWatchEntry);
		if (watchEntry == null) {
			watchEntry = newWatchEntry;
		}
		FileMonitorKey monitorKey = watchEntry.createMonitorKey(null, mode,
				watchKeys);
		return monitorKey;
	}

	@Override
	public void run() {
		log.info("start file monitor service thread: {}", Thread
				.currentThread().getName());
		startGuard.release();
		try {
			while (true) {
				WatchKey key;
				try {
					key = watchService.take();
				} catch (InterruptedException e) {
					log.info("watch thread is interrupted");
					break;
				}
				log.trace("take watch key for path '{}'", key.watchable());
				WatchEntry watchEntry = watchKeys.get(key);
				if (!key.isValid()) {
					continue;
				}
				List<WatchEvent<?>> events = key.pollEvents();
				if (log.isTraceEnabled()) {
					log.trace("poll {} watch events", events.size());
					for (WatchEvent<?> event : events) {
						log.trace("- Event {}({}) on {}/{}",
								new Object[] { event.kind(), event.count(),
										watchEntry.getPath(), event.context() });
					}
				}
				// reset key to retrieve pending events
				if (!key.reset()) {
					log.debug("close invalid watch entry {}", watchEntry);
					closeWatchEntry(watchEntry);
				}
				// processing events
				if (watchEntry != null) {
					log.trace("dispatch event to watch entry {}", watchEntry);
					watchEntry.processEvent(events);
				}
			}
		} catch (Throwable t) {
			log.error("got error on monitor thread", t);
		} finally {
			log.info("exit monitor thread");
		}
	}

	private void closeWatchEntry(WatchEntry watchEntry) {
		watchEntrys.remove(watchEntry.getRealPath());
		watchKeys.remove(watchEntry.getWatchKey());
		watchEntry.close();
	}

	@Override
	public void close() throws IOException {
		eventProcess.shutdownNow();
		while (!eventProcess.isTerminated()) {
			try {
				eventProcess.awaitTermination(1, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			}
		}
		synchronized (this) {
			Set<WatchEntry> entries = new HashSet<WatchEntry>(
					watchKeys.values());
			for (WatchEntry entry : entries) {
				closeWatchEntry(entry);
			}
			watchService.close();
		}
	}

	@Override
	public Object getKeyByFile(File file) throws IOException {
		return FileMonitors.getKeyByFile(file);
	}
}