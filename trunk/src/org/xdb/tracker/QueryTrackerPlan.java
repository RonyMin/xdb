package org.xdb.tracker;

import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.xdb.Config;
import org.xdb.client.ComputeClient;
import org.xdb.error.Error;
import org.xdb.execute.operators.AbstractExecuteOperator;
import org.xdb.execute.operators.OperatorDesc;
import org.xdb.funsql.compile.FunSQLCompiler;
import org.xdb.tracker.operator.AbstractTrackerOperator;
import org.xdb.utils.Identifier;
import org.xdb.utils.MutableInteger;
import org.xdb.utils.Tuple;

import com.oy.shared.lm.graph.Graph;
import com.oy.shared.lm.graph.GraphFactory;
import com.oy.shared.lm.graph.GraphNode;
import com.oy.shared.lm.out.GRAPHtoDOTtoGIF;

//TODO: Rework wish list in request slots and remove method call to getNodeOperators();
public class QueryTrackerPlan implements Serializable {

	private static final long serialVersionUID = -5521482252707107847L;
	private static final Set<Identifier> EMPTY_OP_SET = new HashSet<Identifier>();

	// last deployment operator id
	private Integer lastDeployOperId = 1;

	// last plan operator id
	private Integer lastTrackerOpId = 1;

	// last plan id
	private static Integer lastPlanId = 1;

	// unique operator id
	private final Identifier planId;

	// assigned query tracker node and compute client
	private QueryTrackerNode tracker = null;
	private ComputeClient computeClient = null;

	// plan info
	private final HashMap<Identifier, AbstractTrackerOperator> operators = new HashMap<Identifier, AbstractTrackerOperator>();
	private final HashMap<Identifier, Set<Identifier>> consumers = new HashMap<Identifier, Set<Identifier>>();
	private final HashMap<Identifier, Set<Identifier>> sources = new HashMap<Identifier, Set<Identifier>>();
	private final HashSet<Identifier> roots = new HashSet<Identifier>();
	private final HashSet<Identifier> leaves = new HashSet<Identifier>();
	private final HashMap<String, MutableInteger> slots = new HashMap<String, MutableInteger>();

	// deployment info
	private HashMap<Identifier, OperatorDesc> currentDeployment = new HashMap<Identifier, OperatorDesc>();

	// last error
	private Error err = new Error();

	// constructor
	public QueryTrackerPlan() {
		this.planId = new Identifier(lastPlanId++);
	}

	// getter and setter
	public Set<Identifier> getLeaves() {
		return Collections.unmodifiableSet(leaves);
	}

	public Map<Identifier, AbstractTrackerOperator> getOperatorMapping() {
		return Collections.unmodifiableMap(operators);
	}
	
	public HashMap<String, MutableInteger> getSlots() {
		return slots;
	}
	
	public HashMap<Identifier, OperatorDesc> getCurrentDeployment() {
		return currentDeployment;
	}

	public Identifier getPlanId() {
		return planId;
	}

	public Collection<AbstractTrackerOperator> getOperators() {
		return operators.values();
	}

	public AbstractTrackerOperator getOperator(Identifier opId) {
		return operators.get(opId);
	}

	public HashSet<Identifier> getRoots() {
		return roots;
	}

	public Error getLastError() {
		return err;
	}

	public void assignTracker(final QueryTrackerNode tracker) {
		this.tracker = tracker;
		computeClient = tracker.getComputeClient();
	}

	public void addOperator(final AbstractTrackerOperator op) {
		Identifier opId = this.planId.clone().append(lastTrackerOpId++);
		op.setOperatorId(opId);
		operators.put(opId, op);

		// add operator as leave and root
		this.leaves.add(opId);
		this.roots.add(opId);
		op.setIsRoot(true);

		this.sources.put(opId, EMPTY_OP_SET);
		this.consumers.put(opId, EMPTY_OP_SET);
	}

