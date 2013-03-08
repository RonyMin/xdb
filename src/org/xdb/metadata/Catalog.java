package org.xdb.metadata;

import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xdb.Config;
import org.xdb.error.EnumError;
import org.xdb.error.Error;
import org.xdb.logging.XDBLog;

public class Catalog {
	private static java.sql.Connection conn = null;

	private static HashMap<Long, Attribute> attributes = new HashMap<Long, Attribute>();

	private static HashMap<Long, Connection> connections = new HashMap<Long, Connection>();
	private static HashMap<String, Connection> connectionsByName = new HashMap<String, Connection>();

	private static HashMap<Long, Partition> partitions = new HashMap<Long, Partition>();
	private static HashMap<Long, Partition> partitionsByName = new HashMap<Long, Partition>();

	private static HashMap<Long, List<TableToConnection>> tableToConnByTableOid = new HashMap<Long, List<TableToConnection>>();
	private static HashMap<Long, List<TableToConnection>> tableToConnByConnOid = new HashMap<Long, List<TableToConnection>>();

	private static HashMap<Long, Schema> schemas = new HashMap<Long, Schema>();
	private static HashMap<String, Schema> schemasByName = new HashMap<String, Schema>();

	private static HashMap<Long, Table> tables = new HashMap<Long, Table>();
	private static HashMap<String, Table> tablesByName = new HashMap<String, Table>();

	private static HashMap<Long, Function> functions = new HashMap<Long, Function>();
	private static HashMap<String, Function> functionsByName = new HashMap<String, Function>();

	private static Logger log = XDBLog.getLogger(Catalog.class.getName());

	public static synchronized Error delete() {
		try {
			Class.forName(Config.METADATA_DRIVER_CLASS);
			Catalog.conn = DriverManager.getConnection(Config.METADATA_DB_URL,
					Config.METADATA_USER, Config.METADATA_PASSWORD);

			Error lastError = Error.NO_ERROR;

			lastError = Catalog.executeUpdate(TableToConnection.sqlDeleteAll());
			if (lastError != Error.NO_ERROR) {
				return lastError;
			}

			lastError = Catalog.executeUpdate(Attribute.sqlDeleteAll());
			if (lastError != Error.NO_ERROR) {
				return lastError;
			}

			lastError = Catalog.executeUpdate(Partition.sqlDeleteAll());
			if (lastError != Error.NO_ERROR) {
				return lastError;
			}

			lastError = Catalog.executeUpdate(Table.sqlDeleteAll());
			if (lastError != Error.NO_ERROR) {
				return lastError;
			}

			lastError = Catalog.executeUpdate(Schema.sqlDeleteAll());
			if (lastError != Error.NO_ERROR) {
				return lastError;
			}

			lastError = Catalog.executeUpdate(Connection.sqlDeleteAll());
			if (lastError != Error.NO_ERROR) {
				return lastError;
			}

			lastError = Catalog.executeUpdate(Function.sqlDeleteAll());
			if (lastError != Error.NO_ERROR) {
				return lastError;
			}

			Catalog.conn.close();
			Catalog.unload();

		} catch (Exception e) {
			return createCatalogNotAvailableErr(e);
		}

		return Error.NO_ERROR;
	}

	public static synchronized Error load() {
		Error lastError;

		try {
			Class.forName(Config.METADATA_DRIVER_CLASS);
			Catalog.conn = DriverManager.getConnection(Config.METADATA_DB_URL,
					Config.METADATA_USER, Config.METADATA_PASSWORD);
			Catalog.initConnectionToTable();
			Catalog.initPartitions();
			Catalog.initConnections();
			Catalog.initSchemas();
			Catalog.initTables();
			Catalog.initAttributes();
			Catalog.initFunctions();

			lastError = Catalog.checkCatalog();
			if (lastError != Error.NO_ERROR) {
				return lastError;
			}
		} catch (Exception e) {
			return createCatalogNotAvailableErr(e);
		}
		return Error.NO_ERROR;
	}

