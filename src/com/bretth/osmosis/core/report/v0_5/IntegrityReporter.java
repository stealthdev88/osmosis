package com.bretth.osmosis.core.report.v0_5;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.bretth.osmosis.core.OsmosisRuntimeException;
import com.bretth.osmosis.core.container.v0_5.EntityContainer;
import com.bretth.osmosis.core.container.v0_5.EntityProcessor;
import com.bretth.osmosis.core.container.v0_5.NodeContainer;
import com.bretth.osmosis.core.container.v0_5.RelationContainer;
import com.bretth.osmosis.core.container.v0_5.WayContainer;
import com.bretth.osmosis.core.domain.v0_5.EntityType;
import com.bretth.osmosis.core.domain.v0_5.Relation;
import com.bretth.osmosis.core.domain.v0_5.RelationMember;
import com.bretth.osmosis.core.domain.v0_5.Way;
import com.bretth.osmosis.core.domain.v0_5.WayNode;
import com.bretth.osmosis.core.filter.common.BigBitSet;
import com.bretth.osmosis.core.task.v0_5.Sink;


/**
 * A sink that verifies the referential integrity of all data passing through
 * it.
 * 
 * @author Brett Henderson
 */
public class IntegrityReporter implements Sink, EntityProcessor {
	
	private static final Logger log = Logger.getLogger(IntegrityReporter.class.getName());
	
	private File file;
	private boolean initialized;
	private BufferedWriter writer;
	private BigBitSet nodeBitSet;
	private BigBitSet wayBitSet;
	
	
	/**
	 * Creates a new instance.
	 * 
	 * @param file
	 *            The file to write.
	 */
	public IntegrityReporter(File file) {
		this.file = file;
		
		initialized = false;
		nodeBitSet = new BigBitSet();
		wayBitSet = new BigBitSet();
	}
	
	
	/**
	 * Writes data to the output file.
	 * 
	 * @param data
	 *            The data to be written.
	 */
	private void write(String data) {
		try {
			writer.write(data);
			
		} catch (IOException e) {
			throw new OsmosisRuntimeException("Unable to write data.", e);
		}
	}
	
	
	/**
	 * Writes a new line in the output file.
	 */
	private void writeNewLine() {
		try {
			writer.newLine();
			
		} catch (IOException e) {
			throw new OsmosisRuntimeException("Unable to write data.", e);
		}
	}
	
	
	/**
	 * Initialises the output file for writing. This must be called by
	 * sub-classes before any writing is performed. This method may be called
	 * multiple times without adverse affect allowing sub-classes to invoke it
	 * every time they perform processing.
	 */
	protected void initialize() {
		if (!initialized) {
			OutputStream outStream = null;
			
			try {
				outStream = new FileOutputStream(file);
				
				writer = new BufferedWriter(new OutputStreamWriter(outStream, "UTF-8"));
				
				outStream = null;
				
			} catch (IOException e) {
				throw new OsmosisRuntimeException("Unable to open file " + file + " for writing.", e);
			} finally {
				if (outStream != null) {
					try {
						outStream.close();
					} catch (Exception e) {
						log.log(Level.SEVERE, "Unable to close output stream for file " + file + ".", e);
					}
					outStream = null;
				}
			}
			
			initialized = true;
			
			write("Entity Type, Entity Id, Referred Type, Referred Id");
			writeNewLine();
		}
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public void process(EntityContainer entityContainer) {
		entityContainer.process(this);
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public void process(NodeContainer node) {
		nodeBitSet.set(node.getEntity().getId());
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public void process(WayContainer wayContainer) {
		Way way;
		
		way = wayContainer.getEntity();
		
		wayBitSet.set(way.getId());
		
		for (WayNode wayNode : way.getWayNodeList()) {
			if (!nodeBitSet.get(wayNode.getNodeId())) {
				initialize();
				
				write("Way," + way.getId() + ",Node," + wayNode.getNodeId());
				writeNewLine();
			}
		}
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public void process(RelationContainer relationContainer) {
		Relation relation;
		
		relation = relationContainer.getEntity();
		
		for (RelationMember relationMember : relation.getMemberList()) {
			EntityType memberType;
			
			memberType = relationMember.getMemberType();
			
			if (EntityType.Node.equals(memberType)) {
				if (!nodeBitSet.get(relationMember.getMemberId())) {
					initialize();
					
					write("Relation," + relation.getId() + ",Node," + relationMember.getMemberId());
					writeNewLine();
				}
			} else if (EntityType.Way.equals(memberType)) {
				if (!wayBitSet.get(relationMember.getMemberId())) {
					initialize();
					
					write("Relation," + relation.getId() + ",Way," + relationMember.getMemberId());
					writeNewLine();
				}
			}
		}
	}
	
	
	/**
	 * Flushes all changes to file.
	 */
	public void complete() {
		try {
			if (writer != null) {
				writer.close();
			}
			
		} catch (IOException e) {
			throw new OsmosisRuntimeException("Unable to complete writing to the file " + file + ".", e);
		} finally {
			initialized = false;
			writer = null;
		}
	}
	
	
	/**
	 * Cleans up any open file handles.
	 */
	public void release() {
		try {
			try {
				if (writer != null) {
					writer.close();
				}
			} catch(Exception e) {
				log.log(Level.SEVERE, "Unable to close writer.", e);
			}
		} finally {
			initialized = false;
			writer = null;
		}
	}
}
