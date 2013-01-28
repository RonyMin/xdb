package org.xdb.funsql.compile.operator;


import java.util.Collection;
import java.util.HashMap;
import java.util.Vector;
import org.xdb.error.Error;
import org.xdb.funsql.compile.tokens.TokenAttribute;
import org.xdb.funsql.compile.tokens.TokenIdentifier;
import org.xdb.utils.Identifier;
import org.xdb.utils.SetUtils;
import org.xdb.utils.StringTemplate;
import org.xdb.utils.TokenPair;

import com.oy.shared.lm.graph.Graph;
import com.oy.shared.lm.graph.GraphNode;

/**
 * Operator that represents a n-way join without nesting sub queries
 * @author a.mueller
 *
 */
public class SQLJoin extends AbstractJoinOperator {

	
	
	private static final long serialVersionUID = -1594109032489961614L;


	/**
	 * Create a sql Join based on a equi Join and replace it
	 * @param equiJoin
	 */
	public SQLJoin(EquiJoin equiJoin) {
		super(equiJoin);
		setType(EnumOperator.SQL_JOIN);
	
		TokenPair tp = new TokenPair(equiJoin.getLeftTokenAttribute(), equiJoin.getRightTokenAttribute());
		tp.setLeftTableName(equiJoin.getLeftChild().getOperatorId().toString());
		tp.setRightTableName(equiJoin.getRightChild().getOperatorId().toString());
		jointokens.add(tp);
		
		setOperatorId(equiJoin.operatorId);
		//redirect parents from equi Join to sql Join
		//TODO problem with more than one parent
		for(AbstractCompileOperator parentOp : this.parents) {
			int childOpIdx = parentOp.getChildren().indexOf(equiJoin);
			parentOp.setChild(childOpIdx, this);
		}
		/*AbstractCompileOperator parentOp =  this.parents.get(0);
		int childOpIdx = parentOp.getChildren().indexOf(equiJoin);
		parentOp.setChild(childOpIdx, this);*/
		
		// redirect the children 
		int parentOpIdx;
		for (AbstractCompileOperator child : this.getChildren()) {
			parentOpIdx = child.getParents().indexOf(equiJoin);
			child.setParent(parentOpIdx, this);
		}
	}
	
	/**
	 * Merge Equi Join into an existing SQL Join
	 * @param equiJoin
	 */
	public void mergeChildJoinOp(EquiJoin equiJoin){

		//steps that need to be done		
		//add pair of ne join to the vector
		TokenPair newJoinToken = new TokenPair(equiJoin.getLeftTokenAttribute(), equiJoin.getRightTokenAttribute());
		// because the old equijoin op is not existing anymore rebuild 
		// the tokenpair where it occures in
		newJoinToken.setLeftTableName(equiJoin.getLeftChild().getOperatorId().toString());
		newJoinToken.setRightTableName(equiJoin.getRightChild().getOperatorId().toString());
		
		TokenIdentifier newTableIdent; 
		for (TokenPair jointoken : jointokens) {
				// when the same do not change anything
					if(jointoken.getLeftTokenAttribute().getTable().getName().toString().equals(equiJoin.getOperatorId().toString())){
						Identifier ident = searchSourceTableForJoinColumn(jointoken.getLeftTokenAttribute(), equiJoin);
						newTableIdent = new TokenIdentifier(ident.toString());

						jointoken.getLeftTokenAttribute().setTable(newTableIdent);
					
						jointoken.setLeftTableName(newTableIdent.toString());
					}		
					if(jointoken.getRightTokenAttribute().getTable().getName().toString().equals(equiJoin.getOperatorId().toString())){
						//change Table to new Name
						Identifier ident = searchSourceTableForJoinColumn(jointoken.getRightTokenAttribute(), equiJoin);
						newTableIdent = new TokenIdentifier(ident.toString());
	
						jointoken.getRightTokenAttribute().setTable(newTableIdent);
						jointoken.setRightTableName(newTableIdent.toString());
					}
		}
		
		
		// add new JoinToken to the List
		jointokens.add(newJoinToken);
		
		
		//redirect Children from merged Equi Join
		int oldindex = this.children.indexOf(equiJoin);
		if(!this.children.contains(equiJoin.getLeftChild())){
			this.children.set(oldindex, equiJoin.getLeftChild());
		}
		//set the childrens parents to sql join op
		Vector<AbstractCompileOperator> parents = equiJoin.getLeftChild().getParents();
	
		parents.set(parents.indexOf(equiJoin), this);
		if(!this.children.contains(equiJoin.getRightChild())){
			this.addChildren(equiJoin.getRightChild());
		}
		
		parents = equiJoin.getRightChild().getParents();
		parents.set(parents.indexOf(equiJoin), this);

	}
	
	
	
