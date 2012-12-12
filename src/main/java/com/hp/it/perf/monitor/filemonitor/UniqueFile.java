package com.hp.it.perf.monitor.filemonitor;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Observer;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

// This is not thread safe
// Provider for one file with same i-node (file key)
// low level provider for file created
public class UniqueFile implements FileContentProvider {

	private File file;

	private RandomAccessFileReader reader;

	private Object fileKey;

	private FileMonitorService monitorService;

	private FileMonitorKey changeKey;

	private ContentUpdateObservable updater = new ContentUpdateObservable(this);

	private ContentUpdateChecker checker = updater.getUpdateChecker();

	public File getFile() {
		return file;
	}

	public void setFile(File file) {
		this.file = file;
	}

	public FileMonitorService getMonitorService() {
		return monitorService;
	}

	public void setMonitorService(FileMonitorService monitorService) {
		this.monitorService = monitorService;
	}

	@Override
	public LineRecord readLine() throws IOException, InterruptedException {
		while (true) {
			long tickCount = checker.getLastTickCount();
			byte[] line = reader.readLine();
			if (line == null) {
				// no change after read
				if (changeKey == null) {
					// not real-time monitor
					// return null as EOF
					return null;
				} else {
					// real-time monitor
					// wait new change
					checker.await(tickCount);
				}
			} else {
				return wrapRecord(line, reader.getLineNumber());
			}
		}
	}

	private LineRecord wrapRecord(byte[] line, int lineNumber) {
		LineRecord record = new LineRecord();
		record.setLineNum(lineNumber);
		record.setLine(line);
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
				return wrapRecord(line, reader.getLineNumber());
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
				LineRecord record = wrapRecord(line, reader.getLineNumber());
				if (!list.offer(record)) {
					// queue is full
					reader.pushBackLine(line);
					break;
				} else {
					maxSize--;
					lines++;
				}
			} else {
				if (onlyEOF && changeKey == null) {
					// only eof and no change monitor
					return -1;
				} else {
					break;
				}
			}
		}
		return lines;
	}

	@Override
	public void close() throws IOException {
		reader.close();
		if (changeKey != null) {
			changeKey.removeMonitorListener(updater);
			changeKey.close();
		}
	}

	@Override
	public List<FileContentInfo> getFileContentInfos() throws IOException {
		FileContentInfo info = new FileContentInfo();
		info.setFileKey(fileKey);
		if (changeKey != null) {
			info.setFileName(changeKey.getCurrentFile().getAbsolutePath());
			info.setLastModified(changeKey.getLastUpdated());
			info.setLength(changeKey.getLength());
		} else {
			File currentFile = FileMonitors.getFileByKey(file, fileKey);
			if (currentFile == null) {
				info.setFileName(file.getAbsolutePath());
				info.setLastModified(-1);
				// removed
				info.setLength(-1);
			} else {
				info.setFileName(currentFile.getAbsolutePath());
				info.setLastModified(currentFile.lastModified());
				info.setLength(currentFile.length());
			}
		}
		info.setOffset(reader.position());
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
		fileKey = FileMonitors.getKeyByFile(file);
		if (monitorService != null) {
			changeKey = monitorService.singleRegister(file, FileMonitorMode.CHANGE);
			changeKey.addMonitorListener(updater);
		} else {
			// FIXME mock update event if no monitor service
		}
	}

	Object getUniqueKey() {
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

	// @Override
	// public long skip(long bytes) throws IOException {
	// return reader.skip(bytes);
	// }

}
