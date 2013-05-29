package com.hp.ts.perf.incubator.filesearch;

import java.io.IOException;

public interface ContentBlockRandomAccess extends ContentBlockSearchMeasurable {

	public ContentBlock locateBlock(long position) throws IOException;

	public long length();
}
