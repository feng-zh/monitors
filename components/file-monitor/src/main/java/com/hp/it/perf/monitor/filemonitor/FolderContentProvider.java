package com.hp.it.perf.monitor.filemonitor;

import java.io.EOFException;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Observer;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FolderContentProvider extends ManagedFileContentProvider implements
		FileContentProvider, FolderContentProviderMXBean {

	private File folder;

	private FileFilter filter;

	private ContentUpdateObserver filesUpdateNotifier = new ContentUpdateObserver();

	private ContentUpdateObservable folderUpdateNotifier = new ContentUpdateObservable(
			this);

	private Map<Object, FileContentProvider> fileMap = new ConcurrentHashMap<Object, FileContentProvider>();

	private List<FileContentProvider> files = new ArrayList<FileContentProvider>();

	private Deque<FileContentProvider> lastUpdateFiles = new LinkedList<FileContentProvider>();

	private FileMonitorService monitorService;

	private boolean tailMode;

	private static final Logger log = LoggerFactory
			.getLogger(FolderContentProvider.class);

	private FileMonitorKey folderRemoveKey;

	private FileMonitorKey folderChangeKey;

	private FileMonitorKey folderCreateKey;

	private FileMonitorKey folderRenameKey;

	public File getFolder() {
		return folder;
	}

	public void setFolder(File folder) {
		this.folder = folder;
	}

	public FileMonitorService getMonitorService() {
		return monitorService;
	}

	public void setMonitorService(FileMonitorService monitorService) {
		this.monitorService = monitorService;
	}

	public FileFilter getFilter() {
		return filter;
	}

	public void setFilter(FileFilter filter) {
		this.filter = filter;
	}

	public boolean isTailMode() {
		return tailMode;
	}

	public void setTailMode(boolean tailMode) {
		this.tailMode = tailMode;
	}

	@Override
	public LineRecord readLine() throws IOException, InterruptedException {
		BlockingQueue<LineRecord> container = new ArrayBlockingQueue<LineRecord>(
				1);
		while (true) {
			FileContentProvider file = lastUpdateFiles.poll();
			if (file == null) {
				log.trace("start take updated file on folder {}", folder);
				file = filesUpdateNotifier.take();
				if (file == this) {
					file = null;
					// just notified for check again
					log.trace("notify by folder change to refetch");
					// check if no files
					// TODO no moniotr return null?
					if (files.isEmpty()) {
						log.debug("no file monitor on {}", folder);
						return null;
					}
					continue;
				}
				log.trace(
						"fetch one line for updated file content provider {}",
						file);
			}
			// TODO concurrent issue
			int len = file.readLines(container, 1);
			log.trace("read line count {}", len);
			if (len == 1) {
				LineRecord line = container.poll();
				lastUpdateFiles.offerFirst(file);
				return onLineRead(line);
			} else if (len == EOF) {
				// EOF of file (maybe file is deleted)
				file = null;
				// check if any file monitor
				// TODO no moniotr return null?
				if (files.isEmpty()) {
					log.debug("no file monitor on {}", folder);
					return null;
				}
			} else if (len == QUEUE_FULL) {
				throw new AssertionError("should not queue full");
			} else {
				// no data loaded
			}
		}
	}

	@Override
	public LineRecord readLine(long timeout, TimeUnit unit) throws IOException,
			InterruptedException, EOFException {
		long startNanoTime = System.nanoTime();
		long totalNanoTimeout = unit.toNanos(timeout);
		long nanoTimeout = totalNanoTimeout;
		BlockingQueue<LineRecord> container = new ArrayBlockingQueue<LineRecord>(
				1);
		while (nanoTimeout > 0) {
			FileContentProvider file = lastUpdateFiles.poll();
			if (file == null) {
				file = filesUpdateNotifier.poll(nanoTimeout,
						TimeUnit.NANOSECONDS);
				nanoTimeout = totalNanoTimeout
						- (System.nanoTime() - startNanoTime);
				if (nanoTimeout <= 0 || file == null) {
					break;
				}
				if (file == this) {
					file = null;
					// just notified for check again
					log.trace("notify by folder change to refetch");
					// TODO no moniotr return null?
					if (files.isEmpty()) {
						log.debug("no file monitor on {}", folder);
						return null;
					}
					continue;
				}
			}
			// TODO concurrent issue
			int len = file.readLines(container, 1);
			if (len == 1) {
				LineRecord line = container.poll();
				lastUpdateFiles.offerFirst(file);
				return onLineRead(line);
			} else if (len == EOF) {
				// TODO EOF of file
				// TODO no moniotr return null?
				if (files.isEmpty()) {
					log.debug("no file monitor on {}", folder);
					return null;
				}
			} else if (len == QUEUE_FULL) {
				throw new AssertionError("should not queue full");
			} else {
				// no data loaded
			}
			nanoTimeout = totalNanoTimeout
					- (System.nanoTime() - startNanoTime);
		}
		// timeout
		return null;
	}

	@Override
	public int readLines(Queue<LineRecord> list, int maxSize)
			throws IOException {
		int totalLen = 0;
		FileContentProvider file = null;
		RecordedQueue<LineRecord> recordedQueue = new RecordedQueue<LineRecord>(
				list);
		while (maxSize > 0) {
			if (file == null) {
				file = lastUpdateFiles.poll();
			}
			if (file == null) {
				file = filesUpdateNotifier.poll();
				if (file == this) {
					file = null;
					// just notified for check again
					log.trace("notify by folder change to refetch");
					continue;
				}
			}
			if (file == null) {
				log.trace("no file updated");
				break;
			}
			// reset version
			// TODO concurrent issue
			int len = file.readLines(recordedQueue, maxSize);
			if (len == EOF) {
				// TODO EOF of file
				log.debug("file is reaching EOF: {}", file);
				file = null;
			} else if (len == QUEUE_FULL) {
				// queue is full
				// file not loaded finished
				lastUpdateFiles.offerFirst(file);
				for (LineRecord line : recordedQueue.getRecorded()) {
					onLineRead(line);
				}
				return QUEUE_FULL;
			} else if (len > 0) {
				totalLen += len;
				maxSize -= len;
				if (maxSize <= 0) {
					// still not finished
					lastUpdateFiles.offerFirst(file);
				}
			} else {
				// no data loaded
				file = null;
			}
		}
		for (LineRecord line : recordedQueue.getRecorded()) {
			onLineRead(line);
		}
		return totalLen;
	}

	@Override
	public synchronized void init() throws IOException {
		if (folder == null) {
			throw new IllegalArgumentException("folder is null");
		}
		if (!files.isEmpty()) {
			throw new IllegalStateException("init() call called");
		}
		if (!folder.isDirectory()) {
			throw new IOException("invalid folder: " + folder);
		}
		folderRemoveKey = monitorService.folderRegister(folder,
				FileMonitorMode.DELETE);
		folderRemoveKey.addMonitorListener(new FileMonitorListener() {

			@Override
			public void onChanged(FileMonitorEvent event) {
				FileContentProvider removedFile = fileMap.remove(event
						.getChangedFileKey());
				files.remove(removedFile);
				log.trace("folder entry is deleted for file: {} with key {}",
						removedFile == null ? null : removedFile,
						event.getChangedFileKey());
				if (removedFile != null) {
					onSubProviderRemoved(removedFile);
					folderUpdateNotifier.onChanged(event);
				}
			}
		});
		log.trace("register delete monitor key for folder {}", folder);
		folderRenameKey = monitorService.folderRegister(folder,
				FileMonitorMode.RENAME);
		folderRenameKey.addMonitorListener(new FileMonitorListener() {

			@Override
			public void onChanged(FileMonitorEvent event) {
				FileContentProvider renamedFile = fileMap.get(event
						.getChangedFileKey());
				log.info("folder entry is renamed as {}",
						event.getChangedFile());
				if (renamedFile != null) {
					onSubProviderRemoved(renamedFile);
					if (filter == null || filter.accept(event.getChangedFile())) {
						// continue use this one
						onSubProviderCreated(renamedFile);
					} else {
						// remove it now
						files.remove(renamedFile);
						fileMap.remove(((UniqueFile) renamedFile)
								.getUniqueFileKey());
						try {
							renamedFile.close();
						} catch (IOException e) {
							log.warn(
									"close folder entry fail for file: {}: {}",
									new Object[] { event.getChangedFile(), e });
						}
					}
				}
			}

		});
		log.trace("register rename monitor key for folder {}", folder);
		folderCreateKey = monitorService.folderRegister(folder,
				FileMonitorMode.CREATE);
		folderCreateKey.addMonitorListener(new FileMonitorListener() {

			@Override
			public void onChanged(FileMonitorEvent event) {
				File updatedFile = event.getChangedFile();
				try {
					if (filter == null || filter.accept(updatedFile)) {
						addMonitorFile(updatedFile, 0);
						log.trace(
								"folder entry is added for file: {} with key {}",
								updatedFile, event.getChangedFileKey());
					}
				} catch (IOException e) {
					log.warn(
							"folder entry added fail for file: {} with key {}: {}",
							new Object[] { updatedFile,
									event.getChangedFileKey(), e });
				}
				folderUpdateNotifier.onChanged(event);
			}

		});
		log.trace("register create monitor key for folder {}", folder);
		folderChangeKey = monitorService.folderRegister(folder,
				FileMonitorMode.MODIFY);
		folderChangeKey.addMonitorListener(new FileMonitorListener() {

			@Override
			public void onChanged(FileMonitorEvent event) {
				Object fileKey = event.getChangedFileKey();
				FileContentProvider f = fileMap.get(fileKey);
				log.trace("folder is changed by file: {} for key {}", f,
						fileKey);
				if (f != null) {
					folderUpdateNotifier.onChanged(event);
				}
			}
		});
		folderChangeKey.addMonitorListener(folderUpdateNotifier);
		log.trace("register modify monitor key for folder {}", folder);
		// self update register (for new file added etc)
		folderUpdateNotifier.addObserver(filesUpdateNotifier);
		for (File f : folder.listFiles()) {
			if (!f.isFile() || (filter != null && !filter.accept(f))) {
				continue;
			}
			// TODO constant value
			addMonitorFile(f, -1);
		}
	}

	@Override
	public void close() throws IOException {
		// remove self update
		folderUpdateNotifier.deleteObserver(filesUpdateNotifier);
		List<FileContentProvider> toClearFiles;
		// to avoid deadlock
		synchronized (this) {
			toClearFiles = new ArrayList<FileContentProvider>(files);
			files.clear();
			fileMap.clear();
		}
		closeCreateKey();
		closeChangeKey();
		closeRenameKey();
		closeDeleteKey();
		for (FileContentProvider f : toClearFiles) {
			f.removeUpdateObserver(filesUpdateNotifier);
			f.close();
		}
	}

	private void closeDeleteKey() {
		if (folderRemoveKey != null) {
			folderRemoveKey.close();
			folderRemoveKey = null;
			log.trace("unregister delete monitor key for folder {}", folder);
		}
	}

	private void closeChangeKey() {
		if (folderChangeKey != null) {
			folderChangeKey.removeMonitorListener(folderUpdateNotifier);
			folderChangeKey.close();
			folderChangeKey = null;
			log.trace("unregister change monitor key for folder {}", folder);
		}
	}

	private void closeRenameKey() {
		if (folderRenameKey != null) {
			folderRenameKey.close();
			folderRenameKey = null;
			log.trace("unregister rename monitor key for folder {}", folder);
		}
	}

	private void closeCreateKey() {
		if (folderCreateKey != null) {
			folderCreateKey.close();
			folderCreateKey = null;
			log.trace("unregister create monitor key for folder {}", folder);
		}
	}

	@Override
	public List<FileContentInfo> getFileContentInfos(boolean realtime)
			throws IOException {
		List<FileContentInfo> infos = new ArrayList<FileContentInfo>(
				files.size());
		for (FileContentProvider f : files) {
			infos.addAll(f.getFileContentInfos(realtime));
		}
		return infos;
	}

	@Override
	public void addUpdateObserver(Observer observer) {
		folderUpdateNotifier.addObserver(observer);
	}

	@Override
	public void removeUpdateObserver(Observer observer) {
		folderUpdateNotifier.deleteObserver(observer);
	}

	private synchronized UniqueFile addMonitorFile(File file, long initOffset)
			throws IOException {
		UniqueFile uniqueFile = new UniqueFile();
		uniqueFile.setFile(file);
		uniqueFile.setMonitorService(monitorService);
		uniqueFile.addUpdateObserver(filesUpdateNotifier);
		uniqueFile.setInitOffset(initOffset);
		log.trace("init single file {}", file);
		boolean initSuccess = false;
		try {
			uniqueFile.init();
			initSuccess = true;
		} finally {
			if (!initSuccess) {
				uniqueFile.removeUpdateObserver(filesUpdateNotifier);
				try {
					uniqueFile.close();
				} catch (IOException ignored) {
				}
			}
		}
		onSubProviderCreated(uniqueFile);
		files.add(uniqueFile);
		if (monitorService != null) {
			Object uniqueFileKey = monitorService.getKeyByFile(file);
			uniqueFile.setUniqueFileKey(uniqueFileKey);
			fileMap.put(uniqueFileKey, uniqueFile);
			log.debug("single file {} is registered with unique key {}", file,
					uniqueFileKey);
		}
		if (initOffset >= 0) {
			// need to check if read required (<0 means at end of file)
			lastUpdateFiles.offer(uniqueFile);
		}
		return uniqueFile;
	}

	@Override
	public String toString() {
		return String.format("FolderContentProvider (folder=%s)", folder);
	}

	@Override
	public String[] getFileNames() {
		List<String> fileList = new ArrayList<String>(files.size());
		for (FileContentProvider file : files) {
			if (file instanceof UniqueFile) {
				fileList.add(((UniqueFile) file).getFileName());
			}
		}
		return fileList.toArray(new String[fileList.size()]);
	}

	@Override
	protected String getProviderName() {
		return getFolder().getPath();
	}

	@Override
	protected Collection<FileContentProvider> providers() {
		return files;
	}

	@Override
	public String getFolderName() {
		return getFolder().getName();
	}

}