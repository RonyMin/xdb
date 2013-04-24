package org.xdb.tools.partitioner;

import java.io.File;

public class Utils {

	/** 
	 * Find the file folder path 
	 * 
	 * @param file the file path
	 * @return the folder path in which the file resides 
	 */
	public static String getFileDirectory(String file){
		
		File fileObject = new File(file);
		File parentDir = fileObject.getParentFile(); 
		String parentDirName = parentDir.getPath(); 
		return parentDirName;
		
	} 
	
	/** 
	 * Find the file name without extension and location  
	 * @param file the file path 
	 * @return the file name without extension and location
	 */
	public static String getFileName(String file){
	    File fileObj = new File(file);  
	    String fileName = fileObj.getName();
	    
	    int position = fileName.lastIndexOf("."); 
	    if(position == -1)
	        return fileName; 
	    else  
	    	return fileName.substring(0, position);
	} 
	
	/**
	 * Find the file extension, if it is null, then return "dat" as default 
	 * @param file
	 * @return the file's extension (type)
	 */
	public static String getFileType(String file){
		String fileType = "dat"; 
		// Extract the file extension. if there is no extension (dat) as 
		// default file extension value will be used. 
		int dotPosition = file.lastIndexOf('.'); 
		if(dotPosition != -1){
			fileType = file.substring(dotPosition+1);
		} 
        
		return fileType;
	}
	
	/** 
	 * Delete old files (partitions). 
	 * 
	 * @param fileName the name of the file to delete its partitions
	 */
	public static void deleteOldPartitions(String fileName) {

		String directory = Utils.getFileDirectory(fileName); // Get the directory name
		// Check if the directory is existing
		File dir = new File(directory); 
		if (!dir.exists()) {
			System.out.println( directory + " does not exist");
			return;
		} 
        
		// Search the files with specified name patter (the file name + "p").
		String[] info = dir.list();
		for (int i = 0; i < info.length; i++) {
			File file = new File(directory + File.separator + info[i]);
			if (!file.isFile()) { 
				continue;
			}  
			if (info[i].indexOf(Utils.getFileName(fileName)+"_p") == -1) { // name doesn't match
				continue;
			} 

			if (!file.delete())
				System.err.println("Couldn't remove " + file.getPath()); 
			else 
				System.out.println("removing " + file.getPath());
       }
	} 
	
	/**
	 * Calculate the hash code through a set of columns' values 
	 * @param line the record
	 * @param keys the indices of the columns  
	 * @return the hash code of the concatenated values of the columns given by keys array.  
	 */
	public static int calculateHash (String line, String [] keys) {
		
		int hash =0; 
		String[] lineTokens = line.split("\\|"); 
	    
		String keysString = "";
		for(int i=0; i < keys.length; i++){
			keysString += lineTokens[i].trim();
		} 
		hash = Math.abs(keysString.hashCode()); // return always the positive value of the hash. 
        return hash; 
	}
	
	/**
	 * @param keysString
	 * @return
	 */
	public static String[] getKeyIndeicesFromString (String keysString){
		
		String keyIndices[]; 
		if(keysString.length() > 1){
			keyIndices = keysString.split("\\,"); // if we have more than one key (index) the format is 0,1,2... 
		} else {  
			keyIndices = new String []{keysString}; // if we only have one index. 
		}  
		return keyIndices;
	}
}