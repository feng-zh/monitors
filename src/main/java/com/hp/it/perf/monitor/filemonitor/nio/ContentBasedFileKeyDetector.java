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
import java.util.zip.Adler32;

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
		private long offset = -1;
		private int length = -1;
		private long checksum;

		public void sign(Path name, int offset, int len, boolean resign) {
			if (signature != null && !resign) {
				// has content, and not resign required
				if (length >= len) {
					// content is not changed
					return;
				}
			}
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
				Adler32 adler32Checksum = new Adler32();
				adler32Checksum.update(signature, 0, length);
				checksum = adler32Checksum.getValue();
				log.debug(
						"load file '{}' from offset {} with first {} bytes for signature - checksum {}",
						new Object[] { name, offset, length,
								Long.toHexString(checksum) });
			} catch (IOException e) {
				length = -1;
			} finally {
				try {
					file.close();
				} catch (IOException ignored) {
				}
			}
		}

		public boolean isPartial() {
			return length < 1024;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + length;
			result = prime * result + (int) (offset ^ (offset >>> 32));
			result = prime * result + (int) checksum;
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
			if (checksum != other.checksum)
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

	private class FileInfoEntry implements Comparable<FileInfoEntry> {
		private long modified;
		private long length;
		private long lastUpdated;
		private ContentSignature headSignature;
		private Path name;
		private FileKey nativeKey;
		private boolean regularFile;

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
			regularFile = attr.isRegularFile();
		}

		public boolean isRegularFile() {
			return regularFile;
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
				headSignature.sign(name, 0, (int) Math.min(1024, length), true);
			} else if (headSignature.isPartial()) {
				// try to reload more
				if (System.currentTimeMillis() - lastUpdated > 2000) {
					try {
						setFileAttributes();
					} catch (IOException e) {
						return;
					}
				}
				headSignature
						.sign(name, 0, (int) Math.min(1024, length), false);
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

		@Override
		public int compareTo(FileInfoEntry other) {
			int result = Long.compare(this.modified, other.modified);
			return result == 0 ? Long.compare(this.length, other.length)
					: result;
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
			if (addToCache && fileInfo.isRegularFile()) {
				cachedEntries.put(path, fileInfo);
			}
			return fileInfo;
		} catch (IOException e) {
			return null;
		}
	}

	private static class FileInfoMatcher {
		private int[] historyIndexMatchedByCurrent;
		private int[] currentIndexMatchedByHistory;
		private int[] possibleCurrentMatched;
		private int[] possibleHistoryMatched;
		private int size;
		private static final int UNKNOWN = -1;
		private static final int INVALID = -2;
		private static final int UNMATCHED = -3;

		public FileInfoMatcher(int size) {
			this.size = size;
			historyIndexMatchedByCurrent = new int[size + 1];
			currentIndexMatchedByHistory = new int[size + 1];
			possibleCurrentMatched = new int[size];
			possibleHistoryMatched = new int[size];
			for (int i = 0; i < size; i++) {
				historyIndexMatchedByCurrent[i] = UNKNOWN;
				currentIndexMatchedByHistory[i] = UNKNOWN;
				possibleCurrentMatched[i] = UNKNOWN;
				possibleHistoryMatched[i] = UNKNOWN;
			}
			historyIndexMatchedByCurrent[size] = 0;
			currentIndexMatchedByHistory[size] = 0;
		}

		public final void setMatched(int history, int current) {
			historyIndexMatchedByCurrent[current] = history;
			historyIndexMatchedByCurrent[size]++;
			currentIndexMatchedByHistory[history] = current;
			currentIndexMatchedByHistory[size]++;
		}

		public final boolean historyUnknown(int history) {
			return currentIndexMatchedByHistory[history] == UNKNOWN;
		}

		public final boolean currentUnknown(int current) {
			return historyIndexMatchedByCurrent[current] == UNKNOWN;
		}

		public final void markHistoryInvalid(int history) {
			currentIndexMatchedByHistory[history] = INVALID;
			currentIndexMatchedByHistory[size]++;
		}

		public final void markCurrentInvalid(int current) {
			historyIndexMatchedByCurrent[current] = INVALID;
			historyIndexMatchedByCurrent[size]++;
		}

		public void setPossibleMatched(int history, int current) {
			if (possibleHistoryMatched[history] == UNKNOWN) {
				possibleHistoryMatched[history] = current;
			} else {
				possibleHistoryMatched[history] = INVALID;
			}
			if (possibleCurrentMatched[current] == UNKNOWN) {
				possibleCurrentMatched[current] = history;
			} else {
				possibleCurrentMatched[current] = INVALID;
			}
		}

		public void setUniqueMatched() {
			for (int i = 0; i < size; i++) {
				int current = possibleHistoryMatched[i];
				if (current >= 0) {
					int history = possibleCurrentMatched[current];
					if (history == i) {
						setMatched(history, current);
					}
				}
			}
		}

		public void markCurrentUnmatched(int current) {
			historyIndexMatchedByCurrent[current] = UNMATCHED;
			historyIndexMatchedByCurrent[size]++;
		}

		public void markHistoryUnmatched(int history) {
			currentIndexMatchedByHistory[history] = UNMATCHED;
			currentIndexMatchedByHistory[size]++;
		}

		public String toString() {
			return ("H:" + Arrays.toString(historyIndexMatchedByCurrent))
					+ (";C:" + Arrays.toString(currentIndexMatchedByHistory));
		}

		public int getMatchedHistory(int current) {
			return historyIndexMatchedByCurrent[current];
		}

		public int getMatchedCurrent(int history) {
			return currentIndexMatchedByHistory[history];
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
		Path[] eventPaths = new Path[events.size()];
		FileInfoEntry[] historyEntries = new FileInfoEntry[events.size()];
		FileInfoEntry[] currentEntries = new FileInfoEntry[events.size()];
		// smart check history-current match situation
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
			if (currentEntries[i] != null && currentEntries[i].isRegularFile()) {
				cachedEntries.put(eventPaths[i], currentEntries[i]);
			}
		}
		FileInfoMatcher matcher = createMatcher(events, historyEntries,
				currentEntries);
		log.trace("Matcher status: {}", matcher);
		List<WatchEventKeys> eventKeys = evaluteEvents(matcher, events,
				historyEntries, currentEntries);
		return eventKeys;
	}

	private FileInfoMatcher createMatcher(List<WatchEvent<?>> events,
			FileInfoEntry[] historyEntries, FileInfoEntry[] currentEntries) {
		// step 0: prepare helper data structure
		int size = events.size();
		FileInfoMatcher matcher = new FileInfoMatcher(size);
		for (int i = 0; i < size; i++) {
			if (historyEntries[i] == null || !historyEntries[i].isRegularFile()) {
				matcher.markHistoryInvalid(i);
			}
			if (currentEntries[i] == null || !currentEntries[i].isRegularFile()) {
				matcher.markCurrentInvalid(i);
			}
		}
		// step 1: match all native keys matched
		// prepare current native keys
//		Map<FileKey, Integer> keyIdxes = new HashMap<FileKey, Integer>();
//		for (int i = 0; i < size; i++) {
//			if (matcher.currentUnknown(i)) {
//				keyIdxes.put(currentEntries[i].nativeKey, i);
//			}
//		}
//		// do native key match
//		for (int i = 0; i < size; i++) {
//			if (matcher.historyUnknown(i)) {
//				Integer cIdx = keyIdxes.get(historyEntries[i].nativeKey);
//				if (cIdx != null) {
//					matcher.setMatched(i, cIdx.intValue());
//				}
//			}
//		}
		// step 2: match possible same modified/size (only unique)
		// possible not load signature at all (for many rotated files)
		for (int i = 0; i < size; i++) {
			if (matcher.currentUnknown(i)) {
				FileInfoEntry cInfo = currentEntries[i];
				for (int j = 0; j < size; j++) {
					if (matcher.historyUnknown(j)) {
						FileInfoEntry hInfo = historyEntries[j];
						if (cInfo.compareTo(hInfo) == 0) {
							matcher.setPossibleMatched(j, i);
						}
					}
				}
			}
		}
		matcher.setUniqueMatched();
		// step 3: match signature
		// prepare current signatures for not matched
		Map<ContentSignature, Integer> signIdxes = new HashMap<ContentSignature, Integer>();
		int historyPartialSignCount = 0;
		int historyNoSignCount = 0;
		int currentNoSignCount = 0;
		for (int i = 0; i < size; i++) {
			FileInfoEntry cInfo = currentEntries[i];
			if (matcher.currentUnknown(i)) {
				if (cInfo.headSignature == null) {
					cInfo.loadSignature();
				}
				if (cInfo.headSignature != null) {
					signIdxes.put(cInfo.headSignature, i);
				} else {
					currentNoSignCount++;
				}
			}
		}
		for (int i = 0; i < size; i++) {
			FileInfoEntry hInfo = historyEntries[i];
			if (matcher.historyUnknown(i)) {
				if (hInfo.headSignature != null) {
					ContentSignature hSign = hInfo.headSignature;
					if (!hSign.isPartial()) {
						// full signature in history
						Integer cIdx = signIdxes.remove(hSign);
						if (cIdx != null) {
							matcher.setMatched(i, cIdx.intValue());
						}
					} else {
						historyPartialSignCount++;
					}
				} else {
					historyNoSignCount++;
				}
			}
		}
		// step 3: match partial signature
		// rare case for partial sign in history
		if (historyPartialSignCount > 0) {
			for (int i = 0; i < size; i++) {
				FileInfoEntry hInfo = historyEntries[i];
				if (matcher.historyUnknown(i) && hInfo.headSignature != null
						&& hInfo.headSignature.isPartial()) {
					for (Map.Entry<ContentSignature, Integer> entry : signIdxes
							.entrySet()) {
						if (entry.getKey() != null
								&& matcher.currentUnknown(entry.getValue())
								&& entry.getKey().partialMatch(
										hInfo.headSignature)) {
							// partial match found
							matcher.setMatched(i, entry.getValue().intValue());
							break;
						}
					}
				}
			}
		}
		// step 4: mark unmatched for signature exists
		// current if all history has signature
		if (historyNoSignCount == 0) {
			for (int i = 0; i < size; i++) {
				if (matcher.currentUnknown(i)) {
					if (currentEntries[i].headSignature != null) {
						matcher.markCurrentUnmatched(i);
					}
				}
			}
		} else if (currentNoSignCount == 0) {
			for (int i = 0; i < size; i++) {
				if (matcher.historyUnknown(i)) {
					if (historyEntries[i].headSignature != null) {
						matcher.markHistoryUnmatched(i);
					}
				}
			}
		}
		// step 5: check rename proposal
		for (int i = 0; i < size; i++) {
			if (matcher.historyUnknown(i)) {
				FileInfoEntry hInfo = historyEntries[i];
				for (int j = 0; j < size; j++) {
					if (matcher.currentUnknown(j)) {
						FileInfoEntry cInfo = currentEntries[j];
						if (renameProposal.isRenamed(hInfo.name.toFile(),
								hInfo.modified, hInfo.length,
								cInfo.name.toFile(), cInfo.modified,
								cInfo.length)) {
							matcher.setMatched(i, j);
							break;
						}
					}
				}
			}
		}
		// step 6: pass loaded signature for matched
		for (int i = 0; i < size; i++) {
			int history = matcher.getMatchedHistory(i);
			if (history >= 0) {
				if (history == i) {
					// changed self (make sure signature is loaded)
					currentEntries[i].loadSignature(historyEntries[history]);
				} else if (historyEntries[history].headSignature != null) {
					currentEntries[i].loadSignature(historyEntries[history]);
				}
			}
		}
		return matcher;
	}

	private List<WatchEventKeys> evaluteEvents(FileInfoMatcher matcher,
			List<WatchEvent<?>> events, FileInfoEntry[] historyEntries,
			FileInfoEntry[] currentEntries) {
		List<WatchEventKeys> list = new ArrayList<WatchEventKeys>(
				events.size() * 2);
		for (int index = 0, n = events.size(); index < n; index++) {
			WatchEvent<?> event = events.get(index);
			Kind<?> eventKind = event.kind();
			FileInfoEntry currentInfoEntry = currentEntries[index];
			if (currentInfoEntry != null && !currentInfoEntry.isRegularFile()) {
				// ignore non-file event
				continue;
			}
			FileInfoEntry historyInfoEntry = historyEntries[index];
			FileKey currentNativeKey = toNativeFileKey(currentInfoEntry);
			FileKey historyNativeKey = toNativeFileKey(historyInfoEntry);
			int historyValue = matcher.getMatchedHistory(index);
			int currentValue = matcher.getMatchedCurrent(index);
			if (eventKind == StandardWatchEventKinds.ENTRY_DELETE) {
				// check if file real deleted or renamed
				if (historyInfoEntry == null || currentValue < 0) {
					// not exist presently, keep this delete event
					list.add(new WatchEventKeys(event, historyNativeKey, null));
				} else {
					// exist
					list.add(new WatchEventKeys(new DelegateWatchEvent(
							WatchEntry.ENTRY_RENAME_FROM, event),
							historyNativeKey,
							toNativeFileKey(currentEntries[currentValue])));
				}
			} else if (eventKind == StandardWatchEventKinds.ENTRY_MODIFY) {
				// check if file still exist
				if (currentInfoEntry == null) {
					// native mode, keep it
					list.add(new WatchEventKeys(event, historyNativeKey,
							currentNativeKey));
				} else {
					if (currentValue == index) {
						// real modify, keep it
						list.add(new WatchEventKeys(event, historyNativeKey,
								currentNativeKey));
					} else {
						if (currentValue >= 0) {
							// exist
							// this was renamed to other,
							list.add(new WatchEventKeys(
									new DelegateWatchEvent(
											WatchEntry.ENTRY_RENAME_FROM, event),
									historyNativeKey,
									toNativeFileKey(currentEntries[currentValue])));
						} else {
							// not exist
							list.add(new WatchEventKeys(
									new DelegateWatchEvent(
											StandardWatchEventKinds.ENTRY_DELETE,
											event), historyNativeKey, null));
						}
						if (historyValue >= 0) {
							// and some renamed to this
							list.add(new WatchEventKeys(
									new DelegateWatchEvent(
											WatchEntry.ENTRY_RENAME_TO, event),
									toNativeFileKey(historyEntries[historyValue]),
									currentNativeKey));
						} else {
							list.add(new WatchEventKeys(
									new DelegateWatchEvent(
											StandardWatchEventKinds.ENTRY_CREATE,
											event), null, currentNativeKey));
						}
					}
				}
			} else if (eventKind == StandardWatchEventKinds.ENTRY_CREATE) {
				// check if file still exist
				if (currentInfoEntry == null) {
					// native mode, keep it
					list.add(new WatchEventKeys(event, historyNativeKey,
							currentNativeKey));
				} else {
					if (historyValue >= 0) {
						// previous exists
						FileKey prevNativeKey = toNativeFileKey(historyEntries[historyValue]);
						list.add(new WatchEventKeys(new DelegateWatchEvent(
								WatchEntry.ENTRY_RENAME_TO, event),
								prevNativeKey, currentNativeKey));
					} else {
						// new created
						list.add(new WatchEventKeys(event, null,
								currentNativeKey));
					}
				}
			}
		}
		return list;
	}

	private FileKey toNativeFileKey(FileInfoEntry infoEntry) {
		return infoEntry != null ? infoEntry.nativeKey : null;
	}

}
