package com.hp.it.perf.monitor.filemonitor.nio;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hp.it.perf.monitor.filemonitor.FileKey;

public class ContentBasedFileKeyDetector implements FileKeyDetector {

	private static final Logger log = LoggerFactory
			.getLogger(ContentBasedFileKeyDetector.class);

	private Path watchPath;

	private Map<Path, FileInfoEntry> cachedEntries = new HashMap<Path, FileInfoEntry>();

	private FileRenameProposal renameProposal = new FileRenameProposal() {

		@Override
		public boolean isRenamed(File fromFile, long fromModified,
				long fromLength, File toFile, long toModified, long toLength) {
			if (fromModified == toModified && fromLength == toLength) {
				return true;
			}
			if (fromModified <= toModified && fromLength <= toLength) {
				// maybe renamed during this period
				String fromName = fromFile.getName();
				String toName = toFile.getName();
				// check names have same prefix (usually in log file rotation)
				int i = 0;
				for (int n = Math.min(fromName.length(), toName.length()); i < n; i++) {
					if (fromName.charAt(i) != toName.charAt(i)) {
						break;
					}
				}
				if (i > 0) {
					return true;
				} else {
					return false;
				}
			} else {
				return false;
			}
		}
	};

	private static class ContentSignature {
		private byte[] signature;
		private long offset;
		private int length;

		public void sign(Path name, int offset, int len) {
			signature = new byte[len];
			this.offset = offset;
			RandomAccessFile file = null;
			try {
				file = new RandomAccessFile(name.toFile(), "r");
				if (offset > 0) {
					file.seek(offset);
				}
				int index = 0;
				int size;
				while (index < len
						&& (size = file.read(signature, index, len - index)) >= 0) {
					index += size;
				}
				length = index;
				log.debug(
						"load file '{}' from offset {} with first {} bytes for signature",
						new Object[] { name, offset, length });
			} catch (IOException e) {
				length = -1;
			} finally {
				try {
					file.close();
				} catch (IOException ignored) {
				}
			}
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + length;
			result = prime * result + (int) (offset ^ (offset >>> 32));
			result = prime * result + Arrays.hashCode(signature);
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (!(obj instanceof ContentSignature))
				return false;
			ContentSignature other = (ContentSignature) obj;
			if (length != other.length)
				return false;
			if (offset != other.offset)
				return false;
			if (!Arrays.equals(signature, other.signature))
				return false;
			return true;
		}

		boolean partialMatch(ContentSignature other) {
			if (offset != other.offset) {
				return false;
			}
			if (length == other.length) {
				// not partial match (maybe full or not)
				return false;
			}
			byte[] b1 = signature;
			byte[] b2 = other.signature;
			for (int i = 0, n = Math.min(length, other.length); i < n; i++)
				if (b1[i] != b2[i])
					return false;
			return true;
		}

	}

	private class FileInfoEntry {
		private long modified;
		private long length;
		private long lastUpdated;
		private ContentSignature headSignature;
		private Path name;
		private FileKey nativeKey;

		FileInfoEntry(Path name) {
			this.name = name;
		}

		public String toString() {
			return name + "(" + nativeKey + ")";
		}

		public void setFileAttributes() throws IOException {
			lastUpdated = System.currentTimeMillis();
			BasicFileAttributes attr = Files.readAttributes(name,
					BasicFileAttributes.class);
			modified = attr.lastModifiedTime().toMillis();
			length = attr.size();
			nativeKey = new FileKey(attr.fileKey());
		}

		public void loadSignature() {
			if (headSignature == null) {
				if (System.currentTimeMillis() - lastUpdated > 2000) {
					try {
						setFileAttributes();
					} catch (IOException e) {
						return;
					}
				}
				headSignature = new ContentSignature();
				headSignature.sign(name, 0, (int) Math.min(1024, length));
			} else if (headSignature.length < 1024) {
				// try to reload more
				if (System.currentTimeMillis() - lastUpdated > 2000) {
					try {
						setFileAttributes();
					} catch (IOException e) {
						return;
					}
				}
				if (length >= 1024) {
					headSignature.sign(name, 0, 1024);
				}
			}
		}

		public void loadSignature(FileInfoEntry previous) {
			if (headSignature == null) {
				if (previous == null || previous.headSignature == null) {
					loadSignature();
				} else {
					headSignature = previous.headSignature;
				}
			}
		}

