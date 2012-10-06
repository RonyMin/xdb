package org.xdb.error;

/**
 * Enumeration for error numbers and messages
 * @author cbinnig
 *
 */
public enum EnumError {
	NO_ERROR,
	MYSQL_ERROR,
	SERVER_ERROR,
	CLIENT_ERROR,
	COMPUTE_CMD_INVALID,
	COMPILER_UNDEFINED_ERROR,
	CATALOG_NOT_AVAILABLE,
	CATALOG_OBJECT_ALREADY_EXISTS,
	CATALOG_OBJECT_NOT_EXISTS,
	CATALOG_OBJECT_VALUE_TOO_LONG,
	CREATE_TABLE_DUPLICATE_ATTRIBUTES,
	CREATE_TABLE_TYPE_NOT_EXISTS,
	STORE_NOT_EXISTS,
	STORE_CONNECTION_ERROR,
	STORE_EXECUTION_ERROR,
	COMPILER_GENERIC,
	COMPILER_SELECT_EXPRESSION_NOT_SUPPORTED,
	COMPILER_SELECT_ATTRIBUTE_NOT_IN_TABLE,
	COMPILER_SELECT_ATTRIBUTE_IN_NO_TABLE,
	COMPILER_SELECT_ATTRIBUTE_NOT_UNIQUE,
	COMPILER_SELECT_DUPLICATE_TABLE,
	COMPILER_SELECT_ALIAS_MISSING,
	COMPILER_SELECT_DUPLICATE_ATTRIBUTE,
	COMPILER_FUNCTION_OUT_NOT_INITIALISED,
	COMPILER_FUNCTION_VAR_NOT_DECLARED,
	TRACKER_PLAN_INVALID_GENERIC;
	
	public static String toString(EnumError error, String[] args){
		String msg = "";
		
		switch(error){
		case NO_ERROR:
			msg =  "No error occured!";
			break;
		case MYSQL_ERROR:
			msg =  "MySQL error: \"<arg0>\"";
			break;
		case SERVER_ERROR:
			msg =  "Server error: \"<arg0>\"";
			break;
		case CLIENT_ERROR:
			msg =  "Client error: \"<arg0>\"";
			break;
		case COMPUTE_CMD_INVALID:
			msg =  "Compute cmd not valid: \"<arg0>\"";
			break;
		case COMPILER_UNDEFINED_ERROR:
			msg =  "Compiler: Undefined error (\"<arg0>\")";
			break;
		case CATALOG_NOT_AVAILABLE:
			msg =  "Catalog: Currently not available (error: \"<arg0>\" due to \"<arg1>\") !";
			break;
		case CATALOG_OBJECT_ALREADY_EXISTS:
			msg =  "Catalog: Object \"<arg0>\" of type \"<arg1>\" already exists!";
			break;
		case CATALOG_OBJECT_NOT_EXISTS:
			msg =  "Catalog: Object \"<arg0>\" of type \"<arg1>\" does not exists!";
			break;
		case CATALOG_OBJECT_VALUE_TOO_LONG:
			msg =  "Catalog: Value \"<arg0>\" of catalog object \"<arg1>\" too long!";
			break;
		case CREATE_TABLE_DUPLICATE_ATTRIBUTES:
			msg =  "CREATE TABLE: Duplicate attribute definition for attribute \"<arg0>\" in table  \"<arg1>\"  not allowed ";
			break;
		case CREATE_TABLE_TYPE_NOT_EXISTS:
			msg =  "CREATE TABLE: Type for attribute \"<arg0>\" does not exist.";
			break;
		case STORE_NOT_EXISTS:
			msg =  "Store: Type \"<arg0>\" does not exists!";
			break;
		case STORE_CONNECTION_ERROR:
			msg =  "Store: Connection \"<arg0>\" has an error (error: \"<arg1>\")!";
			break;
		case STORE_EXECUTION_ERROR:
			msg =  "Store: Statement \"<arg0>\" can not be executed (error: \"<arg1>\")!";
			break;
		case COMPILER_GENERIC:
			msg = "Compiler: Error \"<arg0>\"!";
			break;
		case COMPILER_SELECT_EXPRESSION_NOT_SUPPORTED:
			msg = "Compiler: Expression \"<arg0>\" not supported!";
			break;
		case COMPILER_SELECT_ATTRIBUTE_NOT_IN_TABLE:
			msg = "Compiler: Attribute \"<arg0>\" does not exist in the table \"<arg1>\"!";
			break;
		case COMPILER_SELECT_ATTRIBUTE_IN_NO_TABLE:
			msg = "Compiler: Attribute \"<arg0>\" does not exist in any table!";
			break;
		case COMPILER_SELECT_ATTRIBUTE_NOT_UNIQUE:
			msg = "Compiler: Attribute \"<arg0>\" not unique!";
			break;
		case COMPILER_SELECT_DUPLICATE_TABLE:
			msg = "Compiler: Table name \"<arg0>\" specified more than once!";
			break;
		case COMPILER_SELECT_DUPLICATE_ATTRIBUTE:
			msg = "Compiler: Attribute name \"<arg0>\" specified more than once!";
			break;
		case COMPILER_SELECT_ALIAS_MISSING:
			msg = "Compiler: Alias for expression \"<arg0>\" required!";
			break;
		case COMPILER_FUNCTION_OUT_NOT_INITIALISED:
			msg= "Compiler: Output parameter \"<arg0>\" not initialised!";
			break;
		case COMPILER_FUNCTION_VAR_NOT_DECLARED:
			msg= "Compiler: Variable \"<arg0>\" not declared!";
			break;
		case TRACKER_PLAN_INVALID_GENERIC:
			msg = "Tracker: Input plan was invalid (no specific error provided).";
			break;
		}
		
		//replace args
		if(args!=null){
			for(int i=0; i<args.length; ++i){
				msg = msg.replaceAll("<arg"+i+">", args[i]);
			}
		}
		
		return msg;
	}
}