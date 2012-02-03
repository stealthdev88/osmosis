// This software is released into the Public Domain.  See copying.txt for details.
package org.openstreetmap.osmosis.replicationhttp.v0_6.impl;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.openstreetmap.osmosis.core.OsmosisRuntimeException;
import org.openstreetmap.osmosis.core.container.v0_6.ChangeContainer;
import org.openstreetmap.osmosis.core.task.v0_6.ChangeSink;
import org.openstreetmap.osmosis.core.task.v0_6.RunnableChangeSource;
import org.openstreetmap.osmosis.core.util.PropertiesPersister;
import org.openstreetmap.osmosis.replication.common.ReplicationState;
import org.openstreetmap.osmosis.xml.common.CompressionMethod;
import org.openstreetmap.osmosis.xml.v0_6.XmlChangeReader;


/**
 * Netty handler for receiving replication data and notifying listeners.
 * 
 * @author Brett Henderson
 */
public class ReplicationDataClientHandler extends SequenceClientHandler {

	private static final Logger LOG = Logger.getLogger(ReplicationDataClientHandler.class.getName());

	private ChangeSink changeSink;
	private String pathPrefix;
	private NoLifecycleChangeSinkWrapper noLifecycleChangeSink;
	private boolean sinkInitInvoked;
	private boolean replicationStateReceived;
	private ReplicationState replicationState;
	private ChunkedDataReceiver chunkReceiver;


	/**
	 * Creates a new instance.
	 * 
	 * @param control
	 *            Provides the Netty handlers with access to the controller.
	 * @param changeSink
	 *            The destination for the replication data.
	 * @param serverHost
	 *            The name of the host system running the sequence server.
	 * @param pathPrefix
	 *            The base path to add to the URL. This is necessary if a data
	 *            server is sitting behind a proxy server that adds a prefix to
	 *            the request path.
	 */
	public ReplicationDataClientHandler(SequenceClientControl control, ChangeSink changeSink, String serverHost,
			String pathPrefix) {
		super(control, serverHost);

		this.changeSink = changeSink;
		this.pathPrefix = pathPrefix;

		noLifecycleChangeSink = new NoLifecycleChangeSinkWrapper(changeSink);

		sinkInitInvoked = false;
		replicationStateReceived = false;
		replicationState = null;
		chunkReceiver = new ChunkedDataReceiver();
	}


	private void sendReplicationData(File chunkFile) {
		// Release all class level resources and prepare for passing the
		// replication data downstream.
		replicationState = null;
		replicationStateReceived = false;
		sinkInitInvoked = false;
		
		// Send the replication data downstream but don't call any lifecycle
		// methods on the change sink because we're managing those separately.
		if (chunkFile != null) {
			RunnableChangeSource changeReader = new XmlChangeReader(chunkFile, true, CompressionMethod.GZip);
			changeReader.setChangeSink(noLifecycleChangeSink);
			changeReader.run();
		}
		
		changeSink.complete();
	}


	private void invokeSinkInit() {
		replicationState = new ReplicationState();
		Map<String, Object> metaData = new HashMap<String, Object>(1);
		metaData.put(ReplicationState.META_DATA_KEY, replicationState);
		changeSink.initialize(metaData);
		sinkInitInvoked = true;
	}


	@Override
	protected String getRequestUri() {
		// We need to know the last replication number that we have received on
		// a previous run. To do this we need to retrieve the replication state
		// from our downstream replication task by initializing.
		invokeSinkInit();

		// The downstream task returns the next sequence number.
		long requestSequenceNumber = replicationState.getSequenceNumber();

		return pathPrefix + "/replicationData/" + requestSequenceNumber + "/tail";
	}


	private ReplicationState loadState(File stateFile) {
		PropertiesPersister persister = new PropertiesPersister(stateFile);
		ReplicationState state = new ReplicationState();
		state.load(persister.loadMap());

		return state;
	}


	@Override
	protected void processMessageData(ChannelBuffer buffer) {
		// Break the data down according to chunk alignment.
		List<File> chunkFiles = chunkReceiver.processData(buffer);
		
		try {
			for (File chunkFile : chunkFiles) {

				if (!replicationStateReceived) {
					// We usually have to invoke the sink init, but if this is
					// during startup we may have already performed this step
					// while preparing our initial request.
					if (!sinkInitInvoked) {
						invokeSinkInit();
					}

					// The first chunk contains the replication state stored in
					// properties format.
					ReplicationState serverReplicationState = loadState(chunkFile);
					if (LOG.isLoggable(Level.FINER)) {
						LOG.finer("Received replication state " + serverReplicationState.getSequenceNumber());
					}

					// Validate that the server has sent us the expected state.
					if (serverReplicationState.getSequenceNumber() != replicationState.getSequenceNumber()) {
						throw new OsmosisRuntimeException("Received sequence number "
								+ serverReplicationState.getSequenceNumber() + " from server, expected "
								+ replicationState.getSequenceNumber());
					}

					// Update the local state with server values.
					replicationState.setTimestamp(serverReplicationState.getTimestamp());
					replicationStateReceived = true;
					
					// If this is replication 0, then we need to finish
					// processing now because the first sequence doesn't have
					// any data.
					if (replicationState.getSequenceNumber() == 0) {
						sendReplicationData(null);
					}
				} else {
					sendReplicationData(chunkFile);
				}
			}
			
		} finally {
			// Delete all chunk files.
			for (File chunkFile : chunkFiles) {
				if (!chunkFile.delete()) {
					LOG.log(Level.WARNING, "Unable to delete the current temporary chunk file " + chunkFile);
				}
			}
		}
	}


	@Override
	public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
		// Release any half populated chunk files.
		chunkReceiver.release();

		super.channelClosed(ctx, e);
	}

	/**
	 * This acts as a proxy between the xml change reader and the real change
	 * sink. The primary purpose is to only propagate calls to process because
	 * the lifecycle methods initialize, complete and release are managed
	 * separately.
	 */
	private static class NoLifecycleChangeSinkWrapper implements ChangeSink {
		private ChangeSink changeSink;


		/**
		 * Creates a new instance.
		 * 
		 * @param changeSink
		 *            The wrapped change sink.
		 */
		public NoLifecycleChangeSinkWrapper(ChangeSink changeSink) {
			this.changeSink = changeSink;
		}


		@Override
		public void initialize(Map<String, Object> metaData) {
			// Do nothing.
		}


		@Override
		public void process(ChangeContainer change) {
			changeSink.process(change);
		}


		@Override
		public void complete() {
			// Do nothing.
		}


		@Override
		public void release() {
			// Do nothing.
		}
	}
}
