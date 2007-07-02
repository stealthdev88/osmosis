package com.bretth.osmosis.mysql.impl;

import java.util.Date;

import com.bretth.osmosis.OsmosisRuntimeException;
import com.bretth.osmosis.container.ChangeContainer;
import com.bretth.osmosis.container.NodeContainer;
import com.bretth.osmosis.task.ChangeAction;


/**
 * Reads the set of node changes from a database that have occurred within a
 * time interval.
 * 
 * @author Brett Henderson
 */
public class NodeChangeReader {
	
	private ModifiedNodeIdReader nodeIdReader;
	private NodeHistoryReader nodeHistoryReader;
	private ChangeContainer nextValue;
	
	
	/**
	 * Creates a new instance.
	 * 
	 * @param host
	 *            The server hosting the database.
	 * @param database
	 *            The database instance.
	 * @param user
	 *            The user name for authentication.
	 * @param password
	 *            The password for authentication.
	 * @param intervalBegin
	 *            Marks the beginning (inclusive) of the time interval to be
	 *            checked.
	 * @param intervalEnd
	 *            Marks the end (exclusive) of the time interval to be checked.
	 */
	public NodeChangeReader(String host, String database, String user, String password, Date intervalBegin, Date intervalEnd) {
		nodeIdReader = new ModifiedNodeIdReader(host, database, user, password, intervalBegin, intervalEnd);
		
		nodeHistoryReader = new NodeHistoryReader(host, database, user, password);
		nodeHistoryReader.setIntervalEnd(intervalEnd);
	}
	
	
	/**
	 * Reads the history of the specified entity and builds a change object.
	 * 
	 * @param nodeId
	 *            The node to examine.
	 * @return The change.
	 */
	private ChangeContainer readChange(long nodeId) {
		int recordCount = 0;
		NodeHistory mostRecentHistory = null;
		NodeContainer nodeContainer;
		
		nodeHistoryReader.reset();
		nodeHistoryReader.setNodeId(nodeId);
		
		// Read the entire node history, we need to know how many records there
		// are and the details of the most recent change.
		while (nodeHistoryReader.hasNext()) {
			NodeHistory nextHistory = nodeHistoryReader.next();
			
			recordCount++;
			mostRecentHistory = nextHistory;
		}
		
		// We must have at least one record, we shouldn't have identified the
		// node if no history elements exist.
		if (recordCount <= 0) {
			throw new OsmosisRuntimeException("No history elements exist for node with id=" + nodeId + ".");
		}
		
		// The node in the result must be wrapped in a container.
		nodeContainer = new NodeContainer(mostRecentHistory.getNode());
		
		// If only one history element exists, it must be a create.
		// Else, if the most recent change leaves it visible it is a modify.
		// Else, it is a delete.
		if (recordCount == 1) {
			// By definition, a create must be visible but we'll double check to be sure.
			if (!mostRecentHistory.isVisible()) {
				throw new OsmosisRuntimeException("Node with id=" + nodeId + " only has one history element but it is not visible.");
			}
			
			return new ChangeContainer(nodeContainer, ChangeAction.Create);
			
		} else if (mostRecentHistory.isVisible()) {
			return new ChangeContainer(nodeContainer, ChangeAction.Modify);
			
		} else {
			return new ChangeContainer(nodeContainer, ChangeAction.Delete);
		}
	}
	
	
	/**
	 * Indicates if there is any more data available to be read.
	 * 
	 * @return True if more data is available, false otherwise.
	 */
	public boolean hasNext() {
		if (nextValue == null) {
			if (nodeIdReader.hasNext()) {
				long nodeId;
				
				nodeId = nodeIdReader.next().longValue();
				
				nextValue = readChange(nodeId);
			}
		}
		
		return (nextValue != null);
	}
	
	
	/**
	 * Returns the next available entity and advances to the next record.
	 * 
	 * @return The next available entity.
	 */
	public ChangeContainer next() {
		if (!hasNext()) {
			throw new OsmosisRuntimeException("No records are available, call hasNext first.");
		}
		
		return nextValue;
	}
	
	
	/**
	 * Releases all database resources. This method is guaranteed not to throw
	 * transactions and should always be called in a finally block whenever this
	 * class is used.
	 */
	public void release() {
		nextValue = null;
		
		nodeIdReader.release();
		nodeHistoryReader.release();
	}
}
