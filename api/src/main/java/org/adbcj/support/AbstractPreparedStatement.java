package org.adbcj.support;

import java.util.List;

import org.adbcj.DbSession;
import org.adbcj.PreparedStatement;

public abstract class AbstractPreparedStatement implements PreparedStatement {

	private final DbSession session;	
	protected List<Object> params; 
	private final String nativeSQL;
	
	public AbstractPreparedStatement(DbSession session, String nativeSQL){
		this.session = session;
		this.nativeSQL = nativeSQL;
	}
	
	@Override
	public String getNativeSQL() {
		return nativeSQL;
	}

	@Override
	public List<Object> getParameterKeys() {
		return params;
	}
	
	public DbSession getSession(){
		return session;
	}
	
}
