package org.xdb.funsql.compile;

import org.xdb.funsql.compile.operator.*;

public interface TreeVisitor{
	
	void visitEquiJoin(EquiJoin ej);
	void visitGenericSelection(GenericSelection gs);
	void visitFunctionCall(FunctionCall fc);
	void visitSimpleAggregation(SimpleAggregation sa);
	void visitGenericProjection(GenericProjection gp);
	void visitTableOperator(TableOperator to);
}
