package org.xdb.funsql.compile.operator;

import java.util.HashMap;
import java.util.List;
import java.util.Vector;

import org.xdb.error.Error;
import org.xdb.funsql.compile.analyze.operator.ITreeVisitor;
import org.xdb.funsql.compile.expression.AbstractExpression;
import org.xdb.funsql.compile.tokens.AbstractToken;
import org.xdb.funsql.compile.tokens.TokenIdentifier;
import org.xdb.utils.Identifier;
import org.xdb.utils.SetUtils;
import org.xdb.utils.StringTemplate;

import com.oy.shared.lm.graph.Graph;
import com.oy.shared.lm.graph.GraphNode;

public class GenericAggregation extends AbstractUnaryOperator {

	private static final long serialVersionUID = 3800517017256774443L;
	
	private Vector<AbstractExpression> groupExprs;
	private Vector<AbstractExpression> aggExprs;
	private Vector<TokenIdentifier> aliases;
	
	private final StringTemplate sqlTemplate = 
			new StringTemplate("SELECT <RESULT> FROM <<OP1>> AS <OP1>"+
					"GROUP BY <GROUP_ATTRS>");
	
	//constructors
	public GenericAggregation(AbstractOperator child) {
		super(child);
		
		this.groupExprs = new Vector<AbstractExpression>();
		this.aggExprs = new Vector<AbstractExpression>();
		this.aliases = new Vector<TokenIdentifier>();
		
		this.type = EnumOperator.GENERIC_AGGREGATION;
	}
	
	//getters and setters
	public void addAlias(TokenIdentifier alias) {
		aliases.add(alias);
	}

	public TokenIdentifier getAlias(int i) {
		return aliases.get(i);
	}

	public Vector<TokenIdentifier> getAliases() {
		return aliases;
	}
	
	public void addGroupExpression(AbstractExpression expr){
		this.groupExprs.add(expr);
	}
	
	public AbstractExpression getGroupExpression(int i){
		return this.groupExprs.get(i);
	}

	public Vector<AbstractExpression> getGroupExpressions() {
		return groupExprs;
	}
	
	public void addAggregationExpression(AbstractExpression expr){
		this.aggExprs.add(expr);
	}
	
	public AbstractExpression getAggregationExpression(int i){
		return this.groupExprs.get(i);
	}

	public Vector<AbstractExpression> getAggregationExpressions() {
		return this.aggExprs;
	}
	
	@Override
	public String toSqlString() {
		final HashMap<String, String>  vars = new HashMap<String, String>();
		final String opDummy = getChild().getOperatorId().toString();
		vars.put("OP1", opDummy);
		vars.put("AGG_ATTRS", SetUtils.stringifyExprVec(aggExprs));
		vars.put("GROUP_ATTRS", SetUtils.stringifyExprVec(groupExprs));
		
		final Vector<String> aliasVec = new Vector<String>(aliases.size());
		for(TokenIdentifier tok : aliases) {
			aliasVec.add(tok.getName());
		}
		final List<String> aggrAliases = aliasVec.subList(0, aggExprs.size()-1);
		final List<String> grpAliases = aliasVec.subList(aggExprs.size()-1, aliasVec.size()-1);
		
		final Vector<String> aggrExprVec = new Vector<String>(aggExprs.size());
		for(AbstractExpression exp : aggExprs) {
			aggrExprVec.add(exp.toString());
		}
		final Vector<String> groupExprVec = new Vector<String>(groupExprs.size());
		for(AbstractExpression exp : groupExprs) {
			groupExprVec.add(exp.toString());
		}
		
		vars.put("RESULT", SetUtils.buildAliasString(opDummy, aggrExprVec, aggrAliases)+","+SetUtils.buildAliasString(opDummy, groupExprVec, grpAliases));
		
		return sqlTemplate.toString(vars);
	}

	@Override
	public boolean isPushDownAllowed(EnumPushDown pd) {
		switch(pd){
		case SELECTION_PUSHDOWN:
		case STOP_PUSHDOWN:
			return true;
		default:
			return false;
		}
	}

	@Override
	public Error traceGraph(Graph g, HashMap<Identifier, GraphNode> nodes) {
		Error err = super.traceGraph(g, nodes);
		if (err.isError())
			return err;

		GraphNode node = nodes.get(this.operatorId);
		StringBuffer footer = new StringBuffer();
		footer.append("Aggregation: ");
		footer.append(this.aggExprs.toString());
		footer.append(AbstractToken.NEWLINE);
		footer.append("Grouping: ");
		footer.append(this.groupExprs.toString());
		footer.append(AbstractToken.NEWLINE);
		footer.append("Aliases: ");
		footer.append(this.aliases.toString());
		node.getInfo().setFooter(footer.toString());
		return err;
	}

	@Override
	public void accept(ITreeVisitor v) {
		v.visitGenericAggregation(this);
	}
}