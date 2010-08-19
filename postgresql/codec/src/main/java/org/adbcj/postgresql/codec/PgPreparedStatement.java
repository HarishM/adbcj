package org.adbcj.postgresql.codec;

import java.util.Map;

import org.adbcj.DbFuture;
import org.adbcj.DbSession;
import org.adbcj.Field;
import org.adbcj.Result;
import org.adbcj.ResultEventHandler;
import org.adbcj.ResultSet;
import org.adbcj.Value;
import org.adbcj.postgresql.codec.frontend.AbstractFrontendMessage;
import org.adbcj.postgresql.codec.frontend.BindMessage;
import org.adbcj.postgresql.codec.frontend.CloseMessage;
import org.adbcj.postgresql.codec.frontend.DescribeMessage;
import org.adbcj.postgresql.codec.frontend.ExecuteMessage;
import org.adbcj.postgresql.codec.frontend.SimpleFrontendMessage;
import org.adbcj.support.AbstractPreparedStatement;
import org.adbcj.support.DefaultResultSet;
import org.adbcj.support.DefaultRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PgPreparedStatement extends AbstractPreparedStatement{

	private final Logger logger = LoggerFactory.getLogger(PgPreparedStatement.class);
	private int paramCount;
	private int[] paramOID;
	private static int prepareCount = 0;
	private String stmtName = "Prepare";
	private int Id;
	
	// Constant Messages
    private static final ExecuteMessage DEFAULT_EXECUTE = new ExecuteMessage();
	private static final DescribeMessage DEFAULT_DESCRIBE = DescribeMessage.createDescribePortalMessage(null);
	
	public PgPreparedStatement(DbSession session, String nativeSQL) {
		super(session, nativeSQL);
		this.Id = ++prepareCount;
		this.stmtName += prepareCount; 
	}

	@SuppressWarnings("unchecked")
	@Override
	public DbFuture<DefaultResultSet> executeQuery(Object... params) {
		
		ResultEventHandler<DefaultResultSet> eventHandler = new ResultEventHandler<DefaultResultSet>() {

			private Value[] currentRow;

			public void startFields(DefaultResultSet accumulator) {
				logger.trace("ResultSetEventHandler: startFields");
			}
			public void field(Field field, DefaultResultSet accumulator) {
				logger.trace("ResultSetEventHandler: field");
				accumulator.addField(field);
			}
			public void endFields(DefaultResultSet accumulator) {
				logger.trace("ResultSetEventHandler: endFields");
			}
			public void startResults(DefaultResultSet accumulator) {
				logger.trace("ResultSetEventHandler: startResults");
			}
			public void startRow(DefaultResultSet accumulator) {
				logger.trace("ResultSetEventHandler: startRow");

				int columnCount = accumulator.getFields().size();
				currentRow = new Value[columnCount];
			}
			public void value(Value value, DefaultResultSet accumulator) {
				logger.trace("ResultSetEventHandler: value");
				currentRow[value.getField().getIndex()] = value;
			}
			public void endRow(DefaultResultSet accumulator) {
				logger.trace("ResultSetEventHandler: endRow");
				DefaultRow row = new DefaultRow(accumulator, currentRow);
				accumulator.addResult(row);
				currentRow = null;
			}
			public void endResults(DefaultResultSet accumulator) {
				logger.trace("ResultSetEventHandler: endResults");
			}
			public void exception(Throwable t, DefaultResultSet accumulator) {
			}
		};
		final AbstractPgConnection connection = (AbstractPgConnection)this.getSession();
		DefaultResultSet resultSet = new DefaultResultSet(connection);
		
		FormatCode[] fCode = new FormatCode[params.length];		
		String parameterValue[] = new String[params.length];
		
		for(int i=0 ; i<params.length ; i++){
			fCode[i] = FormatCode.TEXT;
			parameterValue[i] = params[i].toString();
		}
		
		final BindMessage BIND = new BindMessage(this.stmtName, null, fCode, parameterValue, null);
		return connection.enqueueTransactionalRequest(connection.new Request<DefaultResultSet>((ResultEventHandler<DefaultResultSet>) eventHandler, resultSet) {

			@Override
			protected void execute() throws Exception {
				logger.debug("Issuing prepare execute query : {}",stmtName);
				connection.write(new AbstractFrontendMessage[] {
						BIND,
						PgPreparedStatement.DEFAULT_DESCRIBE,
						PgPreparedStatement.DEFAULT_EXECUTE,
						SimpleFrontendMessage.SYNC
					});
			}
			
			@Override
			public String toString() {
				return "Execute prepare query: "+stmtName;
			}
			
		});
	}
	
	@Override
	public DbFuture<ResultSet> executeQuery(Map<Object, Object> params) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DbFuture<Result> executeUpdate(Object... params) {
		FormatCode[] fCode = new FormatCode[params.length];		
		String parameterValue[] = new String[params.length];
		
		for(int i=0 ; i<params.length ; i++){
			fCode[i] = FormatCode.TEXT;
			parameterValue[i] = params[i].toString();
		}
		
		final AbstractPgConnection connection = (AbstractPgConnection)this.getSession();
		final BindMessage BIND = new BindMessage(this.stmtName, null, fCode, parameterValue, null);
		return connection.enqueueTransactionalRequest(connection.new Request<Result>() {

			@Override
			protected void execute() throws Exception {
				logger.debug("Issuing update statement: {}",stmtName);
				connection.write(new AbstractFrontendMessage[] {
						BIND,
						PgPreparedStatement.DEFAULT_DESCRIBE,
						PgPreparedStatement.DEFAULT_EXECUTE,
						SimpleFrontendMessage.SYNC
					});
			}
			
			@Override
			public String toString() {
				return "Execute update for statement: "+stmtName;
			}
		});
	}

	@Override
	public DbFuture<Result> executeUpdate(Map<Object, Object> params) {
		// TODO Auto-generated method stub
		return null;
	}
	
	protected void setParamCount(int paramCount){
		this.paramCount = paramCount;
	}
	
	protected void setParamOID(int[] paramOID){
		this.paramOID = paramOID;
	}
	
	public int getParamCount(){
		return this.paramCount;
	}
	
	public int[] getParamOID(){
		return this.paramOID;
	}
	
	public int getId(){
		return this.Id;
	}
	
	public DbFuture<Void> close(){
		final AbstractPgConnection connection = (AbstractPgConnection)this.getSession();
		return connection.enqueueTransactionalRequest(connection.new Request<Void>() {

			@Override
			protected void execute() throws Exception {
				logger.debug("Issuing statement close: {}",stmtName);
				connection.write(new AbstractFrontendMessage[] {
						CloseMessage.createCloseStatementMessage(stmtName),
						SimpleFrontendMessage.SYNC
					});
			}
			
			@Override
			public String toString() {
				return "Close statement: "+stmtName;
			}
		});
	}
}
