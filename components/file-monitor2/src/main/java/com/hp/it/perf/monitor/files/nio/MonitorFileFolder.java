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
import com.hp.it.perf.monitor.files.FileContentChangeAwareProxy;
import com.hp.it.perf.monitor.files.FileContentChangeListener;
import com.hp.it.perf.monitor.files.FileInstance;
import com.hp.it.perf.monitor.files.FileInstanceChangeAwareProxy;
import com.hp.it.perf.monitor.files.FileInstanceChangeListener;
import com.hp.it.perf.monitor.files.FileInstanceChangeListener.FileChangeOption;
import com.hp.it.perf.monitor.files.FileOpenOption;
import com.hp.it.perf.monitor.files.FileSet;

class MonitorFileFolder implements FileSet, ContentLineStreamProvider,
		ContentLineStreamProviderDelegator {

	private final FileInstanceChangeAwareProxy instanceChangeProxy = new FileInstanceChangeAwareProxy();

	private final FileContentChangeAwareProxy contentChangeProxy = new FileContentChangeAwareProxy();

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
			fileCluster = new MonitorFileCluster(clusterName, folder, this);
			clusterMap.put(clusterName, fileCluster);
			statistics.fileClusterCount().increment();
			addFileInstanceChangeListener(fileCluster);
			addFileContentChangeListener(fileCluster);
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
		instanceChangeProxy.addFileInstanceChangeListener(listener);
	}

	@Override
	public void removeFileInstanceChangeListener(
			FileInstanceChangeListener listener) {
		instanceChangeProxy.removeFileInstanceChangeListener(listener);
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
				"folder " + folder, option, this, this);
		for (FileInstance instance : instanceList) {
			contentStream.addFileInstance(instance);
		}
		return contentStream;
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

	void onFileInstanceCreated(FileInstance instance, FileChangeOption option) {
		addInstance(instance);
		instanceChangeProxy.onFileInstanceCreated(instance, option);
	}

	void onFileInstanceDeleted(FileInstance instance, FileChangeOption option) {
		List<FileInstanceChangeListener> removingListeners = preRemoveInstance(instance);
		instanceChangeProxy.onFileInstanceDeleted(instance, option);
		postRemoveInstance(instance, removingListeners);
	}

	protected List<FileInstanceChangeListener> preRemoveInstance(
			FileInstance instance) {
		log.debug("pre-removing file instance {}", instance);
		List<FileInstanceChangeListener> internalListeners = new ArrayList<FileInstanceChangeListener>();
		MonitorFileInstance fileInstance = (MonitorFileInstance) instance;
		removeFileContentChangeListener(fileInstance.getContentChangeListener());
		internalListeners.add(fileInstance.getInstanceChangeListener());
		MonitorFileCluster fileCluster = (MonitorFileCluster) fileInstance
				.getFileCluster();
		fileCluster.removeFileInstance(fileInstance);
		removeFileContentChangeListener(fileCluster);
		fileInstance.metadata().invalid();
		contentChangeProxy.removeFileInstance(instance);
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
		contentChangeProxy.addFileInstance(fileInstance);
		addFileInstanceChangeListener(fileInstance.getInstanceChangeListener());
		addFileContentChangeListener(fileInstance.getContentChangeListener());
	}

	void onContentChanged(FileInstance instance) {
		MonitorFileInstance fileInstance = (MonitorFileInstance) instance;
		fileInstance.metadata().markUpdated();
		contentChangeProxy.onContentChanged(instance);
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

	@Override
	public String getPath() {
		return folder.getPath();
	}

}
