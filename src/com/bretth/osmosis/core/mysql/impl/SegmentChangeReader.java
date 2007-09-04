package com.bretth.osmosis.core.mysql.impl;

import java.util.Date;

import com.bretth.osmosis.core.container.v0_4.ChangeContainer;
import com.bretth.osmosis.core.container.v0_4.SegmentContainer;
import com.bretth.osmosis.core.OsmosisRuntimeException;
import com.bretth.osmosis.core.domain.v0_4.Segment;
import com.bretth.osmosis.core.store.PeekableIterator;
import com.bretth.osmosis.core.task.common.ChangeAction;


/**
 * Reads the set of segment changes from a database that have occurred within a
 * time interval.
 * 
 * @author Brett Henderson
 */
public class SegmentChangeReader {
	
	private PeekableIterator<EntityHistory<Segment>> segmentHistoryReader;
	private ChangeContainer nextValue;
	private Date intervalBegin;
	
	
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
	 * @param readAllUsers
	 *            If this flag is true, all users will be read from the database
	 *            regardless of their public edits flag.
	 * @param intervalBegin
	 *            Marks the beginning (inclusive) of the time interval to be
	 *            checked.
	 * @param intervalEnd
	 *            Marks the end (exclusive) of the time interval to be checked.
	 */
	public SegmentChangeReader(String host, String database, String user, String password, boolean readAllUsers, Date intervalBegin, Date intervalEnd) {
		this.intervalBegin = intervalBegin;
		
		segmentHistoryReader = new PeekableIterator<EntityHistory<Segment>>(
			new SegmentHistoryReader(host, database, user, password, readAllUsers, intervalBegin, intervalEnd)
		);
	}
	
	
	/**
	 * Reads the history of the next entity and builds a change object.
	 */
	private ChangeContainer readChange() {
		boolean createdPreviously;
		EntityHistory<Segment> mostRecentHistory;
		SegmentContainer segmentContainer;
		
		// Read the entire segment history, if any segments exist prior to the
		// interval beginning, the segment already existed and therefore
		// cannot be a create.
		createdPreviously = false;
		do {
			mostRecentHistory = segmentHistoryReader.next();
			if (mostRecentHistory.getEntity().getTimestamp().compareTo(intervalBegin) < 0) {
				createdPreviously = true;
			}
		} while (segmentHistoryReader.hasNext() &&
				(segmentHistoryReader.peekNext().getEntity().getId() == mostRecentHistory.getEntity().getId()));
		
		// The segment in the result must be wrapped in a container.
		segmentContainer = new SegmentContainer(mostRecentHistory.getEntity());
		
		// The entity has been modified if it is visible and was created previously.
		// It is a create if it is visible and was NOT created previously.
		// It is a delete if it is NOT visible and was created previously.
		// No action if it is NOT visible and was NOT created previously.
		if (mostRecentHistory.isVisible() && createdPreviously) {
			return new ChangeContainer(segmentContainer, ChangeAction.Modify);
		} else if (mostRecentHistory.isVisible() && !createdPreviously) {
			return new ChangeContainer(segmentContainer, ChangeAction.Create);
		} else if (!mostRecentHistory.isVisible() && createdPreviously) {
			return new ChangeContainer(segmentContainer, ChangeAction.Delete);
		} else {
			return null;
		}
	}
	
	
	/**
	 * Indicates if there is any more data available to be read.
	 * 
	 * @return True if more data is available, false otherwise.
	 */
	public boolean hasNext() {
		while (nextValue == null && segmentHistoryReader.hasNext()) {
			nextValue = readChange();
		}
		
		return (nextValue != null);
	}
	
	
	/**
	 * Returns the next available entity and advances to the next record.
	 * 
	 * @return The next available entity.
	 */
	public ChangeContainer next() {
		ChangeContainer result;
		
		if (!hasNext()) {
			throw new OsmosisRuntimeException("No records are available, call hasNext first.");
		}
		
		result = nextValue;
		nextValue = null;
		
		return result;
	}
	
	
	/**
	 * Releases all database resources. This method is guaranteed not to throw
	 * transactions and should always be called in a finally block whenever this
	 * class is used.
	 */
	public void release() {
		nextValue = null;
		
		segmentHistoryReader.release();
	}
}
