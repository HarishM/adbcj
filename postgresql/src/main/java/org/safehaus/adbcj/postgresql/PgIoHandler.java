package org.safehaus.adbcj.postgresql;

import java.util.Collections;

import org.apache.mina.common.IoHandlerAdapter;
import org.apache.mina.common.IoSession;
import org.safehaus.adbcj.DbException;
import org.safehaus.adbcj.postgresql.backend.AbstractBackendMessage;
import org.safehaus.adbcj.postgresql.backend.AuthenticationMessage;
import org.safehaus.adbcj.postgresql.backend.CommandCompleteMessage;
import org.safehaus.adbcj.postgresql.backend.DataRowMessage;
import org.safehaus.adbcj.postgresql.backend.ErrorResponseMessage;
import org.safehaus.adbcj.postgresql.backend.ReadyMessage;
import org.safehaus.adbcj.postgresql.backend.RowDescriptionMessage;
import org.safehaus.adbcj.postgresql.frontend.FrontendMessage;
import org.safehaus.adbcj.postgresql.frontend.FrontendMessageType;
import org.safehaus.adbcj.support.DefaultDbFuture;
import org.safehaus.adbcj.support.DefaultResult;
import org.safehaus.adbcj.support.DefaultRow;
import org.safehaus.adbcj.support.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PgIoHandler extends IoHandlerAdapter {
	
	private final Logger logger = LoggerFactory.getLogger(PgIoHandler.class);

	private final PgConnectionManager connectionManager;
	
	public PgIoHandler(PgConnectionManager connectionManager) {
		this.connectionManager = connectionManager;
	}

	@Override
	public void sessionCreated(IoSession session) throws Exception {
		logger.debug("sessionCreated");
	}
	
	@Override
	public void sessionOpened(IoSession session) throws Exception {
		logger.debug("sessionOpened");
	}
	
	@Override
	public void sessionClosed(IoSession session) throws Exception {
		PgConnection connection = IoSessionUtil.getConnection(session);
		DefaultDbFuture<Void> closeFuture = connection.getCloseFuture();
		if (closeFuture != null) {
			closeFuture.setDone();
		}
		logger.debug("IoSession closed");
	}
	
	@Override
	public void exceptionCaught(IoSession session, Throwable cause) throws Exception {
		logger.debug("Exception was thrown", cause);
		PgConnection connection = IoSessionUtil.getConnection(session);
		if (connection != null) {
			DefaultDbFuture<?> future = connection.getConnectFuture();
			if (future == null) {
				Request<?> request = connection.getActiveRequest();
				// TODO: Make sure that if we're in a transaction, the transaction is canceled
				if (request != null) {
					future = request.getFuture();
					errorOutFuture(future, cause);
					connection.makeNextRequestActive();
					return;
				}
			} else {
				errorOutFuture(future, cause);
				return;
			}
		}
		// Hand exception over to connection manager
		cause.printStackTrace();
	}
	
	private void errorOutFuture(DefaultDbFuture<?> future, Throwable cause) {
		if (!future.isDone()) {
			future.setException(DbException.wrap(cause));
			future.setDone();
		}
	}

	@Override
	public void messageSent(IoSession session, Object message) throws Exception {
		logger.trace("Message was sent");
	}

	@Override
	public void messageReceived(IoSession session, Object message) throws Exception {
		logger.trace("Received a message {}", message);
		AbstractBackendMessage backendMessage = (AbstractBackendMessage)message;
		// TODO Implement handling remaining backend message types
		switch(backendMessage.getType()) {
		case AUTHENTICATION:
			doAuthentication(session, (AuthenticationMessage)backendMessage);
			break;
		case BIND_COMPLETE:
			logger.trace("Bind complete");
			break;
		case COMMAND_COMPLETE:
			doCommandComplete(session, (CommandCompleteMessage)backendMessage);
			break;
		case DATA_ROW:
			doDataRow(session, (DataRowMessage)backendMessage);
			break;
		case ERROR_RESPONSE:
			doError(session, (ErrorResponseMessage)backendMessage);
		case KEY:
			// TODO Store pid and key in connection
			break;
		case NO_DATA:
			logger.trace("No data");
			break;
		case PARAMETER_STATUS:
			// TODO Store parameter values in connection
			break;
		case PARSE_COMPLETE:
			logger.trace("Parse complete");
			break;
		case READY_FOR_QUERY:
			doReadyForQuery(session, (ReadyMessage)backendMessage);
			break;
		case ROW_DESCRIPTION:
			doRowDescription(session, (RowDescriptionMessage)backendMessage);
			break;
		default:
			throw new IllegalStateException("Need to implement handler for messages of type " + backendMessage.getType());	
		}
	}
	
	private void doAuthentication(IoSession session, AuthenticationMessage authenticationMessage) {
		// TODO Support all postgresql authentication types
		switch (authenticationMessage.getAuthenticaitonType()) {
		case OK:
			// Authenticated successfully
			logger.debug("Authenticated successfully");
			break;
		case MD5_PASSWORD:
			byte[] md5hash = PasswordHash.hashPasswordMD5(
					connectionManager.getUsername(),
					connectionManager.getPassword(),
					authenticationMessage.getData());
			FrontendMessage md5Message = new  FrontendMessage(FrontendMessageType.PASSWORD, md5hash);
			session.write(md5Message);
			break;
		default:
			throw new IllegalStateException("We currently do not support the authentication type of " + authenticationMessage.getAuthenticaitonType());
		}
	}

	private void doCommandComplete(IoSession session, CommandCompleteMessage commandCompleteMessage) {
		PgConnection connection = IoSessionUtil.getConnection(session);
		
		Request<Object> request = connection.getActiveRequest();
		if (request == null) {
			throw new IllegalStateException("Received a data row without an active request");
		}

		switch (commandCompleteMessage.getCommand()) {
		case BEGIN:
		case COMMIT:
		case ROLLBACK:
			request.getFuture().setDone();
			break;
		case SELECT:
			PgResultSet resultSet = (PgResultSet)request.getPayload();
			if (resultSet == null) {
				throw new IllegalStateException("Received a SELECT command completion without an active result set");
			}
			request.getFuture().setValue(resultSet);
			request.getFuture().setDone();
			break;
		case DELETE:
		case INSERT:
		case UPDATE:
			DefaultResult result = new DefaultResult(commandCompleteMessage.getRowCount(), Collections.EMPTY_LIST);
			request.getFuture().setValue(result);
			request.getFuture().setDone();
			break;
		// TODO Implement other command complete message types (i.e. INSERT, DELETE, MOVE, UPDATE etc.)
		default:
			throw new IllegalStateException(String.format("Command completions of type %s are not implemented", commandCompleteMessage.getCommand()));
		}
	}

	private void doDataRow(IoSession session, DataRowMessage dataRowMessage) {
		PgConnection connection = IoSessionUtil.getConnection(session);
		
		Request<Object> request = connection.getActiveRequest();
		if (request == null) {
			throw new IllegalStateException("Received a data row without an active request");
		}
		PgResultSet resultSet = (PgResultSet)request.getPayload();
		if (resultSet == null) {
			throw new IllegalStateException("Received a data row without an active result set");
		}
		resultSet.addResult(new DefaultRow(resultSet, dataRowMessage.getValues()));
	}

	private void doError(IoSession session, ErrorResponseMessage backendMessage) {
		// When receiving an error packet, throw exception and let exceptionCaught notify future
		
		String message = backendMessage.getFields().get(ErrorField.MESSAGE);
		DbException exception;
		if (message == null) {
			exception = new PgException(backendMessage.getFields());
		} else {
			exception = new PgException(message, backendMessage.getFields());
		}
		throw exception;
	}

	private void doReadyForQuery(IoSession session, ReadyMessage backendMessage) {
		PgConnection connection = IoSessionUtil.getConnection(session);
		
		// Check if we're doing connection
		DefaultDbFuture<?> future = connection.getConnectFuture();
		if (future != null) {
			connection.clearConnectFuture();
			future.setDone();
			return;
		}
		
		// If not, handle request future
		
		Request<Object> request = connection.getActiveRequest();
		if (request == null) {
			throw new IllegalStateException("Received a READY with no current request");
		}
		future = request.getFuture();
		switch (backendMessage.getStatus()) {
		case TRANSACTION:
			// TODO Check to see if we think we're running in a transaction and if status is not 'T', throw an error
		case IDLE:
			future.setDone();
			connection.makeNextRequestActive();
			break;
		case ERROR:
			throw new DbException("Transaction is in error state");
		default:
			throw new IllegalStateException("Don't know hot to handle backend status of " + backendMessage.getStatus());
		}
	}

	private void doRowDescription(IoSession session, RowDescriptionMessage rowDescriptionMessage) {
		PgConnection connection = IoSessionUtil.getConnection(session);
		
		Request<Object> request = connection.getActiveRequest();
		if (request == null) {
			throw new IllegalStateException("Received a row description without an active request");
		}
		PgResultSet resultSet = new PgResultSet(rowDescriptionMessage.getFields().length);
		for (int i = 0; i < rowDescriptionMessage.getFields().length; i++) {
			PgField field = rowDescriptionMessage.getFields()[i];
			// TODO Figure out how to map Postgresql meta-data properly
			// TODO Add support for fetching extended Postgresql meta-data so we can have all the same info the MySQL has - do this on a ConnectionManager basis
			// TODO Add support for meta-data caching to ADBCJ API to facilitate invalidating cache on live systems
			resultSet.addField(field);
		}
		request.setPayload(resultSet);
	}

	public PgConnectionManager getConnectionManager() {
		return connectionManager;
	}
	
}