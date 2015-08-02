package com.hp.it.perf.monitor.files.nio;

import java.nio.file.Path;

interface FileKeyDetectorFactory {

	public FileKeyDetector create(Path basePath);

}
