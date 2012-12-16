package com.hp.it.perf.monitor.filemonitor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

class FileMonitors {

	public static FileKey getKeyByFile(File file) throws IOException {
		// not follow link
		BasicFileAttributes attr = Files.readAttributes(file.toPath(),
				BasicFileAttributes.class);
		return new FileKey(attr.fileKey());
	}

	public static File getFileByKey(File refFile, FileKey fileKey)
			throws IOException {
		// quick check if key is for this file
		try {
			FileKey key = getKeyByFile(refFile);
			if (isSameFileKey(key, fileKey)) {
				return refFile;
			}
		} catch (IOException ignored) {
			// some error here, fall back to list in dir
		}
		Path dir = refFile.toPath().toAbsolutePath().getParent();
		// TODO heavy operation in remote system?
		for (Path path : Files.newDirectoryStream(dir)) {
			BasicFileAttributes attr = Files.readAttributes(path,
					BasicFileAttributes.class);
			if (isSameFileKey(new FileKey(attr.fileKey()), fileKey)) {
				return path.toFile();
			}
		}
		// not found
		return null;
	}

	public static boolean isKeyForFile(FileKey fileKey, File refFile) {
		try {
			FileKey key = getKeyByFile(refFile);
			if (isSameFileKey(key, fileKey)) {
				return true;
			}
		} catch (IOException ignored) {
			// some error here, fall back to list in dir
		}
		return false;
	}

	private static boolean isSameFileKey(FileKey key1, FileKey key2) {
		return (key1 != null && key1.equals(key2));
	}
}
