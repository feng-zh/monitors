package example;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

class FileMonitorServiceImpl implements FileMonitorService {

	private static final long DELAY = 2; // TODO 2 seconds

	private ScheduledExecutorService scheduler = Executors
			.newScheduledThreadPool(0);

	public FileMonitorKey register(File file, FileMonitorMode monitorMode,
			FileMonitorListener... listeners) throws IOException {
		if (monitorMode == FileMonitorMode.CREATE) {
			return registerMonitor(file, new FileMonitorKeyImpl(file,
					monitorMode) {

				private boolean prevExist = false;

				@Override
				protected boolean isChanged() {
					boolean exist = FileMonitors.isExist(getMonitorFile());
					boolean changed = !prevExist && exist;
					prevExist = exist;
					setChangedFile(exist ? getMonitorFile() : null);
					return changed;
				}

			});
		} else if (monitorMode == FileMonitorMode.RENAME) {
			final long fileIndex = FileMonitors.getFileIndex(file);
			if (fileIndex < 0) {
				throw new IOException("file index is not loaded");
			}
			return registerMonitor(file, new FileMonitorKeyImpl(file,
					monitorMode) {

				private final long currentFileIndex = fileIndex;

				@Override
				protected boolean isChanged() throws IOException {
					File currentFile = getChangedFile();
					if (currentFile == null) {
						// init phase
						setChangedFile(currentFile);
					}
					boolean changed = false;
					long fileIndex = FileMonitors.getFileIndex(currentFile);
					if (fileIndex != currentFileIndex) {
						// find renamed file in same folder
						File newNameFile = FileMonitors.getFileByIndex(
								currentFile.getAbsoluteFile().getParentFile(),
								currentFileIndex);
						if (newNameFile == null) {
							// file is removed or moved out of folder or folder
							// has error
							// treat as no change
							currentFile = null;
						} else {
							setChangedFile(newNameFile);
							changed = true;
						}
					}
					return changed;
				}

			});
		} else if (monitorMode == FileMonitorMode.CHANGE) {
			final FileMetadata fileMetadata = FileMonitors
					.getFileMetadata(file);
			return registerMonitor(file, new FileMonitorKeyImpl(file,
					monitorMode) {

				private FileMetadata currentFileMetadata = fileMetadata;

				@Override
				protected boolean isChanged() throws IOException {
					File currentFile = getChangedFile();
					if (currentFile == null) {
						// init phase
						setChangedFile(currentFile);
					}
					FileMetadata oldFileMetadata = currentFileMetadata;
					currentFileMetadata = FileMonitors
							.getFileMetadata(getMonitorFile());
					return !currentFileMetadata.equals(oldFileMetadata);
				}
			});
		}
		throw new UnsupportedOperationException();
	}

	private FileMonitorKey registerMonitor(File file,
			FileMonitorKeyImpl montorKey) {
		ScheduledFuture<?> future = scheduler.scheduleWithFixedDelay(montorKey,
				DELAY, DELAY, TimeUnit.SECONDS);
		montorKey.setScheduledFuture(future);
		return montorKey;
	}

	@Override
	public void close() throws IOException {
		scheduler.shutdown();
	}

}