	public void setSources(Identifier operId, final Set<Identifier> opSources) {
		this.sources.put(operId, opSources);

		if (!opSources.isEmpty()) {
			leaves.remove(operId);
		}
	}

	public void setConsumers(Identifier operId,
			final Set<Identifier> opConsumers) {
		this.consumers.put(operId, opConsumers);

		if (!opConsumers.isEmpty()) {
			roots.remove(operId);
			AbstractTrackerOperator operator = this.operators.get(operId);
			operator.setIsRoot(false);
		}
	}

	// methods

	/**
	 * Removes result tables of root nodes
	 * 
	 * @param currentDeployment
	 */
	public Error cleanPlan() {
		// close operators which are root operators
		for (final Entry<Identifier, OperatorDesc> entry : currentDeployment
				.entrySet()) {
			final AbstractTrackerOperator planOp = operators
					.get(entry.getKey());
			if (planOp.isRoot()) {
				final OperatorDesc operDesc = entry.getValue();
				err = computeClient.closeOperator(operDesc);

				if (err.isError()) {
					return err;
				}
			}
		}
		return err;
	}

	/**
	 * Closes all operators and ignores errors
	 * 
	 * @return
	 */
	public Error cleanPlanOnError() {
		// close all operators
		for (final Entry<Identifier, OperatorDesc> entry : currentDeployment
				.entrySet()) {
			final OperatorDesc operDesc = entry.getValue();
			computeClient.closeOperator(operDesc);
		}
		return err;
	}

	/**
	 * Executes a plan using a given deployment description
	 * 
	 * @param currentDeployment
	 */
	public Error executePlan() {
		if (err.isError()) {
			return err;
		}

		// start execution on leave operators
		for (final Identifier leaveId : leaves) {
			final OperatorDesc leaveDesc = currentDeployment.get(leaveId);
			err = computeClient.executeOperator(leaveDesc);

			if (err.isError()) {
				return err;
			}
		}

		// close operators which are not root operators
		for (final Entry<Identifier, OperatorDesc> entry : currentDeployment
				.entrySet()) {
			final AbstractTrackerOperator planOp = operators
					.get(entry.getKey());
			if (!planOp.isRoot()) {
				final OperatorDesc operDesc = entry.getValue();
				err = computeClient.closeOperator(operDesc);

				if (err.isError()) {
					return err;
				}
			}
		}

		return err;
	}

	/**
	 * Deploys the given plan and creates a deployment description
	 * 
	 * @return
	 */
	public Error deployPlan() {

		// request compute slots
		requestSlots();
		if (err.isError())
			return this.err;

		// prepare deployment
		prepareDeployment();
		if (err.isError())
			return this.err;

		// distribute plan to compute nodes
		distributePlan();
		if (err.isError()) {
			for (final OperatorDesc deployOperDesc : currentDeployment.values()) {
				computeClient.closeOperator(deployOperDesc);
			}
			currentDeployment.clear();

			return err;
		}

		return this.err;
	}

	/**
	 * Requests computation slots from master tracker
	 */
	private void requestSlots() {
		final ResourceScheduler resourceScheduler = new ResourceScheduler(this);
		final MutableInteger numSlots = new MutableInteger(
				resourceScheduler.calcMaxParallelization());
		final Map<String, MutableInteger> requiredSlots = new HashMap<String, MutableInteger>();

		if (numSlots.intValue() > 0) {
			requiredSlots.put(ResourceScheduler.RANDOM, numSlots);
		}

		final Tuple<Map<String, MutableInteger>, Error> tuple = tracker
				.requestComputeSlots(requiredSlots);

		final Map<String, MutableInteger> allocatedSlots = tuple.getObject1();
		err = tuple.getObject2();
		slots.putAll(allocatedSlots);
	}
	
	/**
	 * Prepare deployment of plan by assigning operators
	 * to compute nodes 
	 */
	private void prepareDeployment(){
		for (final Identifier leave : leaves) {
			prepareDeployment(leave);
			if (err.isError())
				return;
		}
	}
	