	public Vector<TokenPair> getJointokens() {
		return jointokens;
	}

	/**
	 * Method that return the source table for a join attribute
	 * @param token 
	 * @param op
	 */
	public Identifier searchSourceTableForJoinColumn(TokenAttribute token, AbstractCompileOperator op){
		//iterate overall children to find the base/source table for the column name
		for(AbstractCompileOperator child: op.getChildren()){
			
			if(child.getResultAttributes().contains(token.getName().toString())){
				return child.getOperatorId();
			}

		}
		return null;
	}
	
	
	@Override
	public Error traceOperator(Graph g, HashMap<Identifier, GraphNode> nodes){
		Error err = super.traceOperator(g, nodes);
		if(err.isError())
			return err;
		
		GraphNode node = nodes.get(this.operatorId);
		String  joinString ="";
		
		for(TokenPair tp : this.jointokens){
			joinString = tp.getLeftTokenAttribute().toString()+"="+tp.getRightTokenAttribute().toString() +"; " + joinString;
		}
		node.getInfo().setFooter(joinString);
		return err;
	}
	
	
	@Override
	public String toSqlString() {

		HashMap<String, String> vars = new HashMap<String, String>();

		String results ="";
		for (AbstractCompileOperator child : this.getChildren()) {
			results = SetUtils.buildAliasString(child.getResultTableAttributes(),child.getResultAttributes())+"," + results;
		}
		results = results.substring(0, results.lastIndexOf(','));

	
	
		// build up HashMap with table name and join attributes
		HashMap<String,String> joinParams= new HashMap<String, String>();
		
		
		//build two vectors with join params and table name
		Vector<TokenAttribute> joinattributes =  new Vector<TokenAttribute>();
		for (TokenPair tokenPair : this.getJointokens()) {
			joinattributes.add(tokenPair.getLeftTokenAttribute());
			joinattributes.add(tokenPair.getRightTokenAttribute());
			joinParams.put(tokenPair.getLeftTableName(), tokenPair.getLeftTokenAttribute().toString());
			joinParams.put(tokenPair.getRightTableName(), tokenPair.getRightTokenAttribute().toString());
			
		}

		String templateString = "";
		vars.put("RESULT", results);
		//put join params into map
		int idx = 0;
		for (TokenPair tokenPair : this.getJointokens()) {
			if( idx > 0 ){
				templateString =  templateString +" INNER JOIN <<OP"+(idx)+">> AS <OP"+(idx)+"> ON <JATT"+(idx)+"> = <JATT"+(idx+1)+">";
			} else {
				templateString = "SELECT <RESULT> FROM <<OP0>> AS <OP0>  INNER JOIN <<OP1>> AS <OP1> ON <JATT0> = <JATT1>";
			}
			vars.put("OP"+idx, tokenPair.getLeftTableName());
			vars.put("JATT"+idx, tokenPair.getLeftTokenAttribute().toString() );
			idx++;
			vars.put("OP"+idx, tokenPair.getRightTableName());
			vars.put("JATT"+idx, tokenPair.getRightTokenAttribute().toString()  );	
			idx++;
		}


		 StringTemplate sqlTemplate = new StringTemplate(templateString);
	
		return sqlTemplate.toString(vars);
		
	}

	@Override
	public void renameAttributes(String oldChildId, String newChildId) {
		Vector<TokenAttribute> atts = new Vector<TokenAttribute>();
		for (TokenPair tp :this.jointokens) {
			atts.add(tp.getLeftTokenAttribute());
			atts.add(tp.getRightTokenAttribute());
		}
		TokenAttribute.renameTable(atts, oldChildId, newChildId);

	}

	@Override
	public boolean isLeaf() {
		return (this.getChildren().size() == 0);
	}

	@Override
	public void renameForPushDown(Collection<TokenAttribute> selAtts, int childIdx) {
		TokenAttribute.renameTable(selAtts, this.getChild(childIdx).getOperatorId().toString());
	}
}