package thmp;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.List;

import thmp.DetectHypothesis.DefinitionListWithThm;

/**
 * An instance of ParsedExpression represents a final parsed theorem.
 * Containing its top-scoring spanning parse trees (if any), 
 * the commands satisfied and built.
 * To be serialized to persistent storage used later
 * for search, etc, so don't need to re-parse everything at every 
 * initialization.
 * @author yihed
 *
 */
public class ParsedExpression implements Serializable{

	private static final long serialVersionUID = -6334467107326376015L;

	private String originalThmStr;
	
	private List<Struct> parseRootList;
	
	//contains definitions needed for this thm, list of variable assignments
	//made outside the theorem, and modified thm string with definitions prepended.
	private DefinitionListWithThm defListWithThm;
	
	//the highest-ranked ParseStruct, combining the different
	//WLCommands fulfilled into one tree.
	private ParseStruct headParseStruct;
	
	//relational vector, @see RelationVec.java.
	private BigInteger relationVec;
	
	//context vector, @see ContextVec.java
	private transient int[] contextVec;
	//need the String form for serialization
	private String contextVecStr;
	
	/*public ParsedExpression(String thmStr, List<Struct> parseRootList){
		this.originalThmStr = thmStr;
		this.parseRootList = parseRootList;
	}*/
	
	public ParsedExpression(String thmStr, ParseStruct headParseStruct,
			DefinitionListWithThm defListWithThm, int[] contextVec, 
			BigInteger relationVec){
		this.originalThmStr = thmStr;
		this.headParseStruct = headParseStruct;
		this.defListWithThm = defListWithThm;
		this.relationVec = relationVec;
		this.contextVec = contextVec;
		this.contextVecStr = GenerateContextVector.contextVecIntArrayToString(contextVec);
		//System.out.println("contextVecStr during ParsedExpression construction! " + contextVecStr);
	}
	
	/**
	 * @return the relationVec
	 */
	public BigInteger getRelationVec() {
		return relationVec;
	}

	/**
	 * @return the contextVec
	 */
	public int[] getContextVec() {
		return contextVec;
	}

	/**
	 * @return the String representation of contextVec 
	 */
	public String contextVecStr() {
		return contextVecStr;
	}
	
	/**
	 * @return the originalThmStr
	 */
	public String getOriginalThmStr() {
		return originalThmStr;
	}

	/**
	 * @return the parseRootList
	 */
	public List<Struct> getParseRootList() {
		return parseRootList;
	}

	/**
	 * @return the headParseStruct
	 */
	public ParseStruct getHeadParseStruct() {
		return headParseStruct;
	}	
	
	public DefinitionListWithThm getDefListWithThm(){
		return this.defListWithThm;
	}
	
	@Override
	public String toString(){
		StringBuilder sb = new StringBuilder(100);
		sb.append("\ndefListWithThm: ").append(defListWithThm);
		sb.append("\n\nheadParseStruct: ").append(headParseStruct);
		return sb.toString();
	}
}