	public static synchronized Error unload() {
		Catalog.attributes.clear();
		Catalog.connections.clear();
		Catalog.connectionsByName.clear();
		Catalog.schemas.clear();
		Catalog.schemasByName.clear();
		Catalog.tables.clear();
		Catalog.tablesByName.clear();
		Catalog.functions.clear();
		Catalog.functionsByName.clear();
		Catalog.partitions.clear();
		Catalog.partitionsByName.clear();
		Catalog.tableToConnByConnOid.clear();
		Catalog.tableToConnByTableOid.clear();

		try {
			Catalog.conn.close();
		} catch (Exception e) {
			return createCatalogNotAvailableErr(e);
		}

		return Error.NO_ERROR;
	}

	public static synchronized Error createCatalogNotAvailableErr(Exception e,
			String detail) {
		String arg0 = e.toString();
		String args[] = { arg0, detail };
		return new Error(EnumError.CATALOG_NOT_AVAILABLE, args);
	}

	public static synchronized Error createCatalogNotAvailableErr(Exception e) {
		String arg0 = e.toString();
		String args[] = { arg0, "undefined reason" };
		return new Error(EnumError.CATALOG_NOT_AVAILABLE, args);
	}

	public static synchronized Error createObjectAlreadyExistsErr(
			AbstractDatabaseObject object) {
		String args[] = { object.getName(), object.getObjectType().toString() };
		Error error = new Error(EnumError.CATALOG_OBJECT_ALREADY_EXISTS, args);
		return error;
	}

	public static synchronized Error createObjectNotExistsErr(String name,
			EnumDatabaseObject type) {
		String args[] = { name, type.toString() };
		Error error = new Error(EnumError.CATALOG_OBJECT_NOT_EXISTS, args);
		return error;
	}

	public static synchronized Error createObjectValueTooLongErr(String name,
			EnumDatabaseObject type) {
		String args[] = { name, type.toString() };
		Error error = new Error(EnumError.CATALOG_OBJECT_VALUE_TOO_LONG, args);
		return error;
	}

	public static synchronized Error createTableContainsDupAttErr(
			String tableName, String attName) {
		String[] args = { tableName, attName };
		Error lastError = new Error(EnumError.CATALOG_TABLE_DUP_ATTS, args);
		return lastError;
	}

	private static synchronized Error checkCatalog() {
		if (Catalog.getSchema(Config.COMPILE_DEFAULT_SCHEMA) == null) {
			return Catalog.createObjectNotExistsErr(
					Config.COMPILE_DEFAULT_SCHEMA, EnumDatabaseObject.SCHEMA);
		}
		return Error.NO_ERROR;
	}

	private static synchronized Error executeUpdate(String sql) {
		try {
			log.log(Level.INFO, sql);
			Statement stmt = Catalog.conn.createStatement();
			stmt.executeUpdate(sql);
		} catch (Exception e) {
			return createCatalogNotAvailableErr(e);
		}
		return Error.NO_ERROR;
	}

	// to deal with partitions
	private static synchronized void initPartitions() throws Exception {
		Statement stmt = null;
		ResultSet rs = null;
		stmt = Catalog.conn.createStatement();
		rs = stmt.executeQuery(Partition.sqlSelectMaxOid());
		if (rs.next()) {
			Partition.LAST_OID(rs.getLong(1));
		}

		stmt = Catalog.conn.createStatement();
		rs = stmt.executeQuery(Partition.sqlSelectAll());
		while (rs.next()) {
			// add attribute to catalog
			long oid = rs.getLong(1);
			String source_name = rs.getString(2);
			String source_schema = rs.getString(3);
			String source_partition_name = rs.getString(4);
			long tableOid = rs.getLong(5);
			String partition_name = rs.getString(6);
			long connection_oid = rs.getLong(7);
			Partition part = new Partition(oid, source_name, source_schema,
					source_partition_name, tableOid, partition_name,
					connection_oid);
			Catalog.addPartition(part);

		}

	}

