package com.hp.ts.perf.incubator.filesearch;

import java.io.IOException;

public interface ContentBlockSequentialAccess extends
		ContentBlockSearchMeasurable {

	public ContentBlock nextBlock() throws IOException;

}
