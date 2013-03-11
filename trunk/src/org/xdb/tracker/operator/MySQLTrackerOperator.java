package org.xdb.tracker.operator;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

import org.xdb.execute.operators.AbstractExecuteOperator;
import org.xdb.execute.operators.MySQLExecuteOperator;
import org.xdb.execute.operators.OperatorDesc;
import org.xdb.funsql.compile.tokens.AbstractToken;
import org.xdb.utils.Identifier;
import org.xdb.utils.StringTemplate;

public class MySQLTrackerOperator extends AbstractTrackerOperator {

	private static final long serialVersionUID = -6394800229111645825L;
	private Vector<StringTemplate> executeSQLs = new Vector<StringTemplate>();

	// constructors
	public MySQLTrackerOperator() {
	}

	// getters and setters
	public void addExecuteSQL(StringTemplate dml) {
		this.executeSQLs.add(dml);
	}

	public Collection<StringTemplate> getExecuteSQLs() {
		return executeSQLs;
	}

	// methods

	@Override
	public AbstractExecuteOperator genDeployOperator(OperatorDesc operDesc,
			Map<Identifier, OperatorDesc> currentDeployment) {

		Identifier deployOperId = operDesc.getOperatorID();
		MySQLExecuteOperator deployOper = new MySQLExecuteOperator(deployOperId);

		HashMap<String, String> args = new HashMap<String, String>();

		// generate DDLs to open operator
		for (String tableName : this.inTables.keySet()) {
			TableDesc inTableDesc = this.inTableDesc.get(tableName);
			String deployTableName = genDeployTableName(tableName, deployOperId);

			if (inTableDesc.isTemp()) { // temporary table for intermediate
										// results
				OperatorDesc sourceOp = currentDeployment.get(inTableDesc
						.getOperatorID());
				String sourceURL = sourceOp.getComputeSlot().getHost();
				String sourceTableName = inTableDesc.getTableName();
		
				Identifier sourceOperId = sourceOp.getOperatorID();

				String deployTableDDL = this.genDeployInputTableDDL(tableName,
						deployOperId, sourceTableName, sourceOperId, sourceURL);
				deployOper.addOpenSQL(deployTableDDL);

				//Check if URL is equal to local host or
				//if deployed URL is equal to URL of table?
				//If yes, then do not use federated table
				InetAddress sourceAddress = null;
				try {
					sourceAddress =InetAddress.getByName(sourceURL);
				} catch (UnknownHostException e) {
					e.printStackTrace();
				}

				//this needs more work: use low level table name instead of xdb
				//check if federated engine is necessary
				if(sourceAddress.isAnyLocalAddress()|| sourceAddress.isLoopbackAddress()){
					//get desc of source Table
					Identifier sourceDeployOperId = sourceOp.getOperatorID();
					//set different deploymentname
					deployTableName = genDeployTableName(sourceTableName, sourceDeployOperId);
				}

				args.put(tableName, "SELECT * FROM " + deployTableName);

			} else { // table is stored in an XDB instance
				URI connURI = inTableDesc.getURI();
				String sourceTableName = inTableDesc.getTableName();
				String sourceURL = connURI.getHost();
				String sourceDB = connURI.getPath().substring(1);

				String deployTableDDL = this.genDeployInputTableDDL(
							tableName, deployOperId, sourceTableName, sourceDB,
							sourceURL);
				deployOper.addOpenSQL(deployTableDDL);

				//Check if URL is equal to local host or
				//if deployed URL is equal to URL of table?
				//If yes, then do not use federated table
				//is local and if is Currentdeployment = source URL
				InetAddress sourceAddress = null;
				try {
					sourceAddress =InetAddress.getByName(sourceURL);
				} catch (UnknownHostException e) {
					e.printStackTrace();
				}

				//Check first if its's running on localhost
				//Second check if soure Table has the same URL as the current Node
				if(sourceAddress.isAnyLocalAddress()|| sourceAddress.isLoopbackAddress()){
					deployTableName = sourceDB + "." + sourceTableName;
				}else if (sourceURL.equals(operDesc.getComputeSlot())){
					deployTableName = sourceDB + "." + sourceTableName;
				}
				/*if(sourceURL.equals("127.0.0.1")){
					deployTableName = sourceDB + "." + sourceTableName;
				}*/
				args.put(tableName, deployTableName);
			}
		}

		for (String tableName : this.outTables.keySet()) {

			String deployTableDDL = this.genDeployOutputTableDDL(tableName,
					deployOperId);

			String deployTableName = genDeployTableName(tableName, deployOperId);
			deployOper.addOpenSQL(deployTableDDL);
			args.put(tableName, deployTableName);
		}

		// generate DMLs to execute operator
		for (StringTemplate executeSQL : this.executeSQLs) {
			deployOper.addExecuteSQL(executeSQL.toString(args));
		}

		// generate DDLs to close operator
		for (String tableName : this.inTables.keySet()) {
			deployOper.addCloseSQL(genDropDeployTableDDL(tableName,
					deployOperId));
		}

		for (String tableName : this.outTables.keySet()) {
			deployOper.addCloseSQL(genDropDeployTableDDL(tableName,
					deployOperId));
		}

		return deployOper;
	}

	public String toString() {
		StringBuilder builder = new StringBuilder();

		for (StringTemplate exeSQL : executeSQLs) {
			builder.append(exeSQL.toString());
			builder.append(AbstractToken.NEWLINE);
		}

		return builder.toString();
	}
}