	private static synchronized void initAttributes() throws Exception {
		Statement stmt = null;
		ResultSet rs = null;
		stmt = Catalog.conn.createStatement();

		rs = stmt.executeQuery(Attribute.sqlSelectMaxOid());
		if (rs.next()) {
			Attribute.LAST_OID(rs.getLong(1));
		}

		stmt = Catalog.conn.createStatement();
		rs = stmt.executeQuery(Attribute.sqlSelectAll());
		while (rs.next()) {
			// add attribute to catalog
			long oid = rs.getLong(1);
			String name = rs.getString(2);
			int type = rs.getInt(3);
			long tableOid = rs.getLong(4);
			Attribute att = new Attribute(oid, name, type, tableOid);
			Catalog.addAttribute(att);

			// add attribute to table
			Catalog.tables.get(tableOid).addAttribute(att);
		}
	}

	private static synchronized void initConnectionToTable() throws Exception {
		Statement stmt = null;
		ResultSet rs = null;

		stmt = Catalog.conn.createStatement();
		rs = stmt.executeQuery(TableToConnection.sqlSelectAll());
		while (rs.next()) {
			long t_oid = rs.getLong(1);
			long c_oid = rs.getLong(2);
			TableToConnection tableToConnec = new TableToConnection(t_oid,
					c_oid);
			Catalog.addTableToConnection(tableToConnec);
		}
	}

	private static synchronized void initConnections() throws Exception {
		Statement stmt = null;
		ResultSet rs = null;
		stmt = Catalog.conn.createStatement();

		rs = stmt.executeQuery(Connection.sqlSelectMaxOid());
		if (rs.next()) {
			Connection.LAST_OID(rs.getLong(1));
		}

		stmt = Catalog.conn.createStatement();
		rs = stmt.executeQuery(Connection.sqlSelectAll());
		while (rs.next()) {
			long oid = rs.getLong(1);
			String name = rs.getString(2);
			String url = rs.getString(3);
			String user = rs.getString(4);
			String passwd = rs.getString(5);
			int store = rs.getInt(6);
			Connection conn = new Connection(oid, name, url, user, passwd,
					store);
			Catalog.addConnection(conn);
		}
	}

	private static synchronized void initSchemas() throws Exception {
		Statement stmt = null;
		ResultSet rs = null;
		stmt = Catalog.conn.createStatement();

		rs = stmt.executeQuery(Schema.sqlSelectMaxOid());
		if (rs.next()) {
			Schema.LAST_OID(rs.getLong(1));
		}

		stmt = Catalog.conn.createStatement();
		rs = stmt.executeQuery(Schema.sqlSelectAll());
		while (rs.next()) {
			long oid = rs.getLong(1);
			String name = rs.getString(2);
			Schema schema = new Schema(oid, name);
			Catalog.addSchema(schema);
		}
	}

	private static synchronized void initTables() throws Exception {
		Statement stmt = null;
		ResultSet rs = null;
		stmt = Catalog.conn.createStatement();

		rs = stmt.executeQuery(Table.sqlSelectMaxOid());
		if (rs.next()) {
			Table.LAST_OID(rs.getLong(1));
		}

		stmt = Catalog.conn.createStatement();
		rs = stmt.executeQuery(Table.sqlSelectAll());
		while (rs.next()) {
			long oid = rs.getLong(1);
			String name = rs.getString(2);
			String sourceName = rs.getString(3);
			String sourceSchema = rs.getString(4);
			long schemaOid = rs.getLong(5);

			Table table = new Table(oid, name, sourceName, sourceSchema,
					schemaOid);
			Catalog.addTable(table);
		}
	}

