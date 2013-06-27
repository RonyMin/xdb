package org.xdb.tracker.scheduler;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.xdb.Config;
import org.xdb.execute.ComputeNodeDesc;
import org.xdb.tracker.QueryTrackerPlan;
import org.xdb.utils.Identifier;

/**
 * Abstract class for resource scheduling
 * 
 * @author cbinnig
 * 
 */
public abstract class AbstractResourceScheduler {
	public static final String RANDOM_COMPUTE_NODE = "R";

	protected final QueryTrackerPlan plan;
	protected EnumResourceScheduler type;
	private static EnumResourceScheduler usedScheduler = Config.QUERYTRACKER_SCHEDULER;

	// assigned slots which are actually available: compute slot URL -> compute node
	protected Map<String, ComputeNodeDesc> assignedComputeNodes = new HashMap<String, ComputeNodeDesc>();

	// constructor
	public AbstractResourceScheduler(final QueryTrackerPlan plan) {
		this.plan = plan;
	}

	/**
	 * Creates scheduler for given query tracker plan plan
	 * 
	 * @param plan
	 * @return
	 */
	public static AbstractResourceScheduler createScheduler(
			final QueryTrackerPlan plan) {
		switch (usedScheduler) {
		case SIMPLE:
			return new SimpleResourceScheduler(plan);
		case WISHLIST_AWARE:
			return new WishlistAwareScheduler(plan);

		}
		return new SimpleResourceScheduler(plan);
	}

	/**
	 * Changes the used scheduler
	 */
	public static void changeScheduler(EnumResourceScheduler newScheduler) {
		usedScheduler = newScheduler;
	}
	
	/**
	 * Returns the number of alternative connections where a operator can be eecuted
	 * @param opId
	 * @return
	 */
	public int getNumberOfConnections(Identifier opId){
		return 1;
	}

	/**
	 * Creates wish-list of compute slots to execute plan
	 * 
	 * @return
	 */
	public abstract Set<String> createComputeNodesWishList();

	/**
	 * Assigns available compute slots to operators of plan
	 * 
	 * @param slots
	 */
	public void assignComputeNodes(Map<String, ComputeNodeDesc> nodes) {
		this.assignedComputeNodes.putAll(nodes);
	} 
	
	public void clearAssignedComputeNodes(){
		this.assignedComputeNodes.clear();
	}

	/**
	 * Returns assigned compute slot after slots have been assigned
	 * 
	 * @param operId
	 * @return
	 */
	public abstract ComputeNodeDesc getComputeNode(final Identifier operId);
	
	/**
	 * Returns assigned compute slot after slots have been assigned.
	 * If multiple alternatives are available alternative given by connectionNumber.
	 * 
	 * @param operId
	 * @param connectionNumber
	 * @return
	 */
	public ComputeNodeDesc getComputeNode(final Identifier operId, int connectionNumber){
		return this.getComputeNode(operId);
	}
}