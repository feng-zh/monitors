package com.hp.it.perf.monitor.filemonitor;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// This is not thread safe
// Provider for one file with same i-node (file key)
// low level provider for file created
public class UniqueFile extends ManagedFileContentProvider implements
		FileContentProvider, UniqueFileMXBean {

	private static final Logger log = LoggerFactory.getLogger(UniqueFile.class);

	public static int DefaultIdleTimeout = 0;

	public static boolean DefaultLazyOpen = false;

	private File file;

	private File currentFile;

	private long initOffset;

	private int idleTimeout = DefaultIdleTimeout;

	private boolean lazyOpen = DefaultLazyOpen;

	private String originalPath;

	private FileKey fileKey;

	private RandomAccessFileReader reader;

	private FileMonitorService monitorService;

	private FileMonitorKey changeKey;

	private FileMonitorKey deleteKey;

	private ContentUpdateObservable updater = new ContentUpdateObservable(this);

	private long providerId = providerIdSeed.incrementAndGet();

	private ContentUpdateChecker checker = new ContentUpdateChecker(updater);

	private FileMonitorKey renameKey;

	private static class ContentUpdateChecker implements Observer {

		private volatile long lastTickCount = 0;

		public static final long InvalidTick = -1;

		private final ContentUpdateObservable observable;

		private final Lock lock = new ReentrantLock();

		private final Condition sync = lock.newCondition();

		// package-private
		ContentUpdateChecker(ContentUpdateObservable observable) {
			this.observable = observable;
			observable.addObserver(this);
		}

		@Override
		public void update(Observable o, Object arg) {
			if (o != observable) {
				// not my observable (as this is added to other observable)
				return;
			}
			lock.lock();
			try {
				lastTickCount = (Long) arg;
				sync.signalAll();
			} finally {
				lock.unlock();
			}
		}

		public void await(long tickCount) throws InterruptedException {
			lock.lock();
			try {
				while (lastTickCount != InvalidTick
						&& lastTickCount <= tickCount) {
					sync.await();
				}
			} finally {
				lock.unlock();
			}
		}

		public boolean await(long tickCount, long timeout, TimeUnit unit)
				throws InterruptedException {
			long nanosTimeout = unit.toNanos(timeout);
			lock.lock();
			try {
				while (lastTickCount != InvalidTick
						&& lastTickCount <= tickCount) {
					if (nanosTimeout <= 0L)
						return false;
					nanosTimeout = sync.awaitNanos(nanosTimeout);
				}
			} finally {
				lock.unlock();
			}
			return true;
		}

		public long getLastTickCount() {
			return lastTickCount;
		}

	}

	public File getFile() {
		return file;
	}

	public void setFile(File file) {
		this.file = file;
	}

	public File getCurrentFile() {
		return currentFile;
	}

	public long getInitOffset() {
		return initOffset;
	}

	public void setInitOffset(long initOffset) {
		this.initOffset = initOffset;
	}

	public FileMonitorService getMonitorService() {
		return monitorService;
	}

	public void setMonitorService(FileMonitorService monitorService) {
		this.monitorService = monitorService;
	}

	public int getIdleTimeout() {
		return idleTimeout;
	}

	public void setIdleTimeout(int idleTimeout) {
		this.idleTimeout = idleTimeout;
	}

	public boolean isLazyOpen() {
		return lazyOpen;
	}

	public void setLazyOpen(boolean lazyOpen) {
		this.lazyOpen = lazyOpen;
	}

	@Override
	public LineRecord readLine() throws IOException, InterruptedException {
		while (true) {
			long tickCount = checker.getLastTickCount();
			byte[] line = reader.readLine();
			if (line == null) {
				log.trace("no line read for file {} with tick '{}'", file,
						tickCount);
				// no change after read
				if (changeKey == null) {
					// not real-time monitor, or deleted
					// return null as EOF
					return null;
				} else {
					// real-time monitor
					// wait new change
					checker.await(tickCount);
					log.trace("notify by new change for file {}", file);
				}
			} else {
				return onLineRead(wrapRecord(line, reader.getLoadedLineNumber()));
			}
		}
	}

	private LineRecord wrapRecord(byte[] line, int lineNumber) {
		LineRecord record = new LineRecord();
		record.setLineNum(lineNumber);
		record.setLine(line);
		record.setProviderId(providerId);
		return record;
	}

	@Override
	public LineRecord readLine(long timeout, TimeUnit unit) throws IOException,
			InterruptedException, EOFException {
		long startNanoTime = System.nanoTime();
		long totalNanoTimeout = unit.toNanos(timeout);
		long nanoTimeout = totalNanoTimeout;
		while (nanoTimeout > 0) {
			long tickCount = checker.getLastTickCount();
			// TODO consider no time elapsed here, as well as no sync here
			byte[] line = reader.readLine();
			if (line == null) {
				// no change after read
				if (changeKey == null) {
					// not real-time monitor
					// return EOF
					throw new EOFException();
				} else {
					// real-time monitor
					// wait new change
					nanoTimeout = totalNanoTimeout
							- (System.nanoTime() - startNanoTime);
					if (nanoTimeout > 0
							&& !checker.await(tickCount, nanoTimeout,
									TimeUnit.NANOSECONDS)) {
						// got change notify
						// re-calculate timeout
						nanoTimeout = totalNanoTimeout
								- (System.nanoTime() - startNanoTime);
					}
				}
			} else {
				return onLineRead(wrapRecord(line, reader.getLoadedLineNumber()));
			}
		}
		// timeout
		return null;
	}

	@Override
	public int readLines(Queue<LineRecord> list, int maxSize)
			throws IOException {
		int lines = 0;
		boolean onlyEOF = true;
		while (maxSize > 0) {
			byte[] line = reader.readLine();
			if (line != null) {
				onlyEOF = false;
				LineRecord record = wrapRecord(line,
						reader.getLoadedLineNumber());
				if (!list.offer(record)) {
					// queue is full
					reader.pushBackLine(line);
					return QUEUE_FULL;
				} else {
					maxSize--;
					lines++;
					onLineRead(record);
				}
			} else {
				if (onlyEOF && changeKey == null) {
					// only eof and no change monitor
					return EOF;
				} else {
					break;
				}
			}
		}
		return lines;
	}

	@Override
	public synchronized void close() throws IOException {
		if (reader != null) {
			reader.close();
			reader = null;
			log.trace("close reader for file {}", file);
		}
		closeRenameKey();
		closeDeleteKey();
		closeChangeKey();
	}

	private void closeDeleteKey() {
		if (deleteKey != null) {
			deleteKey.removeMonitorListener(updater);
			deleteKey.close();
			deleteKey = null;
			log.trace("unregister delete monitor key for file {}", file);
		}
	}

	private void closeChangeKey() {
		if (changeKey != null) {
			changeKey.removeMonitorListener(updater);
			changeKey.close();
			changeKey = null;
			log.trace("unregister change monitor key for file {}", file);
		}
	}

	private void closeRenameKey() {
		if (renameKey != null) {
			renameKey.removeMonitorListener(updater);
			renameKey.close();
			renameKey = null;
			log.trace("unregister rename monitor key for file {}", file);
		}
	}

	@Override
	public List<FileContentInfo> getFileContentInfos(boolean realtime)
			throws IOException {
		FileContentInfo info = new FileContentInfo();
		info.setFileKey(fileKey);
		info.setFileName(originalPath);
		info.setCurrentFileName(currentFile == null ? null : currentFile
				.getPath());
		info.setProviderId(providerId);
		info.setOffset(reader.position());
		if (realtime) {
			if (currentFile != null) {
				info.setLastModified(currentFile.lastModified());
				info.setLength(currentFile.length());
			} else {
				info.setLastModified(0L);
				info.setLength(-1);
			}
		}
		return Collections.singletonList(info);
	}

	@Override
	public void init() throws IOException {
		if (file == null) {
			throw new IllegalArgumentException("file is null");
		}
		if (reader != null) {
			throw new IllegalStateException("init() call called");
		}
		reader = new RandomAccessFileReader(file);
		reader.setKeepAlive(idleTimeout);
		reader.open(initOffset, lazyOpen);
		fileKey = FileMonitors.getKeyByFile(file);
		originalPath = file.getPath();
		log.trace("create random access file reader for file {} with key {}",
				file, fileKey);
		currentFile = file;
		if (monitorService != null) {
			deleteKey = monitorService.singleRegister(file,
					FileMonitorMode.DELETE);
			deleteKey.addMonitorListener(new FileMonitorListener() {

				@Override
				public void onChanged(FileMonitorEvent event) {
					// file is deleted from current folder
					log.debug("file {} is deleted from current folder",
							currentFile);
					currentFile = null;
					closeChangeKey();
					checker.update(updater, ContentUpdateChecker.InvalidTick);
				}
			});
			deleteKey.addMonitorListener(updater);
			log.trace("register delete monitor key for file {}", file);
			renameKey = monitorService.singleRegister(file,
					FileMonitorMode.RENAME);
			renameKey.addMonitorListener(new FileMonitorListener() {

				@Override
				public void onChanged(FileMonitorEvent event) {
					// file is renamed in current folder
					log.debug("file {} is renamed as {}", currentFile,
							event.getChangedFile());
					currentFile = event.getChangedFile();
					reader.changeFileName(event.getChangedFile());
				}
			});
			// make sure reader get rename first
			renameKey.addMonitorListener(updater);
			log.trace("register rename monitor key for file {}", file);
			changeKey = monitorService.singleRegister(file,
					FileMonitorMode.MODIFY);
			changeKey.addMonitorListener(updater);
			log.trace("register change monitor key for file {}", file);
		}
	}

	FileKey getUniqueKey() {
		return fileKey;
	}

	@Override
	public void addUpdateObserver(Observer observer) {
		updater.addObserver(observer);
	}

	@Override
	public void removeUpdateObserver(Observer observer) {
		updater.deleteObserver(observer);
	}

	@Override
	public String toString() {
		return String.format("UniqueFile (file=%s%s)", file,
				file.equals(currentFile) ? "" : ",current=" + currentFile);
	}

	@Override
	protected String getProviderName() {
		return currentFile == null ? file.getName() : currentFile.getName();
	}

	@Override
	public String getFileName() {
		return getFile().getName();
	}

}
