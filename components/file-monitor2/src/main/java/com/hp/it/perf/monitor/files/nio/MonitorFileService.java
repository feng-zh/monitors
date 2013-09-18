package com.hp.it.perf.monitor.files.nio;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.nio.file.SensitivityWatchEventModifier;

@SuppressWarnings("restriction")
class MonitorFileService implements Closeable, Runnable {

	private String watchEntryName;

	private WatchService watchService;

	private static Logger log = LoggerFactory
			.getLogger(MonitorFileService.class);

	private ExecutorService eventProcess;

	private Semaphore startGuard = new Semaphore(0);

	private Map<WatchKey, MonitorFolderEntry> watchKeys = new HashMap<WatchKey, MonitorFolderEntry>();

	private FileKeyDetectorFactory fileKeyDetectorFactory = new FileKeyDetectorFactory() {

		@Override
		public FileKeyDetector create(Path basePath) {
			return new NativeFileKeyDetector(basePath);
		}
	};

	public MonitorFileService(String name, WatchService watchService) {
		this.watchEntryName = name;
		this.watchService = watchService;
		init();
		log.debug("init nio monitor service on {}", watchEntryName);
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

	protected MonitorFolderEntry registerWatch(MonitorFileFolder fileSet)
			throws IOException {
		Path basePath = fileSet.getFolder().toPath();
		WatchKey watchKey = basePath.register(watchService, new Kind<?>[] {
				StandardWatchEventKinds.ENTRY_CREATE,
				StandardWatchEventKinds.ENTRY_MODIFY,
				StandardWatchEventKinds.ENTRY_DELETE },
				SensitivityWatchEventModifier.HIGH);
		log.debug("register watch on path {}", basePath);
		FileKeyDetector fileKeyDetector = fileKeyDetectorFactory
				.create(basePath);
		log.debug("use file key detector {}", fileKeyDetector);
		MonitorFolderEntry folderEntry = new MonitorFolderEntry(fileSet,
				fileKeyDetector);
		watchKeys.put(watchKey, folderEntry);
		return folderEntry;
	}

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
			Set<WatchKey> entries = new HashSet<WatchKey>(watchKeys.keySet());
			for (WatchKey entry : entries) {
				closeWatchEntry(entry);
			}
			watchService.close();
		}
	}

	private void closeWatchEntry(WatchKey watchEntry) {
		watchEntry.cancel();
		watchKeys.remove(watchEntry);
	}

	@Override
	public void run() {
		log.info("start file monitor service thread: {}", Thread
				.currentThread().getName());
		startGuard.release();
		try {
			// TODO what about error in loop
			while (true) {
				WatchKey key;
				try {
					log.trace("waiting for watch events");
					key = watchService.take();
				} catch (InterruptedException e) {
					log.info("watch thread is interrupted");
					break;
				}
				log.trace("take watch key for path '{}'", key.watchable());
				if (!key.isValid()) {
					continue;
				}
				Path path = (Path) key.watchable();
				MonitorFolderEntry watchEntry = watchKeys.get(key);
				List<WatchEvent<?>> events = key.pollEvents();
				if (log.isTraceEnabled()) {
					log.trace("poll {} watch events", events.size());
					for (WatchEvent<?> event : events) {
						log.trace("- Event {}({}) on {}{}{}", new Object[] {
								event.kind(), event.count(), path,
								File.pathSeparator, event.context() });
					}
				}
				// reset key to retrieve pending events
				if (!key.reset()) {
					log.debug("close invalid watch entry {}", watchEntry);
					closeWatchEntry(key);
				}
				// processing events
				if (watchEntry != null) {
					log.trace("dispatch event to watch entry {}", watchEntry);
					watchEntry.processEvent(events);
				}
			}
		} catch (Throwable t) {
			log.error("got error on monitor thread " + Thread.currentThread(),
					t);
		} finally {
			log.info("exit monitor thread: {}", Thread.currentThread()
					.getName());
		}
	}

	void setKeyDetectorFactory(FileKeyDetectorFactory fileKeyDetectorFactory) {
		this.fileKeyDetectorFactory = fileKeyDetectorFactory;
	}

}