		public boolean equals(Object obj) {
			if (!(obj instanceof FileInfoEntry)) {
				return false;
			}
			FileInfoEntry other = (FileInfoEntry) obj;
			ContentSignature otherHeadSignature = other.headSignature;
			if (headSignature == null || otherHeadSignature == null) {
				// try native file key
				return nativeKey.equals(other.nativeKey);
			} else {
				if (headSignature.equals(otherHeadSignature)) {
					return true;
				} else if (headSignature.partialMatch(otherHeadSignature)) {
					return true;
				} else {
					return false;
				}
			}
		}

		boolean equalToHistory(FileInfoEntry history) {
			if (nativeKey.equals(history.nativeKey)) {
				// native key matches
				loadSignature(history);
				return true;
			}
			if (headSignature == null) {
				if (history.headSignature == null) {
					if (renameProposal.isRenamed(history.name.toFile(),
							history.modified, history.length, name.toFile(),
							modified, length)) {
						loadSignature();
						return true;
					}
				} else {
					loadSignature();
					return equals(history);
				}
			}
			loadSignature();
			if (equals(history)) {
				return true;
			} else if (history.headSignature == null) {
				return renameProposal.isRenamed(history.name.toFile(),
						history.modified, history.length, name.toFile(),
						modified, length);
			} else {
				return false;
			}
		}

	}

	public ContentBasedFileKeyDetector(Path watchPath) {
		this.watchPath = watchPath;
	}

	public void setFileRenameProposal(FileRenameProposal renameProposal) {
		this.renameProposal = renameProposal;
	}

	@Override
	public FileKey detectFileKey(Path path) {
		FileInfoEntry infoEntry = detectFileInfoEntry(path, true);
		return toNativeFileKey(infoEntry);
	}

	private FileInfoEntry detectFileInfoEntry(Path path, boolean addToCache) {
		try {
			FileInfoEntry fileInfo = new FileInfoEntry(path);
			fileInfo.setFileAttributes();
			if (addToCache) {
				cachedEntries.put(path, fileInfo);
			}
			return fileInfo;
		} catch (IOException e) {
			return null;
		}
	}

