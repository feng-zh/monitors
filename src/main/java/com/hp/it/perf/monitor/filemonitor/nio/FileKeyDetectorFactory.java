package com.hp.it.perf.monitor.filemonitor.nio;

import java.nio.file.Path;

public interface FileKeyDetectorFactory {

	public FileKeyDetector create(Path basePath);

}
