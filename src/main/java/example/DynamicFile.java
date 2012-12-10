package example;

import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class DynamicFile implements FileContentProvider {

	private File file;

	private Charset charset = Charset.defaultCharset();

	private LineNumberReader reader;

	private File currentFile;

	private FileMonitorService monitorService;

	private FileMonitorKey createMonitorKey;

	private FileMonitorKey changeMonitorKey;

	private FileMonitorKey renameMonitorKey;

	private Lock changeLock = new ReentrantLock();

	private Condition changeCondition = changeLock.newCondition();

	public File getFile() {
		return file;
	}

	public void setFile(File file) {
		this.file = file;
	}

	public Charset getCharset() {
		return charset;
	}

	public void setCharset(Charset charset) {
		this.charset = charset;
	}

	public FileMonitorService getMonitorService() {
		return monitorService;
	}

	public void setMonitorService(FileMonitorService monitorService) {
		this.monitorService = monitorService;
	}

	@Override
	public LineRecord readLine() throws IOException, InterruptedException {
		if (createMonitorKey != null) {
			createMonitorKey.await();
		}
		initReader();
		String line;
		while ((line = reader.readLine()) == null) {
			// no line record
			changeLock.lock();
			try {
				changeCondition.await();
			} finally {
				changeLock.unlock();
			}
		}
		return wrapRecord(line, reader.getLineNumber());
	}

	private LineRecord wrapRecord(String line, int lineNumber) {
		LineRecord record = new LineRecord();
		record.setLineNum(lineNumber);
		record.setLine(line);
		return record;
	}

	private void initReader() throws IOException {
		if (reader == null) {
			reader = new LineNumberReader(new NewLineLatchReader(
					new InputStreamReader(new FileInputStream(file),
							charset.newDecoder())));
		}
	}

	@Override
	public LineRecord readLine(long timeout, TimeUnit unit) throws IOException,
			InterruptedException, EOFException {
		long nanoTimeout = unit.toNanos(timeout);
		long current = System.nanoTime();
		if (createMonitorKey == null
				|| createMonitorKey.await(nanoTimeout, TimeUnit.NANOSECONDS) != null) {
			nanoTimeout -= (System.nanoTime() - current);
			initReader();
			String line;
			while ((line = reader.readLine()) == null) {
				// no line record
				changeLock.lock();
				try {
					if (nanoTimeout > 0) {
						nanoTimeout = changeCondition.awaitNanos(nanoTimeout);
					}
					if (nanoTimeout <= 0) {
						// no timeout or timeout in await
						return null;
					}
				} finally {
					changeLock.unlock();
				}
			}
			return wrapRecord(line, reader.getLineNumber());
		} else {
			// timeout
			return null;
		}
	}

	@Override
	public FileContentInfo getFileInfo() throws IOException {
		FileContentInfo info = new FileContentInfo();
		info.setFileContentKey(file.getName());
		info.setFileName(currentFile.toURI().toString());
		if (createMonitorKey != null && reader == null) {
			info.setLastModified(0);
			info.setLength(-1);
			info.setFileIndex(-1);
		} else {
			info.setLastModified(currentFile.lastModified());
			info.setLength(currentFile.length());
			info.setFileIndex(FileMonitors.getFileIndex(currentFile));
		}
		return info;
	}

	@Override
	public void init() throws IOException {
		if (file == null) {
			throw new IllegalArgumentException("file is null");
		}
		if (monitorService == null) {
			throw new IllegalArgumentException("monitorService is null");
		}
		if (reader != null) {
			throw new IllegalStateException("init() call called");
		}
		currentFile = file;
		createMonitorKey = monitorService
				.register(file, FileMonitorMode.CREATE);
		renameMonitorKey = monitorService.register(file,
				FileMonitorMode.RENAME, new FileMonitorListener() {

					@Override
					public void onChanged(FileMonitorEvent event) {
						notifyFileRenamed(event.getChangedFile());
					}
				});
		changeMonitorKey = monitorService.register(file,
				FileMonitorMode.CHANGE, new FileMonitorListener() {

					@Override
					public void onChanged(FileMonitorEvent event) {
						notifyFileContentChanged(event.getChangedFile());
					}
				});
	}

	protected void notifyFileContentChanged(File changedFile) {
		changeLock.lock();
		try {
			changeCondition.signalAll();
		} finally {
			changeLock.unlock();
		}
	}

	protected void notifyFileRenamed(File changedFile) {
		currentFile = changedFile;
	}

	@Override
	public void close() throws IOException {
		if (reader != null) {
			reader.close();
		}
		if (changeMonitorKey != null) {
			changeMonitorKey.close();
		}
		if (renameMonitorKey != null) {
			renameMonitorKey.close();
		}
	}

}