	// Handle following special cases
	// Poll Mode: Delete, Create (1 => 2)/Rename
	// Poll Mode: Delete, Modify, Create (1,2 => 2',3)/Pair rename
	// Poll Mode: Modify, Create (1 => 1', 2)/Single Rotate
	// Poll Mode: Modify, Modify (1,2 => 1',2')/Rotate
	// Poll Mode: Delete, Modify (1,2 => 2')/Move
	@Override
	public List<WatchEventKeys> detectWatchEvents(List<WatchEvent<?>> events) {
		List<WatchEventKeys> processedEvents = new ArrayList<WatchEventKeys>(
				events.size());
		Path[] eventPaths = new Path[events.size()];
		FileInfoEntry[] historyEntries = new FileInfoEntry[events.size()];
		FileInfoEntry[] currentEntries = new FileInfoEntry[events.size()];
		// pre-load path file key by impacted path
		for (int i = 0, n = events.size(); i < n; i++) {
			WatchEvent<?> event = events.get(i);
			Path eventPath = watchPath.resolve((Path) event.context());
			eventPaths[i] = eventPath;
			historyEntries[i] = cachedEntries.remove(eventPath);
			if (event.kind() != StandardWatchEventKinds.ENTRY_DELETE) {
				currentEntries[i] = detectFileInfoEntry(eventPath, false);
			}
		}
		// update cached keys
		for (int i = 0, n = eventPaths.length; i < n; i++) {
			if (currentEntries[i] != null) {
				cachedEntries.put(eventPaths[i], currentEntries[i]);
			}
		}
		// Processing events
		if (events.size() == 1 && currentEntries[0] != null) {
			if (events.get(0).kind() == StandardWatchEventKinds.ENTRY_MODIFY) {
				if (historyEntries[0] != null) {
					currentEntries[0].loadSignature(historyEntries[0]);
				} else {
					currentEntries[0].loadSignature();
				}
			}
			// only one (no need to check more, because of poll mode)
			processedEvents.add(new WatchEventKeys(events.get(0),
					toNativeFileKey(historyEntries[0]),
					toNativeFileKey(currentEntries[0])));
		} else {
			for (int i = 0, n = events.size(); i < n; i++) {
				WatchEvent<?> event = events.get(i);
				Kind<?> eventKind = event.kind();
				FileInfoEntry currentInfoEntry = currentEntries[i];
				FileInfoEntry historyInfoEntry = historyEntries[i];
				FileKey currentNativeKey = toNativeFileKey(currentInfoEntry);
				FileKey historyNativeKey = toNativeFileKey(historyInfoEntry);
				int existIndex = findSameCurrentFile(currentEntries,
						historyInfoEntry);
				if (eventKind == StandardWatchEventKinds.ENTRY_DELETE) {
					// check if file real deleted or renamed
					if (historyInfoEntry == null || existIndex < 0) {
						// not exist presently, keep this delete event
						processedEvents.add(new WatchEventKeys(event,
								historyNativeKey, null));
					} else {
						// exist
						processedEvents.add(new WatchEventKeys(
								new DelegateWatchEvent(
										WatchEntry.ENTRY_RENAME_FROM, event),
								historyNativeKey,
								toNativeFileKey(currentEntries[existIndex])));
					}
				} else if (eventKind == StandardWatchEventKinds.ENTRY_MODIFY) {
					// check if file still exist
					if (currentInfoEntry == null) {
						// native mode, keep it
						processedEvents.add(new WatchEventKeys(event,
								historyNativeKey, currentNativeKey));
					} else {
						if (existIndex == i) {
							// real modify, keep it
							processedEvents.add(new WatchEventKeys(event,
									historyNativeKey, currentNativeKey));
						} else {
							if (existIndex >= 0) {
								// exist
								// this was renamed to other,
								processedEvents
										.add(new WatchEventKeys(
												new DelegateWatchEvent(
														WatchEntry.ENTRY_RENAME_FROM,
														event),
												historyNativeKey,
												toNativeFileKey(currentEntries[existIndex])));
							} else {
								// not exist
								processedEvents
										.add(new WatchEventKeys(
												new DelegateWatchEvent(
														StandardWatchEventKinds.ENTRY_DELETE,
														event),
												historyNativeKey, null));
							}
							int previousIndex = findSameHistoryFile(
									historyEntries, currentInfoEntry);
							if (previousIndex >= 0) {
								// and some renamed to this
								processedEvents
										.add(new WatchEventKeys(
												new DelegateWatchEvent(
														WatchEntry.ENTRY_RENAME_TO,
														event),
												toNativeFileKey(historyEntries[previousIndex]),
												currentNativeKey));
							} else {
								processedEvents
										.add(new WatchEventKeys(
												new DelegateWatchEvent(
														StandardWatchEventKinds.ENTRY_CREATE,
														event), null,
												currentNativeKey));
							}
						}
					}
				} else if (eventKind == StandardWatchEventKinds.ENTRY_CREATE) {
					// check if file still exist
					if (currentInfoEntry == null) {
						// native mode, keep it
						processedEvents.add(new WatchEventKeys(event,
								historyNativeKey, currentNativeKey));
					} else {
						int previous = findSameHistoryFile(historyEntries,
								currentInfoEntry);
						if (previous >= 0) {
							// previous exists
							FileKey prevNativeKey = toNativeFileKey(historyEntries[previous]);
							processedEvents.add(new WatchEventKeys(
									new DelegateWatchEvent(
											WatchEntry.ENTRY_RENAME_TO, event),
									prevNativeKey, currentNativeKey));
						} else {
							// new created
							processedEvents.add(new WatchEventKeys(event, null,
									currentNativeKey));
						}
					}
				}
			}
		}
		return processedEvents;
	}

	private FileKey toNativeFileKey(FileInfoEntry infoEntry) {
		return infoEntry != null ? infoEntry.nativeKey : null;
	}

	protected int findSameHistoryFile(FileInfoEntry[] historyEntries,
			FileInfoEntry checkCurrent) {
		if (checkCurrent == null) {
			return -1;
		}
		for (int i = 0; i < historyEntries.length; i++) {
			if (historyEntries[i] != null) {
				if (checkCurrent.equalToHistory(historyEntries[i])) {
					return i;
				}
			}
		}
		return -1;
	}

	protected int findSameCurrentFile(FileInfoEntry[] currentEntries,
			FileInfoEntry checkHistory) {
		if (checkHistory == null) {
			return -1;
		}
		for (int i = 0; i < currentEntries.length; i++) {
			if (currentEntries[i] != null) {
				if (currentEntries[i].equalToHistory(checkHistory)) {
					return i;
				}
			}
		}
		return -1;
	}

}