	private static synchronized void initFunctions() throws Exception {
		Statement stmt = null;
		ResultSet rs = null;
		stmt = Catalog.conn.createStatement();

		rs = stmt.executeQuery(Table.sqlSelectMaxOid());
		if (rs.next()) {
			Table.LAST_OID(rs.getLong(1));
		}

		stmt = Catalog.conn.createStatement();
		rs = stmt.executeQuery(Function.sqlSelectAll());
		while (rs.next()) {
			long oid = rs.getLong(1);
			String name = rs.getString(2);
			long schemaOid = rs.getLong(3);
			int language = rs.getInt(4);
			String source = rs.getString(5);

			Function function = new Function(oid, name, schemaOid, language,
					source);
			Catalog.addFunction(function);
		}
	}

	public static synchronized Error createAttribute(Attribute att) {
		if (Catalog.attributes.containsKey(att.getOid())) {
			return createObjectAlreadyExistsErr(att);
		}

		Error lastError = Catalog.executeUpdate(att.sqlInsert());
		if (lastError == Error.NO_ERROR) {
			Catalog.addAttribute(att);
		}

		return lastError;
	}

	public static synchronized Error dropAttribute(Attribute att) {
		Error lastError = Catalog.executeUpdate(att.sqlDelete());
		if (lastError == Error.NO_ERROR) {
			Catalog.removeAttribute(att);
		}
		return lastError;
	}

	public static synchronized Error createPartition(Partition part) {
		if (Catalog.partitions.containsKey(part.getOid())) {
			return createObjectAlreadyExistsErr(part);
		}

		Error lastError = Catalog.executeUpdate(part.sqlInsert());
		if (lastError == Error.NO_ERROR) {
			Catalog.addPartition(part);
		}

		return lastError;
	}

	public static synchronized Error dropPartition(Partition part) {
		Error lastError = Catalog.executeUpdate(part.sqlDelete());
		if (lastError == Error.NO_ERROR) {
			Catalog.removePartition(part);
		}
		return lastError;
	}

	public static synchronized Error createConncection(Connection conn) {
		if (Catalog.connections.containsKey(conn.getOid())) {
			return createObjectAlreadyExistsErr(conn);
		}

		Error lastError = Catalog.executeUpdate(conn.sqlInsert());
		if (lastError == Error.NO_ERROR) {
			Catalog.addConnection(conn);
		}
		return lastError;
	}

	public static synchronized Error dropConnection(Connection conn) {
		Error lastError = Catalog.executeUpdate(conn.sqlDelete());
		if (lastError == Error.NO_ERROR) {
			Catalog.removeConnection(conn);
		}
		return lastError;
	}

	public static synchronized Error createTableToConnection(
			TableToConnection tableToConn) {
		if (Catalog.tableToConnByConnOid
				.containsKey(tableToConn.getTable_oid())
				&& Catalog.tableToConnByConnOid.containsKey(tableToConn
						.getConnection_oid())) {
			return createObjectAlreadyExistsErr(tableToConn);
		}
		// Catalog.addTableToConnection(tableToConn);

		Error lastError = Catalog.executeUpdate(tableToConn.sqlInsert());
		if (lastError == Error.NO_ERROR) {
			Catalog.addTableToConnection(tableToConn);
		}
		return lastError;
	}

	public static synchronized Error dropTableToConnection(
			TableToConnection conn) {
		Error lastError = Catalog.executeUpdate(conn.sqlDelete());
		if (lastError == Error.NO_ERROR) {
			Catalog.removeTableToConnection(conn);
		}
		return lastError;
	}

	public static synchronized Error createSchema(Schema schema) {
		if (Catalog.schemas.containsKey(schema.getOid())) {
			return createObjectAlreadyExistsErr(schema);
		}

		Error lastError = Catalog.executeUpdate(schema.sqlInsert());
		if (lastError == Error.NO_ERROR) {
			Catalog.addSchema(schema);
		}
		return lastError;
	}

