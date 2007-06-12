package com.bretth.osm.conduit.misc;

import java.util.Map;

import com.bretth.osm.conduit.pipeline.SinkManager;
import com.bretth.osm.conduit.pipeline.TaskManager;
import com.bretth.osm.conduit.pipeline.TaskManagerFactory;


/**
 * The task manager factory for a null writer.
 * 
 * @author Brett Henderson
 */
public class NullWriterFactory extends TaskManagerFactory {
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	protected TaskManager createTaskManagerImpl(String taskId, Map<String, String> taskArgs, Map<String, String> pipeArgs) {
		return new SinkManager(taskId, new NullWriter(), pipeArgs);
	}
}
