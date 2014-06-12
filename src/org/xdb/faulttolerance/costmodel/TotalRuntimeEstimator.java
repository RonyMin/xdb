package org.xdb.faulttolerance.costmodel;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.xdb.Config;
import org.xdb.utils.Identifier;

public class TotalRuntimeEstimator { 

	// list of materialization configuration 
	private List<MaterializedPlan> matPlansList = new ArrayList<MaterializedPlan>(); 
    
	// 
	private double totalRunTime; 
	
	// 
	private double runTime; 

	public TotalRuntimeEstimator(List<MaterializedPlan> matPlansList) { 
		this.matPlansList = matPlansList; 

	}

	/**
	 * @return the matPlansList
	 */
	public List<MaterializedPlan> getMatPlansList() {
		return matPlansList;
	}

	/**
	 * @param matPlansList the matPlansList to set
	 */
	public void setMatPlansList(List<MaterializedPlan> matPlansList) {
		this.matPlansList = matPlansList;
	}

	// For each materialization configuration, calcualte the 
	// the average wasted time
	public void calculateAverageWastedTimePerMatConf() {

		int numberOfLevelslnMatConf = 0;  
		List<Level> matLevels = new ArrayList<Level>();
		for (MaterializedPlan materializedPlan : matPlansList) { 
			double averageWastedTime = 0.0; 
			double runTimeWithoutMaterialization = 0.0; 
			double materializationTime = 0.0; 
			matLevels = materializedPlan.getmateriliazedPlanLevels(); 
			for (Level level : matLevels) {
				runTimeWithoutMaterialization += level.getLevelRuntimeEstimate(); 
				materializationTime += level.getMaterializationRuntimeestimate();
				level.setAverageWastedTime(0.5*(level.getLevelRuntimeEstimate() + level.getMaterializationRuntimeestimate())*level.getLevelFailureProbability());
			}   
			
			runTimeWithoutMaterialization += matLevels.get(matLevels.size()-1).getMaterializationRuntimeestimate();
			//materializedPlan.setRunTimeWithoutFailure(runTimeWithoutMaterialization + materializationTime);
			materializedPlan.setMaterializationTime(materializationTime); 
			numberOfLevelslnMatConf = matLevels.size();  
		
			for(int i=0; i<numberOfLevelslnMatConf; i++){   
				averageWastedTime += 0.5*(matLevels.get(i).getLevelRuntimeEstimate() + matLevels.get(i).getMaterializationRuntimeestimate())*
						matLevels.get(i).getLevelFailureProbability();	
			} 
			materializedPlan.setAverageWastedTime(averageWastedTime);
		}
	} 

	public void calculateRuntTimeForMatConfs(){
     
		double runTime = 0.0;
		double levelRunTime = 0.0;
		double T;
		double W;
		double F;
		long r; 
		long totalR = 0; 
		double totalWastedTime = 0.0; 
		double totalRunTimeWF = 0.0;
		for (MaterializedPlan materializedPlan : matPlansList) {  
			// For each mat conf we will apply the cost model level by level 
			List<Level> levels = materializedPlan.getmateriliazedPlanLevels(); 
			System.out.print("Mat Conf -> ");
			totalR = 0;
			totalWastedTime = 0.0; 
			runTime = 0.0; 
			totalRunTimeWF = 0.0;
			for (Level level : levels) {
				T = level.getLevelRuntimeEstimate() + level.getMaterializationRuntimeestimate();  
				W = level.getAverageWastedTime(); 
				F = level.getLevelFailureProbability(); 
				r = level.getNumberOfAttemptsPerLevel(); 
				levelRunTime = T + (W*((1 - Math.pow(F,r+1))/(1-F) - W)) + r*Config.DOOMDB_MTTR;   
				runTime += levelRunTime; 
				totalR += r; 
				totalWastedTime += W; 
				totalRunTimeWF += T;
				System.out.print(level.getSubQquery().get(level.getSubQquery().size()-1).getId());  
				System.out.print(", ");
			}
			System.out.println();
			System.out.println("Total RunTime:"+runTime+" Runtime="+materializedPlan.getRunTimeWithoutFailure() + ", Avg.Wasted Time="+totalWastedTime+", Attempts="+totalR);
            
			materializedPlan.setRunTime(runTime);	 
		} 
	} 
	
	public List<Identifier> getTheRecommendedMaterializationOpsId (){
		Collections.sort(matPlansList);  
		MaterializedPlan materializedPlan = matPlansList.get(0); 
		List<Level> levels = materializedPlan.getmateriliazedPlanLevels(); 
	    System.out.println("Operators should be materialized: ");
	    List<Identifier> materializedOpIds = new ArrayList<Identifier>();
		for (Level level : levels) { 
			materializedOpIds.add(level.getSubQquery().get(level.getSubQquery().size()-1).getId());
			System.out.println("Op: "+level.getSubQquery().get(level.getSubQquery().size()-1).getId());
		} 
        return materializedOpIds;
	}

	/**
	 * @return the totalRunTime
	 */
	public double getTotalRunTime() {
		return totalRunTime;
	}

	/**
	 * @param totalRunTime the totalRunTime to set
	 */
	public void setTotalRunTime(double totalRunTime) {
		this.totalRunTime = totalRunTime;
	}

	/**
	 * @return the runTime
	 */
	public double getRunTime() {
		return runTime;
	}

	/**
	 * @param runTime the runTime to set
	 */
	public void setRunTime(double runTime) {
		this.runTime = runTime;
	}
}