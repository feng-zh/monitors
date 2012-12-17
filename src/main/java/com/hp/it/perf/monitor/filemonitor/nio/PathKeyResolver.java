package com.hp.it.perf.monitor.filemonitor.nio;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;

import com.hp.it.perf.monitor.filemonitor.FileKey;

class PathKeyResolver {

	private Map<Path, FileKey> keyMapping = new HashMap<Path, FileKey>(1);

	private PathKeyResolver parent;

	public PathKeyResolver(PathKeyResolver parent) {
		this.parent = parent;
	}

	public PathKeyResolver() {
	}

	public FileKey resolvePathKey(Path path) {
		FileKey fileKey = keyMapping.get(path);
		if (fileKey == null) {
			// follow link
			fileKey = resolvePathKey0(path);
			keyMapping.put(path, fileKey);
			if (parent != null) {
				parent.updatePathKey(path, fileKey);
			}
		}
		return fileKey;
	}

	private FileKey resolvePathKey0(Path path) {
		try {
			BasicFileAttributes attr = Files.readAttributes(path,
					BasicFileAttributes.class);
			Object nativeKey = attr.fileKey();
			return new FileKey(nativeKey == null ? path.toRealPath().toString()
					: nativeKey);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			return null;
		}
	}

	private void updatePathKey(Path path, FileKey fileKey) {
		keyMapping.put(path, fileKey);
	}

	public boolean isSamePath(Path path1, Path path2) {
		FileKey key1 = resolvePathKey(path1);
		FileKey key2 = resolvePathKey(path2);
		return isSameKey(key1, key2);
	}

	public boolean isKeyForPath(FileKey key, Path path) {
		if (key == null) {
			return false;
		}
		FileKey pathKey = resolvePathKey(path);
		return isSameKey(key, pathKey);
	}

	public FileKey getKeyForDeleted(Path path) {
		if (parent == null) {
			return null;
		}
		// get history key for path
		FileKey oldKey = parent.resolvePathKey(path);
		if (oldKey != null) {
			// check if old key exists
			Path newPath = getPathByKey(path.toAbsolutePath().getParent(),
					oldKey);
			if (newPath != null) {
				parent.updatePathKey(newPath, oldKey);
				// new file exists
				return null;
			} else {
				// not exists
				return oldKey;
			}
		} else {
			// no history file key
			return null;
		}
	}

	public Path getPathByKey(Path dir, FileKey fileKey) {
		// TODO heavy operation in remote system?
		DirectoryStream<Path> directoryStream;
		try {
			directoryStream = Files.newDirectoryStream(dir);
		} catch (IOException ignored) {
			return null;
		}
		for (Path path : directoryStream) {
			Object nativeKey;
			try {
				BasicFileAttributes attr;
				attr = Files.readAttributes(path, BasicFileAttributes.class);
				nativeKey = attr.fileKey();
				if (nativeKey == null) {
					nativeKey = path.toRealPath().toString();
				}
			} catch (IOException ignored) {
				continue;
			}
			if (isSameKey(new FileKey(nativeKey), fileKey)) {
				return path;
			}
		}
		// not found
		return null;
	}

	private static boolean isSameKey(FileKey key1, FileKey key2) {
		return key1 == key2 ? true : (key1 != null && key1.equals(key2));
	}

	public FileKey fastResolvePathKey(Path path) {
		FileKey fileKey = keyMapping.get(path);
		if (fileKey == null) {
			if (parent != null) {
				fileKey = parent.fastResolvePathKey(path);
			} else {
				fileKey = resolvePathKey(path);
			}
			keyMapping.put(path, fileKey);
		}
		return fileKey;
	}

}
