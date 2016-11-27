package thmp;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * List of Struct's to be filled inside a matrix entry.
 * 
 * The scoreSoFar in MatrixPathNodes corresponding to the same Struct
 * could be different, because the paths above this MatrixPathNodes could differ.
 * 
 * @author yihed
 *
 */
public class StructList implements Serializable{
	
	private static final long serialVersionUID = 1L;

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
	 * of pointers to Struct's.
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
		//System.out.println(Arrays.toString(Thread.currentThread().getStackTrace()));
		this.structList.add(newStruct);
	}
	
	public String toString(){
		return structList.toString();
	}
}
