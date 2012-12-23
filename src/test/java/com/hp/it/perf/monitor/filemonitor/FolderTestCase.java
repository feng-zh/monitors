package com.hp.it.perf.monitor.filemonitor;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import java.io.File;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.hp.it.perf.monitor.filemonitor.nio.MultiMonitorService;

public class FolderTestCase {

	private FileMonitorService monitorService;

	private FileTeseBuilder setup;

	@Before
	public void setUp() throws Exception {
		monitorService = new MultiMonitorService();
		setup = new FileTeseBuilder(UniqueFileTestCase.class.getName());
	}

	@After
	public void tearDown() throws Exception {
		monitorService.close();
		setup.close();
		setup.printThreads();
	}

	@Test(timeout = 5000)
	public void testFileRead() throws Exception {
		FolderContentProvider folder = new FolderContentProvider();
		File testFile1 = setup.copy(new File("src/test/data/sample_file1.txt"));
		File testFile2 = setup.copy(new File("src/test/data/sample_file1.txt"),
				"sample_file2.txt");
		folder.setMonitorService(monitorService);
		folder.setFolder(testFile1.getParentFile());
		folder.setTailMode(true);
		folder.init();
		String data = "newline";
		setup.echo(data, testFile1);
		LineRecord line = folder.readLine();
		assertThat(line, is(notNullValue()));
		assertThat(line.getLine(), is(setup.line(data)));
		assertThat(line.getLineNum(), is(equalTo(1)));
		setup.echo(data, testFile2);
		LineRecord line2 = folder.readLine();
		assertThat(line2, is(notNullValue()));
		assertThat(line2.getLine(), is(setup.line(data)));
		assertThat(line2.getLineNum(), is(equalTo(1)));
		folder.close();
	}

}
