package com.hp.it.perf.monitor.files.nio;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;

class PathKeyResolver {

	private static class Versioned<T> {
		private final T data;
		private final int version;

		public Versioned(T data, int version) {
			this.data = data;
			this.version = version;
		}

		public T getData() {
			return data;
		}

		public int getVersion() {
			return this.version;
		}

		public String toString() {
			return data + "(" + version + ")";
		}

	}

	private Map<Path, Versioned<FileKey>> pathKeyMapping = new HashMap<Path, Versioned<FileKey>>();

	private Map<FileKey, Versioned<Path>> keyPathMapping = new HashMap<FileKey, Versioned<Path>>();

	private int currentVersion = 0;

	private Path basePath;

	private boolean searchFolder;

	public PathKeyResolver(PathKeyResolver base) {
		pathKeyMapping.putAll(base.pathKeyMapping);
		keyPathMapping.putAll(base.keyPathMapping);
		currentVersion = base.currentVersion;
		basePath = base.basePath;
		searchFolder = base.searchFolder;
	}

	public PathKeyResolver(Path basePath) {
		this.basePath = basePath;
	}

	public boolean isSearchFolder() {
		return searchFolder;
	}

	public void setSearchFolder(boolean searchFolder) {
		this.searchFolder = searchFolder;
	}

	public int updateVersion() {
		currentVersion++;
		return currentVersion;
	}

	public int getVersion() {
		return currentVersion;
	}

	public FileKey resolvePathKey(Path path) {
		return resolvePathKey(path, currentVersion);
	}

	public FileKey resolvePathKey(Path path, int baseVersion) {
		Versioned<FileKey> vFileKey = pathKeyMapping.get(path);
		if (vFileKey == null || vFileKey.getVersion() < baseVersion) {
			// follow link
			FileKey fileKey = resolvePathKey0(path);
			updatePathKey(path, fileKey);
			return fileKey;
		}
		return vFileKey.getData();
	}

	private void updatePathKey(Path path, FileKey currentKey) {
		Versioned<FileKey> vHistoryKey = pathKeyMapping.get(path);
		Versioned<Path> vHistoryPath = keyPathMapping.get(currentKey);
		pathKeyMapping.remove(path);
		if (vHistoryPath != null) {
			pathKeyMapping.remove(vHistoryPath.getData());
		}
		// not remove until get confirm
		// keyPathMapping.remove(currentKey);
		if (vHistoryKey != null) {
			keyPathMapping.remove(vHistoryKey.getData());
		}
		if (currentKey != null) {
			if (path != null) {
				pathKeyMapping.put(path, new Versioned<FileKey>(currentKey,
						currentVersion));
			}
			keyPathMapping.put(currentKey, new Versioned<Path>(path,
					currentVersion));
		}
	}

	private FileKey resolvePathKey0(Path path) {
		try {
			BasicFileAttributes attr = Files.readAttributes(path,
					BasicFileAttributes.class);
			Object nativeKey = attr.fileKey();
			return new FileKey(nativeKey == null ? path.toRealPath().toString()
					: nativeKey, path);
		} catch (IOException ignored) {
			return null;
		}
	}

	public FileKey resolveCachedPathKey(Path path, int version) {
		Versioned<FileKey> vFileKey = pathKeyMapping.get(path);
		if (vFileKey != null && vFileKey.getVersion() >= version) {
			return vFileKey.getData();
		} else {
			return null;
		}
	}

	public Path resolveCachedPath(FileKey fileKey, int version) {
		Versioned<Path> vPath = keyPathMapping.get(fileKey);
		if (vPath != null && vPath.getVersion() >= version) {
			return vPath.getData();
		} else {
			return null;
		}
	}

	public Path resolvePathByKey(FileKey fileKey, int version) {
		Versioned<Path> vPath = keyPathMapping.get(fileKey);
		if (vPath != null && vPath.getVersion() >= version) {
			return vPath.getData();
		} else if (!searchFolder) {
			// not found, update it to null
			removeDeletedPathKey(fileKey);
			return null;
		}
		DirectoryStream<Path> directoryStream;
		try {
			directoryStream = Files.newDirectoryStream(basePath);
		} catch (IOException ignored) {
			return null;
		}
		try {
			for (Path path : directoryStream) {
				Object nativeKey;
				try {
					BasicFileAttributes attr;
					attr = Files
							.readAttributes(path, BasicFileAttributes.class);
					nativeKey = attr.fileKey();
					if (nativeKey == null) {
						nativeKey = path.toRealPath().toString();
					}
				} catch (IOException ignored) {
					continue;
				}
				FileKey newFileKey = new FileKey(nativeKey, path);
				updatePathKey(path, newFileKey);
				if (isSameKey(newFileKey, fileKey)) {
					return path;
				}
			}
		} finally {
			try {
				directoryStream.close();
			} catch (IOException ignored) {
			}
		}
		// not found
		updatePathKey(null, fileKey);
		return null;
	}

	private void removeDeletedPathKey(FileKey fileKey) {
		Versioned<Path> vPath = keyPathMapping.remove(fileKey);
		if (vPath != null && vPath.getData() != null) {
			Versioned<FileKey> vFileKey = pathKeyMapping.get(vPath.getData());
			if (vFileKey != null && isSameKey(vFileKey.getData(), fileKey)) {
				pathKeyMapping.remove(vPath.getData());
			}
		}
	}

	private static boolean isSameKey(FileKey key1, FileKey key2) {
		return key1 == key2 ? true : (key1 != null && key1.equals(key2));
	}

}
