package thmp.parse;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.wolfram.jlink.Expr;

import thmp.parse.ParseToWLTree.WLCommandWrapper;
import thmp.utils.ExprUtils;

/**
 * ParseStruct is a structure in the parse, can be
 * HYP, or STM
 * 
 * @author yihed
 */
public class ParseStruct implements Serializable{

	private static final long serialVersionUID = 880445087525293544L;
	//enum for type, e.g. "HYP", "STM", etc.
	//private ParseStructType componentType;
	//map of structures such as qualifying object, quantifying variables. 
	//The keys are enums. Should preserve keys as well!
	private List<ParseStruct> childrenParseStructList;
	//map of Structs, i.e. heads of statements. 
	//pointer to head Struct that leads this ParseStruct
	//private Struct headStruct;
	//structs on this layer of the tree.
	private Multimap<ParseStructType, WLCommandWrapper> wrapperMMap;
	
	//keep track of the trigger words that appear in WLCommands 
	//in WLCommandWrappers in the wrapperMMap. Created at construction.
	private Map<String, WLCommandWrapper> triggerWordsMap;
	
	/**
	 * @return the triggerWordsMMap
	 */
	public Map<String, WLCommandWrapper> getTriggerWordsMap() {
		return triggerWordsMap;
	}

	String WLCommandStr;
	
	/**
	 * @return the structList, list of structs associated with this 
	 * layer.
	 */
	public Multimap<ParseStructType, WLCommandWrapper> getWLCommandWrapperMMap() {
		return wrapperMMap;
	}
	
	/**
	 * Adds additional struct to the wrapperMMap (for this layer of ParseStruct tree).
	 */
	public void addParseStructWrapper(ParseStructType type, WLCommandWrapper wrapper) {
		//System.out.println(Arrays.toString(Thread.currentThread().getStackTrace()));
		//keep track of trigger words for this layer
		triggerWordsMap.put(wrapper.WLCommand().getTriggerWord(), wrapper);
		this.wrapperMMap.put(type, wrapper);
	}
	
	/**
	 * Adds additional wrappers to the wrapperMMap (for this layer).
	 */
	public void addParseStructWrapper(Multimap<ParseStructType, WLCommandWrapper> map) {
		//System.out.println(Arrays.toString(Thread.currentThread().getStackTrace()));
		for(WLCommandWrapper wrapper : map.values()){
			triggerWordsMap.put(wrapper.WLCommand().getTriggerWord(), wrapper);
		}		
		this.wrapperMMap.putAll(map);
	}
	
	/**
	 * @return the wLCommandStr
	 */
	public String getWLCommandStr() {
		return WLCommandStr;
	}

	/**
	 * @param wLCommandStr the wLCommandStr to set
	 */
	public void setWLCommandStr(String wLCommandStr) {
		WLCommandStr = wLCommandStr;
	}

	private ParseStruct parentParseStruct;
	
	public ParseStruct(){
		this.childrenParseStructList = new ArrayList<ParseStruct>();
		this.wrapperMMap = ArrayListMultimap.create();
		this.triggerWordsMap = new HashMap<String, WLCommandWrapper>();
		//how about just point to the same tree?
		//this.map = ArrayListMultimap.create(subParseTree);
	}
	
	/**
	 * @param type
	 * @param subStruct	Struct to be added to this ParseStruct 
	 */
	public void addToSubtree(ParseStruct subParseStruct){
		//System.out.println(Arrays.toString(Thread.currentThread().getStackTrace()));
		this.childrenParseStructList.add(subParseStruct);
	}
	
	public ParseStruct parentParseStruct(){
		return this.parentParseStruct;
	}
	
	public void set_parentParseStruct(ParseStruct parent){
		this.parentParseStruct = parent;
	}	
	
	@Override
	public String toString(){
		StringBuilder sb = new StringBuilder();
		return this.createStringAndRetrieveExpr(sb, new ArrayList<Expr>());
	}
	
	/**
	 * Fills exprList with commandExpr's collected during DFS.
	 * To be used in servlet, etc, to get the entire Expr structure.
	 * Creates a single Expr and add to exprList.
	 * @param sb
	 * @param exprList
	 * @return
	 */
	public String createStringAndRetrieveExpr(StringBuilder sb, List<Expr> exprList){
		
		Collection<Map.Entry<ParseStructType, WLCommandWrapper>> wrapperMMapEntries = 
				wrapperMMap.entries();
		int i = wrapperMMapEntries.size();
		
		List<Expr> curLevelExprList = new ArrayList<Expr>();
		boolean hasChild = childrenParseStructList.isEmpty();
		for(Map.Entry<ParseStructType, WLCommandWrapper> entry : wrapperMMapEntries){
			WLCommandWrapper wrapper = entry.getValue();
			Expr commandExpr = wrapper.commandExpr();
			//e.g. "STM"
			String entryKeyStr = entry.getKey().toString();
			Expr ruleExpr = ExprUtils.ruleExpr(new Expr(entryKeyStr), commandExpr);			
			curLevelExprList.add(ruleExpr);		
			if(i > 1 || !hasChild){			
				sb.append("\"").append(entryKeyStr).append("\" :> ").append(wrapper.WLCommandStr()).append(", ");
			}else{
				sb.append("\"").append(entryKeyStr).append("\" :> ").append(wrapper.WLCommandStr());
			}
			i--;
		}
		
		i = childrenParseStructList.size();		
		System.out.println("childrenParseStructList " +childrenParseStructList);
		for(ParseStruct childParseStruct : childrenParseStructList){
			if(i > 1){
				sb.append("{").append(childParseStruct.createStringAndRetrieveExpr(sb, curLevelExprList)).append("};");
			}else{
				sb.append("{").append(childParseStruct.createStringAndRetrieveExpr(sb, curLevelExprList)).append("}");
			}
			i--;
		}	
		//put rule in "Sentence" Head
		Expr sentenceExpr;
		int curLevelExprListSz = curLevelExprList.size();
		if(curLevelExprListSz > 1){
			sentenceExpr = ExprUtils.sentenceExpr(ExprUtils.sequenceExpr(curLevelExprList));
			exprList.add(sentenceExpr);
		}else if(curLevelExprListSz == 1){
			sentenceExpr = ExprUtils.sentenceExpr(curLevelExprList.get(0));
			exprList.add(sentenceExpr);
		}		 
		//exprList.add(ExprUtils.listExpr(curLevelExprList));		
		return sb.toString();
	}
	
}
