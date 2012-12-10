package example;

public class FileMetadata {
	private long lastModified;
	private long length;
	private long fileIndex;

	protected long getLastModified() {
		return lastModified;
	}

	protected void setLastModified(long lastModified) {
		this.lastModified = lastModified;
	}

	protected long getLength() {
		return length;
	}

	protected void setLength(long length) {
		this.length = length;
	}

	protected long getFileIndex() {
		return fileIndex;
	}

	protected void setFileIndex(long fileIndex) {
		this.fileIndex = fileIndex;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (int) (fileIndex ^ (fileIndex >>> 32));
		result = prime * result + (int) (lastModified ^ (lastModified >>> 32));
		result = prime * result + (int) (length ^ (length >>> 32));
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (!(obj instanceof FileMetadata))
			return false;
		FileMetadata other = (FileMetadata) obj;
		if (fileIndex != other.fileIndex)
			return false;
		if (lastModified != other.lastModified)
			return false;
		if (length != other.length)
			return false;
		return true;
	}

}
