package com.hp.it.perf.monitor.files.nio;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.hp.it.perf.monitor.files.ContentLineStream;
import com.hp.it.perf.monitor.files.ContentLineStreamProvider;
import com.hp.it.perf.monitor.files.DefaultFileStatistics;
import com.hp.it.perf.monitor.files.FileCluster;
import com.hp.it.perf.monitor.files.FileContentChangeAwareProxy.SingleFileContentChangeAwareProxy;
import com.hp.it.perf.monitor.files.FileContentChangeListener;
import com.hp.it.perf.monitor.files.FileInstance;
import com.hp.it.perf.monitor.files.FileInstanceChangeAwareProxy.SingleFileInstanceChangeAwareProxy;
import com.hp.it.perf.monitor.files.FileInstanceChangeListener;
import com.hp.it.perf.monitor.files.FileMetadata;
import com.hp.it.perf.monitor.files.FileOpenOption;

class MonitorFileInstance implements FileInstance, ContentLineStreamProvider {

	private final SingleFileInstanceChangeAwareProxy instanceChangeProxy = new SingleFileInstanceChangeAwareProxy(
			this);

	private final SingleFileContentChangeAwareProxy contentChangeProxy = new SingleFileContentChangeAwareProxy(
			this);

	private final File file;

	private final String clusterName;

	private final MonitorFileFolder fileSet;

	private final Map<Object, Object> clientProperites = new ConcurrentHashMap<Object, Object>();

	private MonitorFileMetadata metadata;

	public MonitorFileInstance(String fileName, String clusterName,
			MonitorFileFolder fileSet) {
		this.file = new File(fileSet.getFolder(), fileName);
		this.clusterName = clusterName;
		this.fileSet = fileSet;
		try {
			this.metadata = new MonitorFileMetadata(fileName, file.getPath(),
					file.toURI().toURL());
		} catch (MalformedURLException e) {
			throw new IllegalArgumentException(e);
		}
	}

	FileContentChangeListener getContentChangeListener() {
		return contentChangeProxy;
	}

	@Override
	public void addFileInstanceChangeListener(
			FileInstanceChangeListener listener) {
		instanceChangeProxy.addFileInstanceChangeListener(listener);
	}

	@Override
	public void removeFileInstanceChangeListener(
			FileInstanceChangeListener listener) {
		instanceChangeProxy.removeFileInstanceChangeListener(listener);
	}

	@Override
	public String getName() {
		return file.getName();
	}

	@Override
	public FileCluster getFileCluster() {
		return fileSet.getFileCluster(clusterName);
	}

	String getClusterName() {
		return clusterName;
	}

	@Override
	public MonitorFileFolder getFileSet() {
		return fileSet;
	}

	@Override
	public void putClientProperty(Object key, Object property) {
		if (key == null) {
			throw new NullPointerException("null key");
		}
		if (property == null) {
			clientProperites.remove(key);
		} else {
			clientProperites.put(key, property);
		}
	}

	@Override
	public Object getClientProperty(Object key) {
		return clientProperites.get(key);
	}

	@Override
	public ContentLineStream open(FileOpenOption option) throws IOException {
		Long savedOffset = MonitorFileStream.loadReadOffset(this);
		long offset = option.openOnTail() ? -1 : 0;
		if (savedOffset != null) {
			offset = savedOffset;
		}
		MonitorFileStream stream = new MonitorFileStream(this, offset,
				option.lazyOpen(), option.monitor());
		return stream;
	}

	File getFile() {
		return file;
	}

	FileInstanceChangeListener getInstanceChangeListener() {
		return instanceChangeProxy;
	}

	DefaultFileStatistics getStatistics() {
		return fileSet.getStatistics();
	}

	@Override
	public String toString() {
		return String.format("MonitorFileInstance [file=%s@%s]",
				file.getPath(), hashCode());
	}

	@Override
	public FileMetadata getMetadata(boolean refresh) {
		if (!metadata.isInvalid()) {
			if (metadata.isMarkUpdated() || refresh) {
				metadata.setFileLength(file.length());
				metadata.setLastModifiedDate(file.lastModified());
			}
			return new MonitorFileMetadata(metadata);
		} else {
			return metadata;
		}
	}

	MonitorFileMetadata metadata() {
		return metadata;
	}

	@Override
	public void addFileContentChangeListener(FileContentChangeListener listener) {
		contentChangeProxy.addFileContentChangeListener(listener);
	}

	@Override
	public void removeFileContentChangeListener(
			FileContentChangeListener listener) {
		contentChangeProxy.removeFileContentChangeListener(listener);
	}

}
