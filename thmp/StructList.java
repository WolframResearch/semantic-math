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
	
	/**
	 * Shallow copy of this.structList, as structList is a list
	 * of pointers to Struct's, so even deep copy would only be
	 * copying pointers and not Struct's being pointed to.
	 * @return
	 */
	public StructList copy(){
		StructList newStructlist = new StructList();
		//newStructlist.structList.addAll(this.structList);
		newStructlist.structList = this.structList;
		newStructlist.highestDownScoreIndex = this.highestDownScoreIndex;
		return newStructlist;
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
