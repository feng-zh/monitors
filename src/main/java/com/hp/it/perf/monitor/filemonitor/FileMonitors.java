package com.hp.it.perf.monitor.filemonitor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Iterator;

class FileMonitors {

	public static Object getKeyByFile(File file) throws IOException {
		// not follow link
		BasicFileAttributes attr = Files.readAttributes(file.toPath(),
				BasicFileAttributes.class);
		return attr.fileKey();
	}

	public static File getFileByKey(File refFile, Object fileKey)
			throws IOException {
		// quick check if key is for this file
		try {
			Object key = getKeyByFile(refFile);
			if (isSameFileKey(key, fileKey)) {
				return refFile;
			}
		} catch (IOException ignored) {
			// some error here, fall back to list in dir
		}
		Path dir = refFile.toPath().normalize().getParent();
		// TODO heavy operation in remote system?
		for (Iterator<Path> iterator = dir.iterator(); iterator.hasNext();) {
			Path path = iterator.next();
			BasicFileAttributes attr = Files.readAttributes(path,
					BasicFileAttributes.class);
			if (isSameFileKey(attr.fileKey(), fileKey)) {
				return path.toFile();
			}
		}
		// not found
		return null;
	}

	public static boolean isKeyForFile(Object fileKey, File refFile) {
		try {
			Object key = getKeyByFile(refFile);
			if (isSameFileKey(key, fileKey)) {
				return true;
			}
		} catch (IOException ignored) {
			// some error here, fall back to list in dir
		}
		return false;
	}

	public static boolean isSameFileKey(Object key1, Object key2) {
		return (key1 != null && key1.equals(key2));
	}
}
