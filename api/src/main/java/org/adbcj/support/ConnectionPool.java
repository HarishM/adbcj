package org.adbcj.support;

import org.adbcj.DbFuture;
import org.adbcj.DbSession;
import org.adbcj.DbSessionProvider;

public interface ConnectionPool extends DbSessionProvider{
	
	DbFuture<DbSession> connect();
}
