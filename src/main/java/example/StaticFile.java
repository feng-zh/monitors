package example;

import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;

public class StaticFile implements FileContentProvider {

	protected File file;

	protected Charset charset = Charset.defaultCharset();

	protected transient LineNumberReader reader;

	protected FileMonitorKey createMonitorKey;

	protected FileMonitorService monitorService;

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
	public synchronized LineRecord readLine() throws InterruptedException,
			IOException {
		if (createMonitorKey != null) {
			createMonitorKey.await();
		}
		initReader();
		String line = reader.readLine();
		if (line == null) {
			return null;
		} else {
			return wrapRecord(line, reader.getLineNumber());
		}
	}

	private LineRecord wrapRecord(String line, int lineNumber) {
		LineRecord record = new LineRecord();
		record.setLineNum(lineNumber);
		record.setLine(line);
		return record;
	}

	@Override
	public synchronized LineRecord readLine(long timeout, TimeUnit unit)
			throws IOException, EOFException, InterruptedException {
		if (createMonitorKey == null
				|| createMonitorKey.await(timeout, unit) != null) {
			LineRecord lineRecord = readLine();
			if (lineRecord == null) {
				throw new EOFException();
			} else {
				return lineRecord;
			}
		} else {
			return null;
		}
	}

	@Override
	public synchronized FileContentInfo getFileInfo() throws IOException {
		FileContentInfo info = new FileContentInfo();
		info.setFileContentKey(file.getName());
		info.setFileName(file.toURI().toString());
		if (createMonitorKey != null && reader == null) {
			info.setLastModified(0);
			info.setLength(-1);
			info.setFileIndex(-1);
		} else {
			info.setLastModified(file.lastModified());
			info.setLength(file.length());
			info.setFileIndex(FileMonitors.getFileIndex(file));
		}
		return info;
	}

	@Override
	public synchronized void close() throws IOException {
		if (createMonitorKey != null) {
			createMonitorKey.close();
		}
		if (reader != null) {
			reader.close();
		}
	}

	@Override
	public synchronized void init() throws IOException {
		if (file == null) {
			throw new IllegalArgumentException("file is null");
		}
		if (FileMonitors.isFileExist(file)) {
			if (reader == null) {
				throw new IllegalStateException("init() call called");
			}
			initReader();
		} else {
			if (createMonitorKey != null) {
				throw new IllegalStateException("init() call called");
			}
			if (monitorService == null) {
				throw new IllegalArgumentException("monitor service is null");
			}
			// start file monitor
			createMonitorKey = monitorService.register(file,
					FileMonitorMode.CREATE);
		}
	}

	private synchronized void initReader() throws IOException {
		if (reader == null) {
			this.reader = new LineNumberReader(new InputStreamReader(
					new FileInputStream(file), charset.newDecoder()));
			if (createMonitorKey != null) {
				createMonitorKey.close();
				createMonitorKey = null;
			}
		}
	}

}
