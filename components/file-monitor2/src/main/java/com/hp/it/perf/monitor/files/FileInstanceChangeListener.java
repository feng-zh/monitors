package com.hp.it.perf.monitor.files;

public interface FileInstanceChangeListener {

	public class FileChangeOption {
		private final FileInstance renameFile;

		public FileChangeOption(FileInstance renameFile) {
			this.renameFile = renameFile;
		}

		public FileChangeOption() {
			this.renameFile = null;
		}

		public boolean isRenameOption() {
			return this.renameFile != null;
		}

		public FileInstance getRenameFile() {
			return this.renameFile;
		}
	}

	public void onFileInstanceCreated(FileInstance instance,
			FileChangeOption changeOption);

	public void onFileInstanceDeleted(FileInstance instance,
			FileChangeOption changeOption);

}