	public static synchronized Error dropSchema(Schema schema) {
		Error lastError = Catalog.executeUpdate(schema.sqlDelete());
		if (lastError == Error.NO_ERROR) {
			Catalog.removeSchema(schema);
		}
		return lastError;
	}

	public static synchronized Error createTable(Table table) {
		if (Catalog.tables.containsKey(table.getOid())) {
			return createObjectAlreadyExistsErr(table);
		}

		Error lastError = Catalog.executeUpdate(table.sqlInsert());
		if (lastError == Error.NO_ERROR) {
			Catalog.addTable(table);
		}
		return lastError;
	}

	public static synchronized Error dropTable(Table table) {

		// drop Table to Connections entry
		Error lastError = Error.NO_ERROR;
		ArrayList<TableToConnection> taToCosTmp = (ArrayList<TableToConnection>) Catalog.tableToConnByTableOid
				.get(table.getOid());
	
		// check if replicated table
		if (taToCosTmp != null) {
			@SuppressWarnings("unchecked")
			ArrayList<TableToConnection> taToCos = (ArrayList<TableToConnection>) taToCosTmp
					.clone();
			TableToConnection tableToConnection;
			for (int i = 0; i < taToCos.size(); i++) {
				tableToConnection = taToCos.get(i);
				lastError = Catalog.dropTableToConnection(tableToConnection);
				if (lastError != Error.NO_ERROR)
					return lastError;
			}
		}

		// drop attributes
		for (Attribute att : table.getAttributes()) {
			lastError = Catalog.dropAttribute(att);
			if (lastError != Error.NO_ERROR)
				return lastError;

			Catalog.removeAttribute(att);
		}

		// drop partitions
		for (Partition part : table.getPartitions()) {
			lastError = Catalog.dropPartition(part);
			if (lastError != Error.NO_ERROR)
				return lastError;
			Catalog.removePartition(part);
		}

		// drop table
		lastError = Catalog.executeUpdate(table.sqlDelete());
		if (lastError == Error.NO_ERROR) {
			Catalog.removeTable(table);
		}
		return lastError;
	}

	public static synchronized Error createFunction(Function function) {
		if (Catalog.functions.containsKey(function.getOid())) {
			return createObjectAlreadyExistsErr(function);
		}

		Error lastError = Catalog.executeUpdate(function.sqlInsert());
		if (lastError == Error.NO_ERROR) {
			Catalog.addFunction(function);
		}
		return lastError;
	}

	public static synchronized Error dropFunction(Function function) {
		Error lastError = Catalog.executeUpdate(function.sqlDelete());
		if (lastError == Error.NO_ERROR) {
			Catalog.removeFunction(function);
		}
		return lastError;
	}

	public static synchronized void addAttribute(Attribute att) {
		Catalog.attributes.put(att.getOid(), att);
	}

	public static synchronized void removeAttribute(Attribute att) {
		Catalog.attributes.remove(att.getOid());
	}

	public static synchronized void addPartition(Partition part) {
		Catalog.partitions.put(part.getOid(), part);
	}

	public static synchronized void removePartition(Partition part) {
		Catalog.partitions.remove(part.getOid());
	}

	public static synchronized void addTableToConnection(
			TableToConnection taToCo) {
		List<TableToConnection> values1 = Catalog.tableToConnByConnOid
				.get(taToCo.getConnection_oid());
		if (values1 == null) {
			values1 = new ArrayList<TableToConnection>();
		}
		values1.add(taToCo);
		Catalog.tableToConnByConnOid.put(taToCo.getConnection_oid(), values1);

		List<TableToConnection> values2 = Catalog.tableToConnByTableOid
				.get(taToCo.getTable_oid());
		if (values2 == null) {
			values2 = new ArrayList<TableToConnection>();
		}
		values2.add(taToCo);
		Catalog.tableToConnByTableOid.put(taToCo.getTable_oid(), values2);
	}

