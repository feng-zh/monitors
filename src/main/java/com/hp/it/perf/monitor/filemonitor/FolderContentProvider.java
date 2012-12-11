package com.hp.it.perf.monitor.filemonitor;

import java.io.EOFException;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Observer;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class FolderContentProvider implements FileContentProvider {

	private File folder;

	private FileFilter filter;

	private ContentUpdateObserver<UniqueFile> contentUpdateObserver = new ContentUpdateObserver<UniqueFile>(
			UniqueFile.class);

	private ContentUpdateObservable<FolderContentProvider> contentUpdateObservable = new ContentUpdateObservable<FolderContentProvider>(
			this);

	private Map<Object, UniqueFile> files = new HashMap<Object, UniqueFile>();

	private FileMonitorService monitorService;

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

	@Override
	public LineRecord readLine() throws IOException, InterruptedException {
		BlockingQueue<LineRecord> container = new ArrayBlockingQueue<LineRecord>(
				1);
		while (true) {
			UniqueFile updated = contentUpdateObserver.take();
			int len = updated.readLines(container, 1);
			if (len == 1) {
				return container.poll();
			} else if (len == -1) {
				// TODO EOF of file
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
			UniqueFile updated = contentUpdateObserver.poll(nanoTimeout,
					TimeUnit.NANOSECONDS);
			nanoTimeout = totalNanoTimeout
					- (System.nanoTime() - startNanoTime);
			if (nanoTimeout <= 0 || updated == null) {
				break;
			}
			int len = updated.readLines(container, 1);
			if (len == 1) {
				return container.poll();
			} else if (len == -1) {
				// TODO EOF of file
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
		UniqueFile updated;
		while (maxSize > 0 && (updated = contentUpdateObserver.poll()) != null) {
			// reset version
			int len = updated.readLines(list, maxSize);
			if (len == -1) {
				// TODO EOF of file
			} else {
				totalLen += len;
				maxSize -= len;
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
		FileMonitorKey folderChangeKey = monitorService.register(folder,
				FileMonitorMode.ENTRY_CREATE, FileMonitorMode.ENTRY_CHANGE,
				FileMonitorMode.ENTRY_REMOVE);
		folderChangeKey.addMonitorListener(new FileMonitorListener() {

			@Override
			public void onChanged(FileMonitorEvent event) {
				notifyFolderChange(event);
			}
		});
		folderChangeKey.addMonitorListener(contentUpdateObservable);
		for (File f : folder.listFiles(filter)) {
			UniqueFile uniqueFile = new UniqueFile();
			uniqueFile.setFile(f);
			uniqueFile.setMonitorService(monitorService);
			boolean initSuccess = false;
			try {
				uniqueFile.init();
				initSuccess = true;
			} finally {
				if (!initSuccess) {
					try {
						uniqueFile.close();
					} catch (IOException ignored) {
					}
				}
			}
			files.put(uniqueFile.getUniqueKey(), uniqueFile);
		}
	}

	protected void notifyFolderChange(FileMonitorEvent event) {
		// if new file created
		if (event.getMode() == FileMonitorMode.ENTRY_CREATE) {
			UniqueFile uniqueFile = new UniqueFile();
			uniqueFile.setFile(event.getChangedFile());
			uniqueFile.setMonitorService(monitorService);
			try {
				uniqueFile.init();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				try {
					uniqueFile.close();
				} catch (IOException ignored) {
				}
			}
			files.put(uniqueFile.getUniqueKey(), uniqueFile);
		} else if (event.getMode() == FileMonitorMode.ENTRY_REMOVE) {
			for (int i = 0; i < files.size(); i++) {
				UniqueFile f = files.get(i);
				if (FileMonitors.isSameFileKey(f.getUniqueKey(),
						event.getChangedFileKey())) {
					files.remove(i);
					break;
				}
			}
		} else if (event.getMode() == FileMonitorMode.ENTRY_CHANGE) {
			Object fileKey = event.getChangedFileKey();
			UniqueFile f = files.get(fileKey);
			if (f != null) {
				contentUpdateObserver.update(o, arg);
			}
		}
	}

	@Override
	public void close() throws IOException {
		for (UniqueFile f : files.values()) {
			f.close();
		}
		files.clear();
	}

	@Override
	public List<FileContentInfo> getFileContentInfos() throws IOException {
		List<FileContentInfo> infos = new ArrayList<FileContentInfo>(
				files.size());
		for (UniqueFile f : files.values()) {
			infos.addAll(f.getFileContentInfos());
		}
		return infos;
	}

	@Override
	public void addUpdateObserver(Observer observer) {
		contentUpdateObserver.
	}

	@Override
	public void removeUpdateObserver(Observer observer) {
		// TODO Auto-generated method stub

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
