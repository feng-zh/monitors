package com.hp.it.perf.monitor.files.nio;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.hp.it.perf.monitor.files.DefaultFileStatistics;
import com.hp.it.perf.monitor.files.FileClusterStrategy;
import com.hp.it.perf.monitor.files.FileInstance;
import com.hp.it.perf.monitor.files.FileInstanceFactory;
import com.hp.it.perf.monitor.files.FileSet;
import com.hp.it.perf.monitor.files.FileStatistics;

public class MonitorFileFactory implements FileInstanceFactory {

	private MultiMonitorFileService multiMonitorService = new MultiMonitorFileService();

	// initial as default strategy
	private FileClusterStrategy strategy = new FileClusterStrategy() {

		@Override
		public String getClusterName(String baseName, URL url) {
			return baseName;
		}
	};

	private Map<Path, MonitorFileFolder> folders = new ConcurrentHashMap<Path, MonitorFileFolder>();

	private DefaultFileStatistics statistics = new DefaultFileStatistics();

	@Override
	public FileInstance getFileInstance(String path) throws IOException,
			FileNotFoundException {
		File file = new File(path);
		File folder = file.getParentFile();
		MonitorFileFolder fileSet = (MonitorFileFolder) getFileSet(folder
				.getPath());
		FileInstance instance = fileSet.getFileInstance(file.getName());
		if (instance != null) {
			return instance;
		} else {
			throw new FileNotFoundException(file.toString());
		}
	}

	@Override
	public FileSet getFileSet(String path) throws IOException,
			FileNotFoundException {
		// TODO check IO performance
		File requestFolder = new File(path);
		File canonicalFolder = requestFolder.getCanonicalFile();
		MonitorFileFolder fileFolder = folders.get(canonicalFolder);
		if (fileFolder == null && isInclude(canonicalFolder)) {
			fileFolder = new MonitorFileFolder(requestFolder, strategy,
					statistics,
					multiMonitorService.getMonitorService(requestFolder));
			fileFolder.init();
			statistics.fileSetCount().increment();
			return fileFolder;
		} else {
			throw new FileNotFoundException(path);
		}
	}

	private boolean isInclude(File file) {
		// TODO not implemented
		return true;
	}

	@Override
	public void setFileClusterStrategy(FileClusterStrategy strategy) {
		this.strategy = strategy;
	}

	@Override
	public void close() throws IOException {
		multiMonitorService.close();
	}

	@Override
	public FileStatistics getStatistics() {
		return statistics;
	}

}
