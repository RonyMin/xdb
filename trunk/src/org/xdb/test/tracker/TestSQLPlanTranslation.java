package org.xdb.test.tracker;

import junit.framework.Assert;

import org.junit.Test;
import org.xdb.funsql.compile.FunSQLCompiler;
import org.xdb.funsql.statement.AbstractServerStmt;
import org.xdb.funsql.statement.CreateFunctionStmt;
import org.xdb.test.CompileServerTestCase;
import org.xdb.tracker.QueryTrackerNode;
import org.xdb.tracker.QueryTrackerPlan;

public class TestSQLPlanTranslation extends CompileServerTestCase {
	
	private QueryTrackerNode qTracker;
	@Override
	/**
	 * Setup common statements (connect, drop, ...)
	 */
	public void setUp() {
		super.setUp();
		try {
			this.qTracker = new QueryTrackerNode();
		} catch (Exception e) {
			e.printStackTrace();
			Assert.assertTrue(true);
		}
	}
	
	@Test
	public void testSimpleCreate() {
		FunSQLCompiler compiler = new FunSQLCompiler();
		compiler.doOptimize(true);
		
		// create connection -> no error
		String dropConnSql = "DROP CONNECTION \"testConnection\"";
		AbstractServerStmt stmt = compiler.compile(dropConnSql);
		if (stmt != null)
			this.execute(stmt);

		String createConnSql = "CREATE CONNECTION \"testConnection\" "
				+ "URL 'jdbc:mysql://127.0.0.1/xdb_tmp' " + "USER 'xroot' "
				+ "PASSWORD 'xroot' " + "STORE 'XDB' ";

		stmt = compiler.compile(createConnSql);
		this.assertNoError(compiler.getLastError());
		Assert.assertNotNull(stmt);
		this.execute(stmt);

		// create table
		String dropTableSql = "DROP TABLE \"R\"";
		stmt = compiler.compile(dropTableSql);
		if (stmt != null)
			this.execute(stmt);

		String createTableStmt = "CREATE TABLE \"R\"( " + "  A INT,"
				+ "  B VARCHAR," + "  C INT"
				+ ") IN CONNECTION \"testConnection\"";

		stmt = compiler.compile(createTableStmt);
		this.assertNoError(compiler.getLastError());
		Assert.assertNotNull(stmt);
		this.execute(stmt);

		dropTableSql = "DROP TABLE \"S\"";
		stmt = compiler.compile(dropTableSql);
		if (stmt != null)
			this.execute(stmt);

		createTableStmt = "CREATE TABLE \"S\"( " + "  D INT," + "  E VARCHAR,"
				+ "  F INT" + ") IN CONNECTION \"testConnection\"";

		stmt = compiler.compile(createTableStmt);
		this.assertNoError(compiler.getLastError());
		Assert.assertNotNull(stmt);
		this.execute(stmt);

		// execute CreateFunction
		CreateFunctionStmt fStmt = (CreateFunctionStmt) compiler.compile(""
				+ "CREATE FUNCTION f1( OUT o1 TABLE, OUT o2 TABLE) \n" 
				+ "BEGIN \n"
				+ "VAR v1 = SELECT R1.A AS A1, R2.D AS A2 "
					+ "FROM R AS R1, S AS R2 " 
					+ "WHERE R1.B=R2.E AND R1.C=1; \n"
				+ "VAR v2 = SELECT V1.A1 AS A, V2.F AS B "
					+ "FROM :v1 AS V1, S AS V2 " 
					+ "WHERE V1.A1=3 AND V2.F=V1.A2; \n"
				+ ":o1 = SELECT R1.A FROM :v2 as R1; \n"
				+ ":o2 = SELECT R1.A2 FROM :v1 as R1 "
					+ "WHERE R1.A1=1; \n"
				+ "END; ");
		this.assertNoError(compiler.getLastError());
		fStmt.getPlan().tracePlan(this.getClass().getName()+"_Compiler");
		this.assertNoError(fStmt.execute());
		
		QueryTrackerPlan qPlan = qTracker.generateQueryTrackerPlan(fStmt.getPlan());
		Assert.assertNotNull(qPlan);
		qPlan.tracePlan(this.getClass().getName()+"_Tracker");
		
		assertEquals(qPlan.getTrackerOperators().size(), 3);
	}
}