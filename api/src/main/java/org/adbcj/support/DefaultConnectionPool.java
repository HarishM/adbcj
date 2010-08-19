package org.adbcj.support;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.adbcj.Connection;
import org.adbcj.ConnectionManager;
import org.adbcj.DbException;
import org.adbcj.DbFuture;
import org.adbcj.DbListener;
import org.adbcj.DbSession;
import org.adbcj.DbSessionFuture;
import org.adbcj.PreparedStatement;
import org.adbcj.Result;
import org.adbcj.ResultEventHandler;
import org.adbcj.ResultSet;

public class DefaultConnectionPool implements ConnectionPool {

	private int currentCount;
	private final int maxCount;
	
	private final Queue<DbSession> sessions = new ConcurrentLinkedQueue<DbSession>();
	private final Queue<DefaultDbFuture<DbSession>> sessionFutureQueue = new ConcurrentLinkedQueue<DefaultDbFuture<DbSession>>();
	private final ConnectionManager connectionManager;
	private final AtomicInteger count = new AtomicInteger(1);

	public DefaultConnectionPool(ConnectionManager connectionManager, int fixedCount) {
		this(connectionManager, fixedCount, fixedCount);
	}
	
	public DefaultConnectionPool(ConnectionManager connectionManager, int minCount, int maxCount) {
		this.connectionManager = connectionManager;
		this.currentCount = minCount;
		this.maxCount = maxCount;
		
		while(minCount-- > 0){
			addConnection();
			System.err.println("Pool Size :\t"+ count.get());
			System.out.println("Pool Size :\t"+ count.getAndIncrement());
		}
		
		Thread t = new Thread(new QueueProcessor());
		t.start();
	}
	
	private void addConnection() {
		DbSession session = connectionManager.connect().getUninterruptably();
		sessions.add(new PooledDbSession(session));
	}	
	
	@Override
	public DbFuture<DbSession> connect() {
		DefaultDbFuture<DbSession> sessionFuture = new DefaultDbFuture<DbSession>();
		
		synchronized (sessionFutureQueue) {
			sessionFutureQueue.add(sessionFuture);
			sessionFutureQueue.notifyAll();
		}
		return sessionFuture;
		
	}

	private class PooledDbSession implements DbSession{
		
		private final DbSession session;
	
		public PooledDbSession(DbSession session){
			this.session = session;
		}
		
		@Override
		public void beginTransaction() {
			session.beginTransaction();
		}

		@Override
		public DbSessionFuture<Void> commit() {
			return session.commit();
		}

		@Override
		public DbSessionFuture<Void> rollback() {
			return session.rollback();
		}

		@Override
		public boolean isInTransaction() {
			return session.isInTransaction();
		}

		@Override
		public DbSessionFuture<ResultSet> executeQuery(String sql) {
			return session.executeQuery(sql);
		}

		@Override
		public <T> DbSessionFuture<T> executeQuery(String sql, ResultEventHandler<T> eventHandler, T accumulator) {
			return session.executeQuery(sql, eventHandler, accumulator);
		}

		@Override
		public DbSessionFuture<Result> executeUpdate(String sql) {
			return session.executeUpdate(sql);
		}

		@Override
		public DbSessionFuture<PreparedStatement> prepareStatement(String sql) {
			return session.prepareStatement(sql);
		}

		@Override
		public DbSessionFuture<PreparedStatement> prepareStatement(Object key, String sql) {
			return session.prepareStatement(key, sql);
		}

		@Override
		public DbSessionFuture<Void> close(boolean immediate) throws DbException {
			final DbSession session = this;
			
			synchronized (sessions) {
				if(session.isInTransaction()){
					session.rollback().addListener(new DbListener<Void>(){

						@Override
						public void onCompletion(DbFuture<Void> future)
								throws Exception {
							enqueueSession(session);
						}
					});
				}
				else
					enqueueSession(this);
			}
			return DefaultDbSessionFuture.createCompletedFuture(this, null);
		}

		@Override
		public boolean isClosed() throws DbException {
			return false;
		}
	}
	
	private void enqueueSession(DbSession session){
		sessions.add(session);
		sessions.notifyAll();
	}
	
	private class QueueProcessor implements Runnable{

		@Override
		public void run() {
			
			while(true){
				synchronized (sessionFutureQueue) {
					if(sessionFutureQueue.size() == 0){
						try {
							sessionFutureQueue.wait();
						} catch (InterruptedException e) {}
						
						continue;
					}
				}
				final DefaultDbFuture<DbSession> nextSessionFuture  = sessionFutureQueue.poll();
				
				synchronized (sessions) {
					while(sessions.size() == 0){
						if(currentCount == maxCount){
							try {
								sessions.wait();
							} catch (InterruptedException e) {}
							
							continue;
						}
						else if(currentCount < maxCount){
							currentCount++;
							connectionManager.connect().addListener(new DbListener<Connection>() {
								
								@Override
								public void onCompletion(DbFuture<Connection> future) throws Exception {
									System.err.println("Pool Size :\t"+ count.getAndIncrement());
									DbSession session = future.get(); 
									nextSessionFuture.setResult(new PooledDbSession(session));		
								}
							});
						}
					}
				}
				if(nextSessionFuture.isDone())
					continue;
				
				DbSession session = sessions.poll();
				nextSessionFuture.setResult(session);
			}
		}
	}
}
