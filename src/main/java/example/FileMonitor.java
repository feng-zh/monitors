package example;

import java.io.File;
import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;

import com.sun.nio.file.SensitivityWatchEventModifier;

public class FileMonitor {

	public static void main(String[] args) throws Exception {
		// watchDir(".");
		File file = new File("test.txt");
		FileMonitors.getFileMetadata(file);
	}

	public static void watchDir(String dir) throws Exception {

		// create the watchService
		final WatchService watchService = createWatchService();

		// register the directory with the watchService
		// for create, modify and delete events
		final Path path = Paths.get(dir);
		path.register(watchService, new WatchEvent.Kind<?>[] {
				StandardWatchEventKinds.ENTRY_CREATE,
				StandardWatchEventKinds.ENTRY_MODIFY,
				StandardWatchEventKinds.ENTRY_DELETE },
				SensitivityWatchEventModifier.HIGH);

		// start an infinite loop
		while (true) {

			// remove the next watch key
			final WatchKey key = watchService.take();

			// get list of events for the watch key
			for (WatchEvent<?> watchEvent : key.pollEvents()) {

				// get the filename for the event
				final WatchEvent<Path> ev = (WatchEvent<Path>) watchEvent;
				final Path filename = ev.context();

				// get the kind of event (create, modify, delete)
				final Kind<?> kind = watchEvent.kind();

				// print it out
				System.out.println(kind + ": " + filename);
			}

			// reset the key
			boolean valid = key.reset();

			// exit loop if the key is not valid
			// e.g. if the directory was deleted
			if (!valid) {
				break;
			}
		}
	}

	private static WatchService createWatchService() throws Exception {
		Class<?> watchServiceClass = Class
				.forName("sun.nio.fs.PollingWatchService");
		Constructor<?> constructor = watchServiceClass.getDeclaredConstructor();
		constructor.setAccessible(true);
		return (WatchService) constructor.newInstance();
		// FileSystems.getDefault().newWatchService();
	}
}
