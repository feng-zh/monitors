package com.hp.it.perf.monitor.files;

import java.io.IOException;

public class SuperSetTestCase extends FolderTestCase {

	@Override
	protected ContentLineStream createLineStream(FileSet folder)
			throws IOException {
		SuperSetContentLineStream lineStream = new SuperSetContentLineStream(
				new FileOpenOptionBuilder().tailMode().build());
		lineStream.addFileSet(folder);
		return lineStream;
	}

	@Override
	public void testNoFileMonitor() throws Exception {
	}

	@Override
	public void testDelete() throws Exception {
	}

}
