package thmp;

import java.util.ArrayList;
import java.util.List;

/**
 * List of Struct's to be filled inside a matrix entry
 * 
 * The scoreSoFar in MatrixPathNodes corresponding to the same Struct
 * could be different, because the paths above this MatrixPathNodes could differ.
 * 
 * @author yihed
 *
 */
public class StructList {
	
	//index in StructList of highest scores gathered from *downstream*,
	//in particular the highest among all Struct's, and each path along 
	//the different MatrixPathNode's
	private int highestDownScoreIndex;
	
	private List<Struct> structList;	
	
	public StructList(){
		this.structList = new ArrayList<Struct>();
		this.highestDownScoreIndex = -1;
	}
	
	public List<Struct> structList(){
		return this.structList;
	}	
	
	public int size(){
		return structList.size();
	}
	
	public int highestDownScoreIndex(){
		return this.highestDownScoreIndex;
	}

	public void set_highestDownScoreIndex(int index){
		this.highestDownScoreIndex = index;
	}
	
	public void add(Struct newStruct){
		this.structList.add(newStruct);
	}
}
