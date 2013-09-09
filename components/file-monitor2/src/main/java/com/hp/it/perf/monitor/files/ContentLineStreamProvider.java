package com.hp.it.perf.monitor.files;

import java.io.IOException;

public interface ContentLineStreamProvider {

	public ContentLineStream open(FileOpenOption option) throws IOException;

}
