package com.hp.it.perf.monitor.files;

import java.io.IOException;

public interface ContentLineStreamProviderDelegator {

	public ContentLineStream openLineStream(FileInstance fileInstance,
			FileOpenOption option) throws IOException;

}