	public static synchronized void removeTableToConnection(
			TableToConnection taToCo) {
		List<TableToConnection> values1 = Catalog.tableToConnByConnOid
				.get(taToCo.getConnection_oid());
		values1.remove(taToCo);
		if (values1 != null) {
			if (values1.size() == 0) {
				Catalog.tableToConnByConnOid.remove(taToCo.getConnection_oid());
			} else {
				Catalog.tableToConnByConnOid.put(taToCo.getConnection_oid(),
						values1);
			}
		}
		List<TableToConnection> values2 = Catalog.tableToConnByTableOid
				.get(taToCo.getTable_oid());
		if (values2 != null) {
			values2.remove(taToCo);
			if (values1.size() == 0) {
				Catalog.tableToConnByTableOid.remove(taToCo.getTable_oid());
			} else {
				Catalog.tableToConnByTableOid.put(taToCo.getTable_oid(),
						values2);
			}
		}

	}

	public static synchronized void addConnection(Connection conn) {
		Catalog.connections.put(conn.getOid(), conn);
		Catalog.connectionsByName.put(conn.hashKey(), conn);
	}

	public static synchronized void removeConnection(Connection conn) {
		Catalog.connections.remove(conn.getOid());
		Catalog.connectionsByName.remove(conn.hashKey());
	}

	public static synchronized void addSchema(Schema schema) {
		Catalog.schemas.put(schema.getOid(), schema);
		Catalog.schemasByName.put(schema.hashKey(), schema);
	}

	public static synchronized void removeSchema(Schema schema) {
		Catalog.schemas.remove(schema.getOid());
		Catalog.schemasByName.remove(schema.hashKey());
	}

	public static synchronized void addTable(Table table) {
		Catalog.tables.put(table.getOid(), table);
		Catalog.tablesByName.put(table.hashKey(), table);
	}

	public static synchronized void removeTable(Table table) {
		Catalog.tables.remove(table.getOid());
		Catalog.tablesByName.remove(table.hashKey());
	}

	public static synchronized void addFunction(Function function) {
		Catalog.functions.put(function.getOid(), function);
		Catalog.functionsByName.put(function.hashKey(), function);
	}

	public static synchronized void removeFunction(Function function) {
		Catalog.tables.remove(function.getOid());
		Catalog.tablesByName.remove(function.hashKey());
	}

	public static synchronized Attribute getAttribute(long oid) {
		return Catalog.attributes.get(oid);
	}

	public static synchronized Partition getPartition(long oid) {
		return Catalog.partitions.get(oid);
	}

	public static synchronized Connection getConnection(long oid) {
		return Catalog.connections.get(oid);
	}

	public static synchronized Connection getConnection(String key) {
		return Catalog.connectionsByName.get(key);
	}

	public static synchronized Schema getSchema(long oid) {
		return Catalog.schemas.get(oid);
	}

	public static synchronized Schema getSchema(String key) {
		return Catalog.schemasByName.get(key);
	}

	public static synchronized Table getTable(long oid) {
		return Catalog.tables.get(oid);
	}

	public static synchronized Table getTable(String key) {
		return Catalog.tablesByName.get(key);
	}

	public static synchronized Function getFunction(long oid) {
		return Catalog.functions.get(oid);
	}

	public static synchronized Function getFunction(String key) {
		return Catalog.functionsByName.get(key);
	}

	// needed Interface operations
	public static List<Connection> getConnectionsForTable(String tablename) {
		Table table = Catalog.tablesByName.get(tablename);

		List<TableToConnection> taToCo = tableToConnByTableOid.get(table
				.getOid());

		List<Connection> connections = new ArrayList<Connection>();
		for (TableToConnection ttc : taToCo) {
			connections.add(Catalog.connections.get(ttc.getConnection_oid()));
		}
		return connections;
	}
}