package com.hp.it.perf.monitor.filemonitor.nio;

import java.io.Serializable;

class FileKey implements Serializable {

	private static final long serialVersionUID = 1L;

	private transient Object nativeKey;

	private Serializable serializableKey;

	public FileKey(Object fileKey) {
		this.nativeKey = fileKey;
		this.serializableKey = nativeKey.toString();
	}

	public Object getFileKey() {
		return nativeKey;
	}

	public Serializable getSerializableKey() {
		return serializableKey;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((nativeKey == null) ? 0 : nativeKey.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (!(obj instanceof FileKey))
			return false;
		FileKey other = (FileKey) obj;
		if (nativeKey == null) {
			if (other.nativeKey != null)
				return false;
		} else if (!nativeKey.equals(other.nativeKey))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return serializableKey.toString();
	}

}