	/**
	 * Prepares deployment for a given operator in plan
	 * 
	 * @param operId
	 * @param currentDeployment
	 */
	private void prepareDeployment(final Identifier operId) {

		// operator already deployed
		if (currentDeployment.containsKey(operId)) {
			return;
		}

		// identify best used node
		String usedNode = null;

		// TODO: Get next best ComputeNode
		int maxSlots = 0;
		for (final Entry<String, MutableInteger> availableNode : slots
				.entrySet()) {
			if (maxSlots < availableNode.getValue().intValue()) {
				usedNode = availableNode.getKey();
				maxSlots = availableNode.getValue().intValue();
			}
		}
		final MutableInteger numOfFreeNodes = slots.get(usedNode);
		numOfFreeNodes.dec();

		// generate deployment description from plan operator
		final Identifier deployOperId = operId.clone();
		deployOperId.append(lastDeployOperId++);
		final OperatorDesc deployOperDesc = new OperatorDesc(deployOperId,
				usedNode);

		// add to current deployment description
		currentDeployment.put(operId, deployOperDesc);

		// prepare deployment of consumers
		for (final Identifier consumerId : consumers.get(operId)) {
			prepareDeployment(consumerId);
		}
	}


	/**
	 * Distributes plan using a given deployment description
	 * 
	 * @param currentDeployment
	 */
	private void distributePlan() {
		for (final Entry<Identifier, OperatorDesc> entry : currentDeployment
				.entrySet()) {
			final Identifier operId = entry.getKey();
			final OperatorDesc deployOperDesc = entry.getValue();
			final AbstractTrackerOperator oper = operators.get(operId);

			// create executable operator and set consumers / sources
			final AbstractExecuteOperator deployOper = oper.genDeployOperator(
					deployOperDesc, currentDeployment);

			for (final Identifier consumerId : consumers.get(operId)) {
				final OperatorDesc consumerDesc = currentDeployment
						.get(consumerId);
				deployOper.addConsumer(consumerDesc);
			}

			for (final Identifier sourceId : sources.get(operId)) {
				final OperatorDesc sourceDesc = currentDeployment.get(sourceId);
				deployOper.addSource(sourceDesc);
			}

			// deploy operator
			err = computeClient.openOperator(deployOperDesc.getOperatorNode(),
					deployOper);
			if (err.isError())
				return;
		}
	}

	/**
	 * Generates a visual graph representation of the compile plan
	 */
	public Error traceGraph(String fileName) {
		fileName += planId;
		final Error error = new Error();
		final Graph graph = GraphFactory.newGraph();

		final HashMap<Identifier, GraphNode> nodes = new HashMap<Identifier, GraphNode>();

		// add nodes to plan
		for (Identifier opId : this.operators.keySet()) {
			GraphNode node = graph.addNode();
			AbstractTrackerOperator op = this.operators.get(opId);
			node.getInfo().setCaption(op.toString());
			node.getInfo().setHeader(op.getOutTables().toString());
			node.getInfo().setFooter(op.getInTables().toString());
			nodes.put(opId, node);
		}

		// add edges to plan
		for (Map.Entry<Identifier, Set<Identifier>> entry : this.sources
				.entrySet()) {
			Identifier fromId = entry.getKey();
			GraphNode from = nodes.get(fromId);

			for (Identifier toId : entry.getValue()) {
				GraphNode to = nodes.get(toId);
				graph.addEdge(from, to);
			}
		}

		// output graph to *.gif
		final String path = Config.DOT_TRACE_PATH;
		final String dotFileName = path + fileName + ".dot";
		final String gifFileName = path + fileName + ".gif";
		final String exeFileName = Config.DOT_EXE;
		try {
			GRAPHtoDOTtoGIF.transform(graph, dotFileName, gifFileName,
					exeFileName);
		} catch (final IOException e) {
			return FunSQLCompiler.createGenericCompileErr(e.getMessage());
		}
		return error;
	}
}
