package org.xdb.server;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.logging.Level;

import org.xdb.Config;
import org.xdb.error.Error;
import org.xdb.execute.operators.AbstractExecuteOperator;
import org.xdb.funsql.compile.CompilePlan;
import org.xdb.tracker.QueryTrackerNode;
import org.xdb.tracker.QueryTrackerNodeDesc;

public class QueryTrackerServer extends AbstractServer {

	public static final int CMD_EXECUTE_PLAN = 1;
	public static final int CMD_OPERATOR_READY = 2;

	private final QueryTrackerNode tracker;

	public QueryTrackerServer() throws Exception{
		super();

		port = Config.QUERYTRACKER_PORT;
		tracker = new QueryTrackerNode();
	}

	public QueryTrackerNode getNode(){
		return this.tracker;
	}
	
	public QueryTrackerNodeDesc getDescription() {
		return this.tracker.getDescription();
	}


	private class Handler extends AbstractHandler {
		// constructor
		public Handler(final Socket client) {
			super(client);
			logger = QueryTrackerServer.this.logger;
		}

		/**
		 * Handle incoming cmd
		 * 
		 * @return
		 * @throws IOException
		 */
		@Override
		protected Error handle(final ObjectOutputStream out, final ObjectInputStream in) throws IOException {
			Error err = new Error();

			final int cmd = in.readInt();
			try {
				switch (cmd) {
				case CMD_STOP_SERVER:
					logger.log(Level.INFO, "Received CMD_STOP_SERVER");
					QueryTrackerServer.this.stopServer();
					break;
				case CMD_PING_SERVER:
					logger.log(Level.INFO, "Received CMD_PING_SERVER");
					break;
				case CMD_EXECUTE_PLAN:
					//got new compile plan
					final CompilePlan cplan = (CompilePlan) in.readObject();
					
					err = tracker.executePlan(cplan);
					break;
				case CMD_OPERATOR_READY:
					final AbstractExecuteOperator op = (AbstractExecuteOperator) in.readObject();
					err = tracker.operatorReady(op);
				}
				out.writeObject(err);
				out.flush();
			} catch (final Exception e) {
				err = createServerError(e);
			}

			return err;
		}
	}

	@Override
	protected void handle(final Socket client) {
		final Handler handler = new Handler(client);
		handler.start();
	}
	
	/**
	 * @param args
	 * @throws Exception 
	 */
	public static void main(final String[] args) throws Exception {
		final QueryTrackerServer server = new QueryTrackerServer();
		server.startServer();
	}

}
