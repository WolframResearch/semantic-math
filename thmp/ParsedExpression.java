package thmp;

import java.io.Serializable;
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

	private static final long serialVersionUID = 1L;
	
	private String originalThmStr;
	
	private List<Struct> parseRootList;
	
	//contains definitions needed for this thm, list of variable assignments
	//made outside the theorem, and modified thm string with definitions prepended.
	private DefinitionListWithThm defListWithThm;
	
	//the highest-ranked ParseStruct, combining the different
	//WLCommands fulfilled into one tree.
	private ParseStruct headParseStruct;
	
	public ParsedExpression(String thmStr, List<Struct> parseRootList){
		this.originalThmStr = thmStr;
		this.parseRootList = parseRootList;
	}
	
	public ParsedExpression(String thmStr, ParseStruct headParseStruct,
			DefinitionListWithThm defListWithThm){
		this.originalThmStr = thmStr;
		this.headParseStruct = headParseStruct;
		this.defListWithThm = defListWithThm;
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
}
