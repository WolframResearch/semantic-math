package thmp.parse;

import java.util.List;

/**
 * List of MatrixPathNode's corresponding to a matrix (mx) entry.
 * Struct on the mxEntry.
 * 
 * Don't need this! Can make reference to the corresponding list of MatrixPathNode's inside Struct
 * 
 * @author yihed
 * @deprecated
 */
public class MatrixPathNodeList {

	//scoreSoFar is set during DFS
	private List<Struct> structList;
	
	
	public void add(Struct newStruct){
		this.structList.add(newStruct);
	}
}
