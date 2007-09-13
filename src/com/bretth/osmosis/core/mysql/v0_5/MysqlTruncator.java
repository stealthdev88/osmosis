package com.bretth.osmosis.core.mysql.v0_5;

import com.bretth.osmosis.core.mysql.common.DatabaseContext;
import com.bretth.osmosis.core.mysql.common.DatabaseLoginCredentials;
import com.bretth.osmosis.core.task.common.RunnableTask;


/**
 * A standalone OSM task with no inputs or outputs that truncates tables in a
 * mysql database. This is used for removing all existing data from tables.
 * 
 * @author Brett Henderson
 */
public class MysqlTruncator implements RunnableTask {
	
	// These SQL statements will be invoked to truncate each table.
	private static final String[] SQL_STATEMENTS = {
		"TRUNCATE current_relation_members",
		"TRUNCATE current_relation_tags",
		"TRUNCATE current_relations",
		"TRUNCATE current_way_nodes",
		"TRUNCATE current_way_tags",
		"TRUNCATE current_ways",
		"TRUNCATE current_nodes",
		"TRUNCATE relation_members",
		"TRUNCATE relation_tags",
		"TRUNCATE relations",
		"TRUNCATE way_nodes",
		"TRUNCATE way_tags",
		"TRUNCATE ways",
		"TRUNCATE nodes"
	};
	
	
	private DatabaseContext dbCtx;
	
	
	/**
	 * Creates a new instance.
	 * 
	 * @param loginCredentials
	 *            Contains all information required to connect to the database.
	 */
	public MysqlTruncator(DatabaseLoginCredentials loginCredentials) {
		dbCtx = new DatabaseContext(loginCredentials);
	}
	
	
	/**
	 * Truncates all data from the database.
	 */
	public void run() {
		try {
			for (int i = 0; i < SQL_STATEMENTS.length; i++) {
				dbCtx.executeStatement(SQL_STATEMENTS[i]);
			}
			
		} finally {
			dbCtx.release();
		}
	}
}
