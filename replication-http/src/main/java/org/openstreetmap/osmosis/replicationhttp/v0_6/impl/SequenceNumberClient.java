// This software is released into the Public Domain.  See copying.txt for details.
package org.openstreetmap.osmosis.replicationhttp.v0_6.impl;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.openstreetmap.osmosis.core.OsmosisRuntimeException;


/**
 * This class creates a HTTP client that connects to a sequence server, listens
 * for updated sequence numbers as they are received, and notifies any
 * configured listeners.
 * 
 * @author Brett Henderson
 */
public class SequenceNumberClient {

	private InetSocketAddress serverAddress;
	/**
	 * A flag used only by the external control thread to remember if the server
	 * has been started or not.
	 */
	private boolean masterRunning;
	/**
	 * The factory for all processing threads.
	 */
	private ChannelFactory factory;
	/**
	 * The channel used to receive sequence updates from the server.
	 */
	private Channel channel;
	/**
	 * The controller to be notified when events occur.
	 */
	private SequenceNumberClientControl control;


	/**
	 * Creates a new instance.
	 * 
	 * @param serverAddress
	 *            The address of the sequence server providing notification of
	 *            updated sequence numbers.
	 * @param control
	 *            The controller to be notified when events occur.
	 */
	public SequenceNumberClient(InetSocketAddress serverAddress, SequenceNumberClientControl control) {
		this.serverAddress = serverAddress;
		this.control = control;
	}


	/**
	 * Starts the client.
	 */
	public void start() {
		if (masterRunning) {
			throw new OsmosisRuntimeException("The server has already been started");
		}

		// Mark the client as running.
		masterRunning = true;

		// Create the processing thread pools.
		factory = new NioClientSocketChannelFactory(Executors.newCachedThreadPool(), Executors.newCachedThreadPool());

		ClientBootstrap bootstrap = new ClientBootstrap(factory);
		bootstrap.setPipelineFactory(new SequenceNumberClientChannelPipelineFactory(control));
		bootstrap.setOption("tcpNoDelay", true);
		bootstrap.setOption("keepAlive", true);
		ChannelFuture future = bootstrap.connect(serverAddress);

		// Get a reference to the channel.
		channel = future.getChannel();

		// Wait for the connection attempt to complete.
		future.awaitUninterruptibly();

		// Abort if the startup failed.
		if (!future.isSuccess()) {
			throw new OsmosisRuntimeException("Unable to launch sequence client.");
		}
	}


	/**
	 * Stops the client. This must be called in all cases even if start failed.
	 */
	public void stop() {
		if (masterRunning) {
			channel.close().awaitUninterruptibly();

			factory.releaseExternalResources();
			masterRunning = false;
		}
	}
}
