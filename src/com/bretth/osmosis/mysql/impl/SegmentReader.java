package com.bretth.osmosis.mysql.impl;

import java.sql.ResultSet;
import java.sql.SQLException;

import com.bretth.osmosis.OsmosisRuntimeException;
import com.bretth.osmosis.data.Segment;


/**
 * Reads all segments from a database ordered by their identifier.
 * 
 * @author Brett Henderson
 */
public class SegmentReader extends EntityReader<Segment> {
	private static final String SELECT_SQL =
		"SELECT id, node_a, node_b, tags FROM current_segments ORDER BY id";
	
	private EmbeddedTagParser tagParser;
	
	
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
	 */
	public SegmentReader(String host, String database, String user, String password) {
		super(host, database, user, password);
		
		tagParser = new EmbeddedTagParser();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	protected ResultSet createResultSet(DatabaseContext queryDbCtx) {
		return queryDbCtx.executeStreamingQuery(SELECT_SQL);
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	protected Segment createNextValue(ResultSet resultSet) {
		long id;
		long from;
		long to;
		String tags;
		Segment segment;
		
		try {
			id = resultSet.getLong("id");
			from = resultSet.getLong("node_a");
			to = resultSet.getLong("node_b");
			tags = resultSet.getString("tags");
			
		} catch (SQLException e) {
			throw new OsmosisRuntimeException("Unable to read segment fields.", e);
		}
		
		segment = new Segment(id, from, to);
		segment.addTags(tagParser.parseTags(tags));
		
		return segment;
	}
}
