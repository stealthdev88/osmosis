// This software is released into the Public Domain.  See copying.txt for details.
package org.openstreetmap.osmosis.replicationhttp.v0_6.impl;

import java.nio.channels.ClosedChannelException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.util.CharsetUtil;


/**
 * Netty handler for sending replication sequence numbers to clients.
 * 
 * @author Brett Henderson
 */
public abstract class SequenceServerHandler extends SimpleChannelHandler {

	private static final Logger LOG = Logger.getLogger(SequenceServerHandler.class.getName());

	private SequenceServerControl control;


	/**
	 * Creates a new instance.
	 * 
	 * @param control
	 *            Provides the Netty handlers with access to the controller.
	 */
	public SequenceServerHandler(SequenceServerControl control) {
		this.control = control;
	}


	/**
	 * Gets the central control object.
	 * 
	 * @return The controller.
	 */
	protected SequenceServerControl getControl() {
		return control;
	}


	@Override
	public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e) {
		control.registerChannel(e.getChannel());
	}


	/**
	 * Writes a HTTP 404 response to the client.
	 * 
	 * @param ctx
	 *            The Netty context.
	 * @param future
	 *            The future for current processing.
	 * @param requestedUri
	 *            The URI requested by the client.
	 */
	private void writeResourceNotFound(final ChannelHandlerContext ctx, ChannelFuture future, String requestedUri) {
		// Write the HTTP header to the client.
		DefaultHttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_0, HttpResponseStatus.NOT_FOUND);
		response.addHeader("Content-Type", "text/plain");

		// Send the 404 message to the client.
		ChannelBuffer buffer = ChannelBuffers.copiedBuffer("The requested resource does not exist: " + requestedUri,
				CharsetUtil.UTF_8);
		response.setContent(buffer);
		Channels.write(ctx, future, response);

		// Wait for the previous operation to finish and then close the channel.
		future.addListener(new ChannelFutureListener() {
			@Override
			public void operationComplete(ChannelFuture future) {
				ctx.getChannel().close();
			}
		});
	}


	/**
	 * Writes a HTTP 410 response to the client.
	 * 
	 * @param ctx
	 *            The Netty context.
	 * @param future
	 *            The future for current processing.
	 * @param requestedUri
	 *            The URI requested by the client.
	 */
	private void writeResourceGone(final ChannelHandlerContext ctx, ChannelFuture future, String requestedUri) {
		// Write the HTTP header to the client.
		DefaultHttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_0, HttpResponseStatus.GONE);
		response.addHeader("Content-Type", "text/plain");

		// Send the 410 message to the client.
		ChannelBuffer buffer = ChannelBuffers.copiedBuffer("The requested resource is no longer available: "
				+ requestedUri, CharsetUtil.UTF_8);
		response.setContent(buffer);
		Channels.write(ctx, future, response);

		// Wait for the previous operation to finish and then close the channel.
		future.addListener(new ChannelFutureListener() {
			@Override
			public void operationComplete(ChannelFuture future) {
				ctx.getChannel().close();
			}
		});
	}


	/**
	 * Writes a HTTP 400 response to the client.
	 * 
	 * @param ctx
	 *            The Netty context.
	 * @param future
	 *            The future for current processing.
	 * @param requestedUri
	 *            The URI requested by the client.
	 * @param errorMessage
	 *            Further information about why the request is bad.
	 */
	private void writeBadRequest(final ChannelHandlerContext ctx, ChannelFuture future, String requestedUri,
			String errorMessage) {
		final String newLine = "\r\n";

		// Write the HTTP header to the client.
		DefaultHttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_0, HttpResponseStatus.NOT_FOUND);
		response.addHeader("Content-Type", "text/plain");

		// Send the 400 message to the client.
		StringBuilder messageBuilder = new StringBuilder();
		messageBuilder.append("Bad Request").append(newLine);
		messageBuilder.append("Message: ").append(errorMessage).append(newLine);
		messageBuilder.append("Requested URI: ").append(requestedUri).append(newLine);
		ChannelBuffer buffer = ChannelBuffers.copiedBuffer(messageBuilder.toString(), CharsetUtil.UTF_8);
		response.setContent(buffer);
		Channels.write(ctx, future, response);

		// Wait for the previous operation to finish and then close the channel.
		future.addListener(new ChannelFutureListener() {
			@Override
			public void operationComplete(ChannelFuture future) {
				ctx.getChannel().close();
			}
		});
	}


	/**
	 * Writes sequence data to the client. If follow is set, it allows
	 * continuous updates to be streamed to the client.
	 * 
	 * @param ctx
	 *            The Netty context.
	 * @param future
	 *            The future for current processing.
	 * @param contentType
	 *            The content type to set on the HTTP response.
	 * @param lastSequenceNumber
	 *            The last known sequence number. Sending will start from after
	 *            this number.
	 * @param follow
	 *            If true, continuous updates will be sent to the client.
	 */
	protected void initiateSequenceWriting(final ChannelHandlerContext ctx, final ChannelFuture future,
			String contentType, final long lastSequenceNumber, final boolean follow) {
		// Write the HTTP header to the client.
		DefaultHttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
		response.addHeader("Content-Type", contentType);
		response.setChunked(true);
		response.addHeader(HttpHeaders.Names.TRANSFER_ENCODING, HttpHeaders.Values.CHUNKED);
		Channels.write(ctx, future, response);

		// Wait for the previous operation to finish and then start sending
		// sequence numbers to this channel.
		future.addListener(new ChannelFutureListener() {
			@Override
			public void operationComplete(ChannelFuture future) throws Exception {
				if (future.isSuccess()) {
					control.determineNextChannelAction(ctx.getChannel(), lastSequenceNumber, follow);
				}
			}
		});
	}


	/**
	 * Parses the request and initialises the response processing, typically by
	 * calling the writeSequence method.
	 * 
	 * @param ctx
	 *            The Netty context.
	 * @param future
	 *            The future for current processing.
	 * @param request
	 *            The client request.
	 */
	protected abstract void handleRequest(ChannelHandlerContext ctx, ChannelFuture future, HttpRequest request);


	@Override
	public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
		// We have received a message from the client which is a HTTP request.
		HttpRequest request = (HttpRequest) e.getMessage();

		// Invoke the implementation specific handler for request parsing.
		try {
			handleRequest(ctx, e.getFuture(), request);
			
		} catch (ResourceNotFoundException ex) {
			writeResourceNotFound(ctx, e.getFuture(), request.getUri());
		} catch (ResourceGoneException ex) {
			writeResourceGone(ctx, e.getFuture(), request.getUri());
		} catch (BadRequestException ex) {
			writeBadRequest(ctx, e.getFuture(), request.getUri(), ex.getMessage());
		}
	}


	@Override
	public abstract void writeRequested(ChannelHandlerContext ctx, MessageEvent e) throws Exception;


	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
		// Get the cause of the exception.
		Throwable t = e.getCause();

		// A ClosedChannelException occurs if the client disconnects and is not
		// an error scenario.
		if (!(t instanceof ClosedChannelException)) {
			LOG.log(Level.SEVERE, "Error during processing.", t);
		}

		// We must stop sending to this client if any errors occur during
		// processing.
		e.getChannel().close();
	}

	/**
	 * Used during request parsing to notify that the requested URI could not be
	 * found.
	 */
	protected static class ResourceNotFoundException extends RuntimeException {
		private static final long serialVersionUID = -1L;
	}

	/**
	 * Used during request parsing to notify that the request is invalid in some
	 * way.
	 */
	protected static class BadRequestException extends RuntimeException {
		private static final long serialVersionUID = -1L;


		/**
		 * Creates a new instance.
		 * 
		 * @param message
		 *            The error message.
		 */
		public BadRequestException(String message) {
			super(message);
		}
	}

	/**
	 * Used during request parsing to notify that the requested URI is no longer
	 * available.
	 */
	protected static class ResourceGoneException extends RuntimeException {
		private static final long serialVersionUID = -1L;
	}
}
