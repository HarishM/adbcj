package org.adbcj.postgresql.mina;

import org.adbcj.postgresql.codec.AbstractPgConnection;
import org.adbcj.postgresql.codec.AbstractPgConnectionManager;
import org.adbcj.postgresql.codec.frontend.AbstractFrontendMessage;
import org.apache.mina.core.session.IoSession;

/**
 * @author Mike Heath
 */
public class MinaConnection extends AbstractPgConnection {

	private final MinaConnectionManager.PgConnectFuture connectFuture;
	private final IoSession session;

	public MinaConnection(AbstractPgConnectionManager connectionManager, MinaConnectionManager.PgConnectFuture connectFuture, IoSession session) {
		super(connectionManager);
		this.connectFuture = connectFuture;
		this.session = session;
	}

	public MinaConnectionManager.PgConnectFuture getConnectFuture() {
		return connectFuture;
	}

	@Override
	protected boolean isConnectionClosing() {
		return session.isClosing();
	}

	@Override
	protected void write(AbstractFrontendMessage message) {
		session.write(message);
	}

	@Override
	protected void write(AbstractFrontendMessage[] messages) {
		session.write(messages);
	}

}
