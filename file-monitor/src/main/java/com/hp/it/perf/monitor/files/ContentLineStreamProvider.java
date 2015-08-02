package com.hp.it.perf.monitor.files;

import java.io.IOException;

public interface ContentLineStreamProvider extends FileContentChangeAware {

	public ContentLineStream open(FileOpenOption option) throws IOException;

}
