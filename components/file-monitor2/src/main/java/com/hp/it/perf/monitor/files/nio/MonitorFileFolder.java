package com.hp.it.perf.monitor.files.nio;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hp.it.perf.monitor.files.CompositeInstanceContentLineStream;
import com.hp.it.perf.monitor.files.ContentLineStream;
import com.hp.it.perf.monitor.files.ContentLineStreamProvider;
import com.hp.it.perf.monitor.files.ContentLineStreamProviderDelegator;
import com.hp.it.perf.monitor.files.DefaultFileStatistics;
import com.hp.it.perf.monitor.files.FileCluster;
import com.hp.it.perf.monitor.files.FileClusterStrategy;
import com.hp.it.perf.monitor.files.FileContentChangedListener;
import com.hp.it.perf.monitor.files.FileInstance;
import com.hp.it.perf.monitor.files.FileInstanceChangeAwareProxy;
import com.hp.it.perf.monitor.files.FileInstanceChangeListener;
import com.hp.it.perf.monitor.files.FileInstanceChangeListener.FileChangeOption;
import com.hp.it.perf.monitor.files.FileOpenOption;
import com.hp.it.perf.monitor.files.FileSet;

class MonitorFileFolder implements FileSet, ContentLineStreamProvider,
		ContentLineStreamProviderDelegator {

	private final FileInstanceChangeAwareProxy proxy = new FileInstanceChangeAwareProxy();

	private List<FileContentChangedListener> listenerList = new CopyOnWriteArrayList<FileContentChangedListener>();

	private final File folder;

	private final List<FileInstance> instanceList = new CopyOnWriteArrayList<FileInstance>();

	private final Map<String, MonitorFileCluster> clusterMap = new ConcurrentHashMap<String, MonitorFileCluster>();

	private final MonitorFileService monitorService;

	private final FileClusterStrategy clusterNameStrategy;

	private final DefaultFileStatistics statistics;

	private MonitorFolderEntry folderWatchEntry;

	private static final Logger log = LoggerFactory
			.getLogger(MonitorFileFolder.class);

	public MonitorFileFolder(File folder,
			FileClusterStrategy clusterNameStrategy,
			DefaultFileStatistics statistics, MonitorFileService monitorService) {
		this.folder = folder;
		this.clusterNameStrategy = clusterNameStrategy;
		this.statistics = statistics;
		this.monitorService = monitorService;
	}

	FileInstance getFileInstance(String name) {
		for (FileInstance instance : instanceList) {
			if (instance.getName().equals(name)) {
				return instance;
			}
		}
		return null;
	}

	void init() throws IOException {
		// make sure list file first
		for (File file : folder.listFiles()) {
			if (file.isFile()) {
				addInstance(makeFileInstance(file));
			}
		}
		this.folderWatchEntry = this.monitorService.registerWatch(this);
	}

	MonitorFileInstance makeFileInstance(File file) {
		String clusterName;
		try {
			clusterName = clusterNameStrategy.getClusterName(file.getName(),
					file.toURI().toURL());
		} catch (MalformedURLException e) {
			throw new RuntimeException(e);
		}
		MonitorFileCluster fileCluster = clusterMap.get(clusterName);
		if (fileCluster == null) {
			fileCluster = new MonitorFileCluster(clusterName, this);
			clusterMap.put(clusterName, fileCluster);
			statistics.fileClusterCount().increment();
			addFileInstanceChangeListener(fileCluster);
		}
		MonitorFileInstance fileInstance = new MonitorFileInstance(
				file.getName(), clusterName, this);
		statistics.fileInstanceCount().increment();
		fileCluster.addFileInstance(fileInstance);
		return fileInstance;
	}

	@Override
	public void addFileInstanceChangeListener(
			FileInstanceChangeListener listener) {
		proxy.addFileInstanceChangeListener(listener);
	}

	@Override
	public void removeFileInstanceChangeListener(
			FileInstanceChangeListener listener) {
		proxy.removeFileInstanceChangeListener(listener);
	}

	@Override
	public List<? extends FileInstance> listInstances() {
		return Collections.unmodifiableList(instanceList);
	}

	@Override
	public Map<String, ? extends FileCluster> listClusters() {
		return Collections.unmodifiableMap(clusterMap);
	}

	@Override
	public ContentLineStream open(FileOpenOption option) throws IOException {
		final CompositeInstanceContentLineStream contentStream = new CompositeInstanceContentLineStream(
				"folder " + folder, option, this) {

			@Override
			protected void onClosing() {
				removeFileInstanceChangeListener(this);
				removeFileContentChangeListener(this);
			}

		};
		addFileInstanceChangeListener(contentStream);
		addFileContentChangeListener(contentStream);
		for (FileInstance instance : instanceList) {
			contentStream.addFileInstance(instance);
		}
		return contentStream;
	}

	public void addFileContentChangeListener(FileContentChangedListener listener) {
		listenerList.add(listener);
	}

	public void removeFileContentChangeListener(
			FileContentChangedListener listener) {
		listenerList.remove(listener);
	}

	void onFileInstanceCreated(FileInstance instance, FileChangeOption option) {
		addInstance(instance);
		proxy.onFileInstanceCreated(instance, option);
	}

	void onFileInstanceDeleted(FileInstance instance, FileChangeOption option) {
		List<FileInstanceChangeListener> removingListeners = preRemoveInstance(instance);
		proxy.onFileInstanceDeleted(instance, option);
		postRemoveInstance(instance, removingListeners);
	}

	protected List<FileInstanceChangeListener> preRemoveInstance(
			FileInstance instance) {
		log.debug("pre-removing file instance {}", instance);
		List<FileInstanceChangeListener> internalListeners = new ArrayList<FileInstanceChangeListener>();
		MonitorFileInstance fileInstance = (MonitorFileInstance) instance;
		internalListeners.add(fileInstance.getChangeListener());
		MonitorFileCluster fileCluster = (MonitorFileCluster) fileInstance
				.getFileCluster();
		fileCluster.removeFileInstance(fileInstance);
		fileInstance.metadata().invalid();
		instanceList.remove(fileInstance);
		if (fileCluster.isEmpty()) {
			clusterMap.remove(fileCluster.getName());
			internalListeners.add(fileCluster);
			statistics.fileClusterCount().decrement();
		}
		statistics.fileInstanceCount().decrement();
		return internalListeners;
	}

	protected void postRemoveInstance(FileInstance instance,
			List<FileInstanceChangeListener> internalListeners) {
		log.debug("post-removing file instance {}", instance);
		for (FileInstanceChangeListener listener : internalListeners) {
			removeFileInstanceChangeListener(listener);
		}
	}

	protected void addInstance(FileInstance newInstance) {
		log.debug("adding file instance {}", newInstance);
		MonitorFileInstance fileInstance = (MonitorFileInstance) newInstance;
		instanceList.add(fileInstance);
		fileInstance.getMetadata(true);
		((MonitorFileCluster) fileInstance.getFileCluster())
				.addFileInstance(fileInstance);
		addFileInstanceChangeListener(fileInstance.getChangeListener());
	}

	void onContentChanged(FileInstance instance) {
		MonitorFileInstance fileInstance = (MonitorFileInstance) instance;
		fileInstance.metadata().markUpdated();
		for (FileContentChangedListener listener : listenerList) {
			listener.onContentChanged(instance);
		}
	}

	File getFolder() {
		return folder;
	}

	MonitorFileCluster getFileCluster(String clusterName) {
		return clusterMap.get(clusterName);
	}

	DefaultFileStatistics getStatistics() {
		return statistics;
	}

	@Override
	public ContentLineStream openLineStream(FileInstance fileInstance,
			FileOpenOption option) throws IOException {
		return ((MonitorFileInstance) fileInstance).open(option);
	}

	FileInstance getOrCreateFileInstance(File file) {
		FileInstance fileInstance = getFileInstance(file.getName());
		if (fileInstance == null) {
			fileInstance = makeFileInstance(file);
		}
		return fileInstance;
	}

}
