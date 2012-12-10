package example;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Iterator;
import java.util.Map;

public class FileMonitors {

	static boolean isFileExist(File file) {
		return file.exists() && file.isFile() && file.canRead();
	}

	static boolean isDirectoryExist(File file) {
		return file.exists() && file.isDirectory() && file.canRead();
	}

	static boolean isExist(File file) {
		return file.exists() && file.canRead();
	}

	static long getFileIndex(File file) {
		try {
			Number attribute = (Number) Files.getAttribute(file.toPath(),
					"unix:ino");
			return attribute.longValue();
		} catch (IOException e) {
			return -1;
		}
	}

	// FIXME how to handle soft and hard link
	static File getFileByIndex(File dir, long fileIndex) throws IOException {
		Iterator<Path> iterator = Files.newDirectoryStream(dir.toPath())
				.iterator();
		while (iterator.hasNext()) {
			Path path = iterator.next();
			try {
				Number attribute = (Number) Files
						.getAttribute(path, "unix:ino");
				if (attribute.longValue() == fileIndex) {
					return path.toFile();
				}
			} catch (IOException e) {
				// continue
			}
		}
		return null;
	}

	static FileMetadata getFileMetadata(File file) throws IOException {
		Map<String, Object> attributes = Files.readAttributes(file.toPath(),
				"unix:*");
		FileMetadata metadata = new FileMetadata();
		metadata.setFileIndex(((Number) attributes.get("ino")).longValue());
		metadata.setLastModified(((FileTime) attributes.get("lastModifiedTime"))
				.toMillis());
		metadata.setLength(((Long) attributes.get("size")).longValue());
		return metadata;
	}
}
