package com.hp.it.perf.monitor.filemonitor;

import java.io.EOFException;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Observer;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FolderContentProvider implements FileContentProvider {

	private File folder;

	private FileFilter filter;

	private ContentUpdateObserver updateNotifier = new ContentUpdateObserver();

	private ContentUpdateObservable contentUpdateObservable = new ContentUpdateObservable(
			this);

	private Map<FileKey, UniqueFile> files = new HashMap<FileKey, UniqueFile>();

	private Queue<FileContentProvider> lastUpdates = new LinkedList<FileContentProvider>();

	private FileMonitorService monitorService;

	private boolean tailMode;

	private static final Logger log = LoggerFactory
			.getLogger(FolderContentProvider.class);

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
			FileContentProvider file = lastUpdates.poll();
			if (file == null) {
				log.trace("start take updated file on folder {}", folder);
				file = updateNotifier.take();
				if (file == this) {
					// just notified for check again
					log.trace("notify by folder change to refetch");
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
				lastUpdates.offer(file);
				return line;
			} else if (len == -1) {
				// TODO EOF of file
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
			FileContentProvider file = lastUpdates.poll();
			if (file == null) {
				file = updateNotifier.poll(nanoTimeout, TimeUnit.NANOSECONDS);
				nanoTimeout = totalNanoTimeout
						- (System.nanoTime() - startNanoTime);
				if (nanoTimeout <= 0 || file == null) {
					break;
				}
				if (file == this) {
					// just notified for check again
					log.trace("notify by folder change to refetch");
					continue;
				}
			}
			// TODO concurrent issue
			int len = file.readLines(container, 1);
			if (len == 1) {
				LineRecord line = container.poll();
				lastUpdates.offer(file);
				return line;
			} else if (len == -1) {
				// TODO EOF of file
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
		while (maxSize > 0) {
			if (file == null) {
				file = lastUpdates.poll();
			}
			if (file == null) {
				file = updateNotifier.poll();
				if (file == this) {
					// just notified for check again
					log.trace("notify by folder change to refetch");
					continue;
				}
			}
			if (file == null) {
				break;
			}
			// reset version
			// TODO concurrent issue
			int len = file.readLines(list, maxSize);
			if (len == -1) {
				// TODO EOF of file
			} else if (len > 0) {
				totalLen += len;
				maxSize -= len;
				if (maxSize <= 0) {
					// still not finished
					lastUpdates.offer(file);
				}
			} else {
				// no data loaded
			}
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
		FileMonitorKey folderCreateKey = monitorService.folderRegister(folder,
				FileMonitorMode.CREATE);
		folderCreateKey.addMonitorListener(new FileMonitorListener() {

			@Override
			public void onChanged(FileMonitorEvent event) {
				File updatedFile = event.getChangedFile();
				try {
					addMonitorFile(updatedFile, 0);
					log.trace("folder entry is added for file: {} with key {}",
							updatedFile, event.getChangedFileKey());
					contentUpdateObservable.onChanged(event);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}

		});
		log.trace("register create monitor key for folder {}", folder);
		FileMonitorKey folderChangeKey = monitorService.folderRegister(folder,
				FileMonitorMode.MODIFY);
		folderChangeKey.addMonitorListener(new FileMonitorListener() {

			@Override
			public void onChanged(FileMonitorEvent event) {
				FileKey fileKey = event.getChangedFileKey();
				UniqueFile f = files.get(fileKey);
				log.trace("folder is changed by file: {} for key {}", f,
						fileKey);
				if (f != null) {
					contentUpdateObservable.onChanged(event);
				}
			}
		});
		folderChangeKey.addMonitorListener(contentUpdateObservable);
		log.trace("register modify monitor key for folder {}", folder);
		FileMonitorKey folderRemoveKey = monitorService.folderRegister(folder,
				FileMonitorMode.DELETE);
		folderRemoveKey.addMonitorListener(new FileMonitorListener() {

			@Override
			public void onChanged(FileMonitorEvent event) {
				UniqueFile removedFile = files.remove(event.getChangedFileKey());
				log.trace("folder entry is deleted for file: {} with key {}",
						removedFile == null ? null : removedFile.getFile(),
						event.getChangedFileKey());
				if (removedFile != null) {
					contentUpdateObservable.onChanged(event);
				}
			}
		});
		contentUpdateObservable.addObserver(updateNotifier);
		log.trace("register delete monitor key for folder {}", folder);
		for (File f : folder.listFiles(filter)) {
			if (!f.isFile()) {
				continue;
			}
			addMonitorFile(f, -1);
		}
	}

	@Override
	public void close() throws IOException {
		contentUpdateObservable.deleteObserver(updateNotifier);
		for (UniqueFile f : files.values()) {
			f.close();
		}
		files.clear();
	}

	@Override
	public List<FileContentInfo> getFileContentInfos(boolean realtime)
			throws IOException {
		List<FileContentInfo> infos = new ArrayList<FileContentInfo>(
				files.size());
		for (UniqueFile f : files.values()) {
			infos.addAll(f.getFileContentInfos(realtime));
		}
		return infos;
	}

	@Override
	public void addUpdateObserver(Observer observer) {
		contentUpdateObservable.addObserver(observer);
	}

	@Override
	public void removeUpdateObserver(Observer observer) {
		contentUpdateObservable.deleteObserver(observer);
	}

	private UniqueFile addMonitorFile(File file, long initOffset)
			throws IOException {
		UniqueFile uniqueFile = new UniqueFile();
		uniqueFile.setFile(file);
		uniqueFile.setMonitorService(monitorService);
		uniqueFile.addUpdateObserver(updateNotifier);
		uniqueFile.setInitOffset(initOffset);
		log.trace("init single file {}", file);
		boolean initSuccess = false;
		try {
			uniqueFile.init();
			initSuccess = true;
		} finally {
			if (!initSuccess) {
				uniqueFile.removeUpdateObserver(updateNotifier);
				try {
					uniqueFile.close();
				} catch (IOException ignored) {
				}
			}
		}
		FileKey fileKey = uniqueFile.getUniqueKey();
		files.put(fileKey, uniqueFile);
		lastUpdates.offer(uniqueFile);
		log.debug("single file {} is registered with unique key {}", file,
				fileKey);
		return uniqueFile;
	}

	// @Override
	// public long skip(long bytes) throws IOException {
	// // TODO Auto-generated method stub
	// return 0;
	// }
	//
	// @Override
	// public long available() throws IOException {
	// // TODO Auto-generated method stub
	// return 0;
	// }

}
