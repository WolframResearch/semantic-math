package thmp;

import java.util.Map;

import com.google.common.collect.Multimap;

import thmp.ThmP1.ParsedPair;

/**
 * Relational vectors used for search. Similar
 * to context vectors, but emphasizes the few
 * key relations, e.g. "A is", "is A", "if A",
 * "exists A",
 * Each relation occupies one segment of the total 
 * relation vec, so 5 relations -> 5 * total number of 
 * terms (rows) in term-document matrix. So must be judicious
 * when adding new relations. 
 * Vector is represented as a BitSet.
 * **What about other verbs and actions, e.g. 
 * "f maps X to Y", "A acts on B".
 * 
 * @author yihed
 *
 */
public class RelationVec {

	/**
	 * Enum for the different types of
	 * relations, such as "is A", "A is",
	 * along with their offset f.
	 */
	public static enum RelationType{
		
		//_IS: "A is", IS_: "is A".  
		_IS(0), IS_(1), IF(2), EXIST(3), NONE(-1);
		
		//offset for how many times the total number of terms
		//in term-document matrix the segment for this type starts.
		private int vectorOffset;
		
		private RelationType(int offset){
			this.vectorOffset = offset;			
		}
		
		/**
		 * @return offset for how many times the total number of terms
		 * in term-document matrix the segment for this type starts.
		 */
		public int vectorOffset(){
			return this.vectorOffset;
		}
		
		//need to get relation 
		
		//get the relation from the different built WLCommand's. not this way.
		//public static RelationType findRelationType(){
			
		//}
		
	}
	
	/**
	 * Builds relation vec from ParsedPair's in parsedPairMMap, 
	 * @param parsedPairMMap
	 */
	public static void getA(Multimap<ParseStructType, ParsedPair> parsedPairMMap){
		
		for(Map.Entry<ParseStructType, ParsedPair> pairEntry : parsedPairMMap.entries()){
			ParseStructType parseStructType = pairEntry.getKey();
			ParsedPair parsedPair = pairEntry.getValue();
			WLCommand wlCommand = parsedPair.getWlCommand();
			
		}
		
		//getTermStrIndex()
		
	}
	
	
	public static void main(String[] args){
		//parse and produce vecs.
		
		ParseTreeToVec.getTermStrIndex("");
		
	}
}
