package example;

import java.util.EventListener;

public interface FileMonitorListener extends EventListener {

	public void onChanged(FileMonitorEvent event);

}
