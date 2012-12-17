package com.hp.it.perf.monitor.filemonitor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

class FileMonitors {

	public static FileKey getKeyByFile(File file) throws IOException {
		// not follow link
		Path path = file.toPath();
		BasicFileAttributes attr = Files.readAttributes(path,
				BasicFileAttributes.class);
		Object nativeKey = attr.fileKey();
		return new FileKey(nativeKey == null ? path.toRealPath().toString()
				: nativeKey);
	}

}
