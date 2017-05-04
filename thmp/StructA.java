package thmp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

import com.wolfram.jlink.Expr;

import thmp.ParseToWLTree.WLCommandWrapper;
import thmp.Struct.ChildRelation;
import thmp.Struct.NodeType;
import thmp.WLCommand.PosTerm;
import thmp.utils.ExprUtils;
import thmp.utils.ExprUtils.AssocExprWrapper;
import thmp.utils.ExprUtils.RuleExprWrapper;
import thmp.utils.WordForms;

public class StructA<A, B> extends Struct{

	//a Struct can correspond to many MatrixPathNode's, but each MatrixPathNode 
	//corresponds to one unique Struct.
	//I.e. one-to-many map between Struct's and MatrixPathNode's
	
	private static final long serialVersionUID = 8934779328383033339L;
	private A prev1; 
	private B prev2; 
	//parentStruct is *not* unique! Depends on which DFS path we take.
	private Struct parentStruct;
	//Depth from root of the tree. Root has depth 0. *Not* intrinsic to the Struct, 
	//depends on the DFS path.
	private int depth;
	//score for this structA, to indicate likelihood of relation in Rule
	//Ranges over (0, 1]. 1 by default
	private double score;
	private String type; //or, and, adj, pro etc, cannot ent
	//private String type1; //type of prev1, , al, string etc. Is this used??
	//private String type2; //type of prev2. Also not used! Commented out Dec 2016.
	private NodeType PREV1_TYPE;
	private NodeType PREV2_TYPE;
	private List<Struct> children = new ArrayList<Struct>(); 
	//relation to child, eg "of," "which is", "over", 
	//as in "independent of $n$."
	private List<ChildRelation> childRelationList = new ArrayList<ChildRelation>();	
	private boolean hasChild;
	
	//additional part of speech
	private volatile Set<String> extraPosSet;
	//list of Struct at mx element, to which this Struct belongs
	//pointer to mx.get(i).get(j)
	//if not null, means this is head of some parsed WLCommand. 
	//private String WLCommandStr;
	//WLCommand associated with this Struct, should have corresponding WLCommandStr.
	//Perhaps group the WLCommandStr with this into the WLCommand?
	private List<WLCommandWrapper> WLCommandWrapperList;
	//how many times this Struct has been part of a WLCommand.
	private int WLCommandStrVisitedCount;
	//pointer to the head of a previously built Struct that already
	//contains this Struct, so no need to build this Struct again into the current 
	//WLCommand in build(), remember to reset to null after iterating through
	private Struct previousBuiltStruct;
	private Struct posteriorBuiltStruct;
	//the head Struct (to append to) of a WLCommand this Struct currently belongs to.
	//Not intrinsic to this Struct!
	private Struct structToAppendCommandStr;
	//list of structs in mx that this struct is attached to.
	private StructList structList;
	//includes this/current Struct's score!
	private double DOWNPATHSCOREDEFAULT = 1;
	private double maxDownPathScore = DOWNPATHSCOREDEFAULT;
	
	//number of units down from this Struct (descendents), used for tie-breaking.
	//StructH counts as one unit. Lower numUnits wins, since it means more consolidation.
	//e.g. children of MathObj's grouped together with the MathObj.
	//The lower the better.
	private int numUnits;
	//don't need mxPathNodeList. The path down from this Struct should 
	//be unique. It's the parents' paths to here that can differ
	//private List<MatrixPathNode> mxPathNodeList;
	private WLCommand commandBuilt;
	
	//is this ever needed?
	public StructA(A prev1, NodeType prev1Type, B prev2, NodeType prev2Type, String type, StructList structList){		
		this(prev1, prev1Type, prev2, prev2Type, type);
		this.structList = structList;
	}
	
	//should use different constructors to take in either Struct or String, rather than A, B !!!
	//so don't need the suppressWarnings
	@SuppressWarnings("unchecked")
	public StructA(A prev1, NodeType prev1Type, B prev2, NodeType prev2Type, String type){	
		this.PREV1_TYPE = prev1Type;
		this.PREV2_TYPE = prev2Type;
		if(prev1Type == NodeType.STR){
			this.prev1 = (A)((String)prev1).trim();
		}else{
			this.prev1 = prev1;	
		}		
		if(prev2Type == NodeType.STR){
			this.prev2 = (B)((String)prev2).trim();
		}else{
			this.prev2 = prev2;	
		}
		this.prev1 = prev1;		
		this.prev2 = prev2;
		this.type = type; 
		this.numUnits = 1;
		this.score = 1;
	}
	
	/**
	 * @param prev1
	 * @param prev2
	 * @param type
	 * @param score
	 * @param structList  pointer to list of Struct's containing this.
	 * @param downPathScore 
	 * @param numUnits
	 */
	public StructA(A prev1, NodeType prev1Type, B prev2, NodeType prev2Type, String type, double score, StructList structList, 
			double downPathScore, int numUnits){
		this.PREV1_TYPE = prev1Type;
		this.PREV2_TYPE = prev2Type;
		this.prev1 = prev1;		
		this.prev2 = prev2;
		this.type = type; 
		this.structList = structList;
		this.score = score;
		this.maxDownPathScore = downPathScore;
		//System.out.println(Arrays.toString(Thread.currentThread().getStackTrace()));
		this.numUnits = numUnits;
		//this.mxPathNodeList = new ArrayList<MatrixPathNode>();
	}
	
	public NodeType prev1NodeType(){
		return PREV1_TYPE;
	}
	
	public NodeType prev2NodeType(){
		return PREV2_TYPE;
	}
	
	//this method should never be called on StructA
	//Would be safer to remove from abstract class, cast
	//the Struct to StructH in ThmP1, and call struct() on that.
	//That way ClassCastException will be generated instead of
	//problems down the road by caused by null.
	public Map<String, String> struct(){		
		return null;
	}
	
	/**
	 * Shallow copy. In particular, does not copy children objects, children
	 * point to the same children of this StructA. 
	 */
	public StructA<A, B> copy(){
		//shallow copy of structlist
		StructA<A, B> newStruct;
		if(null != this.structList){
			StructList copiedStructlist = this.structList.copy();	
			newStruct = new StructA<A, B>(this.prev1, this.PREV1_TYPE, 
					this.prev2, this.PREV2_TYPE, this.type, copiedStructlist);
		}else{
			newStruct = new StructA<A, B>(this.prev1, this.PREV1_TYPE, 
					this.prev2, this.PREV2_TYPE, this.type);
		}
		newStruct.maxDownPathScore = this.maxDownPathScore;
		newStruct.numUnits = this.numUnits;
		newStruct.score = this.score;
		newStruct.parentStruct = this.parentStruct;
		newStruct.set_childRelationType(this.childRelationType());
		this.copyChildrenToStruct(newStruct);
		//newStruct.WLCommandStr = this.WLCommandStr;
		return newStruct;
	}
	
	public StructA<? extends Object, ? extends Object> copyToStructA(String newType){
		return this;
	}
	/**
	 * Retrieves the left-most child of this struct, which should
	 * be a String based on the structure of Struct.
	 * @return
	 */
	public String getLeftMostChild(){
		StructA<?,?> curStructA = this;
		while(curStructA.prev1 instanceof StructA){
			curStructA = (StructA<?,?>)curStructA.prev1;
		}
		return curStructA.prev1.toString();
	}
	
	@Override
	protected void setHasChildToTrue(){
		this.hasChild = true;
	}
	
	/**
	 * Set parent pointer
	 * @param parent	parent Struct
	 */
	@Override
	public void set_parentStruct(Struct parent){
		this.parentStruct = parent;
	}
	
	@Override
	public Struct parentStruct(){		
		return this.parentStruct;
	}
	
	/**
	 * Set possessivePrev.
	 * @param prev	
	 */
	@Override
	public void set_possessivePrev(Struct prev){
		
	}
	
	@Override
	public Struct possessivePrev(){
		return null;
	}
	
	/**
	 * Sets the depth from root of the tree. Root has depth 0. *Not* intrinsic to the Struct,
	 * depends on the DFS path.
	 */
	@Override
	public void set_dfsDepth(int depth){
		this.depth = depth;
	}
	
	@Override
	public int dfsDepth(){
		return this.depth;
	}
	
	public int WLCommandStrVisitedCount(){
		return this.WLCommandStrVisitedCount;
	}
	
	public void clear_WLCommandStrVisitedCount(){
		this.WLCommandStrVisitedCount = 0;
	}
	
	public List<WLCommandWrapper> WLCommandWrapperList(){
		return this.WLCommandWrapperList;
	}
	
	public void clear_WLCommandWrapperList(){
		this.WLCommandWrapperList = null;
	}
	
	/**
	 * Retrieves corresponding WLCommandWrapper.
	 * @return
	 */
	public WLCommand WLCommandWrapper(WLCommandWrapper curCommandWrapper){		
		return this.WLCommandWrapperList.get(curCommandWrapper.listIndex()).WLCommand();
	}

	/**
	 * Create WLCommandWrapper from WLCommand.
	 * Add it to list of WLCommandWrappers.
	 * @return the created Wrapper.
	 */
	public WLCommandWrapper add_WLCommandWrapper(WLCommand curCommand){		
		//System.out.println(Arrays.toString(Thread.currentThread().getStackTrace()));
		if(this.WLCommandWrapperList == null){
			this.WLCommandWrapperList = new ArrayList<WLCommandWrapper>();
		}
		int listIndex = this.WLCommandWrapperList.size();
		WLCommandWrapper curCommandWrapper = new WLCommandWrapper(curCommand, listIndex);
		//add wrapper reference to curCommand
		curCommand.setCommandWrapper(curCommandWrapper);
		this.WLCommandWrapperList.add(curCommandWrapper);
		return curCommandWrapper;
	}
	
	/**
	 * Only applicable to StructH.
	 */
	@Override
	public Set<String> getPropertySet(){
		return Collections.<String>emptySet();
	}
	
	@Override
	public String simpleToString(boolean includeType, WLCommand curCommand, List<Expr> exprList){
		return simpleToString(includeType, curCommand, null, null, exprList);
	}
	
	@Override
	public String simpleToString(boolean includeType, WLCommand curCommand){
		return simpleToString(includeType, curCommand, null, null, new ArrayList<Expr>());
	}
	/**
	 * @param includeType
	 * @param curCommand Command that the returned String is built towards.
	 * curCommand is null if this struct should not be counted towards commandNumUnits.
	 * Should only be called during building command, WLCommand.build()!
	 * (during ParseToWLTree.buildWLCommandTreeDfs()).
	 * This updates scores, e.g. commandNumUnits.
	 * @return
	 */
	@Override
	public String simpleToString(boolean includeType, WLCommand curCommand, PosTerm triggerPosTerm, PosTerm curPosTerm,
			List<Expr> exprList){

		/*if(this.type.equals("assert")){
			System.out.println("StructA - this "+ this);
			System.out.println("StructA - (Struct)this.prev1).type() "+ ((Struct)this.prev1).type());
			System.out.println("this.WLCommandWrapperList " + this.WLCommandWrapperList);
			//throw new RuntimeException("WLCommandWrapperList " +this.WLCommandWrapperList);
		}*/
		//System.out.println(Arrays.toString(Thread.currentThread().getStackTrace()));
		if(this.WLCommandWrapperList != null){
			int wrapperListSz = WLCommandWrapperList.size();
			//wrapperListSz should be > 0, since list is created when first wrapper is added
			
			WLCommandWrapper curWrapper = WLCommandWrapperList.get(wrapperListSz - 1);
			WLCommand composedCommand = curWrapper.WLCommand(); 
			Struct structHeadWithOtherHead = null;
			if(WLCommand.structsWithOtherHeadCount(composedCommand) == 0 || 
					null != (structHeadWithOtherHead = composedCommand.structHeadWithOtherHead())
					//should more precisely check for .equals, but dfsDepth suffices here.
					&& this.dfsDepth() == structHeadWithOtherHead.dfsDepth()){//HERE
				//if(WLCommand.structsWithOtherHeadCount(composedCommand) > 0){
					//System.out.println("structA WLCommand.structsWithOtherHeadCount(composedCommand) " + WLCommand.structsWithOtherHeadCount(composedCommand));
				//}
			if(curWrapper != null){
				int commandNumUnits = WLCommand.commandNumUnits(composedCommand);
				//System.out.println("StructA - commandNumUnits composed " + commandNumUnits);
				WLCommand.increment_commandNumUnits(curCommand, commandNumUnits);
				//System.out.println("increment_commandNumUnits : numUnits " + commandNumUnits + " composedCommand: " + composedCommand); 				
			}
			//been built into one command already
			if(null == this.commandBuilt){
				this.commandBuilt = curCommand;
				this.WLCommandStrVisitedCount++;
			}else if(!curCommand.equals(this.commandBuilt)){
				this.WLCommandStrVisitedCount++;				
			}			
			//System.out.println("WLCommandStrVisitedCount" + this.WLCommandStrVisitedCount);
			//System.out.println("++++++===curWrapper " +curWrapper.WLCommandStr() + " " + this );
			curCommand.addComposedWLCommands(composedCommand);
			return curWrapper.WLCommandStr();	
			}
		}		
		
		if(PREV1_TYPE.equals(NodeType.STR) && PREV2_TYPE.equals(NodeType.STR)){
			
			StringBuilder fullContentSB = new StringBuilder((String)this.prev1);
			Expr prev1Expr = new Expr((String)this.prev1);
			//System.out.println("*********prev1 type: " + type());
			String makePptStr = retrievePosTermPptStr(triggerPosTerm, curPosTerm);
			//child must return an Association ExprWrapper 
			//e.g. "Qualifiers" -> {"with", Math["Name"->"axiom"]}
			List<RuleExprWrapper> childRuleWrapperList = new ArrayList<RuleExprWrapper>();
			String childStr = appendChildrenQualifierString(includeType, curCommand, childRuleWrapperList);
			Expr headExpr;
			//List<Expr> strExprList = new ArrayList<Expr>();
			//String prev1Str = (String)this.prev1;
			if(this.type.equals("det") || this.type.equals("pro")){				
				if(!prev2.equals("")){
					fullContentSB.insert(0, "Reference[\"").append("\", \"").append((String)this.prev2)
						.append(childStr).append("\"]");
					headExpr = new Expr(Expr.SYMBOL, "Reference");
					if(childRuleWrapperList.size() > 0){
						exprList.add(new Expr(headExpr, new Expr[]{prev1Expr, new Expr((String)this.prev2), 
								childRuleWrapperList.get(0).expr()}));
					}else{
						exprList.add(new Expr(headExpr, new Expr[]{prev1Expr, new Expr((String)this.prev2)}));						
					}
				}else{
					fullContentSB.insert(0, "Reference[\"").append(childStr).append("\"]");
					headExpr = new Expr(Expr.SYMBOL, "Reference");
					if(childRuleWrapperList.size() > 0){
						exprList.add(new Expr(headExpr, new Expr[]{prev1Expr, childRuleWrapperList.get(0).expr()}));
					}else{
						exprList.add(new Expr(headExpr, new Expr[]{prev1Expr}));
					}
				}
			}else{
				//quotes around prev1 string, construct Expr from children
				fullContentSB.insert(0, "\"").append("\"");
				if(this.type.equals("adj") || this.type.equals("adverb") || this.type.equals("qualifier") ){
					
					fullContentSB.insert(0, " MathProperty[").append(childStr).append("]");
					
					headExpr = new Expr(Expr.SYMBOL, "MathProperty");
					if(childRuleWrapperList.size() > 0){
						exprList.add(new Expr(headExpr, new Expr[]{prev1Expr, childRuleWrapperList.get(0).expr()}));
					}else{
						exprList.add(new Expr(headExpr, new Expr[]{prev1Expr}));
					}
						
				}else if(prev2.equals("")){
					/*use "Math" Head here generally so not to have headless object. But perhaps should be more specific.*/
					if(!"".equals(makePptStr)){
						String pptCommaStr = "\"" + makePptStr + "\", ";
						fullContentSB.insert(0, pptCommaStr).insert(0, "MathProperty[").append(childStr).append("]");
						Expr makePptExpr = new Expr(makePptStr);
						headExpr = new Expr(Expr.SYMBOL, "MathProperty");
						if(childRuleWrapperList.size() > 0){
							exprList.add(new Expr(headExpr, new Expr[]{makePptExpr, prev1Expr, childRuleWrapperList.get(0).expr()}));
						}else{
							exprList.add(new Expr(headExpr, new Expr[]{makePptExpr, prev1Expr}));							
						}
					}else{
						fullContentSB.insert(0, "Math[").append(childStr).append("]");
						headExpr = new Expr(Expr.SYMBOL, "Math");
						if(childRuleWrapperList.size() > 0){
							exprList.add(new Expr(headExpr, new Expr[]{prev1Expr, childRuleWrapperList.get(0).expr()}));
						}else{
							exprList.add(new Expr(headExpr, new Expr[]{prev1Expr}));							
						}
					}
					//if(this.type.equals("texAssert")) System.out.println("StructA -struct "+ this +"has child? " +this.hasChild);
					///throw new IllegalStateException("makePptStr " + makePptStr + " " + triggerPosTerm.isPropertyTerm());
				}
				if(!prev2.equals("")){
					if(!"".equals(makePptStr)){
						fullContentSB.insert(0, makePptStr).insert(0, "MathProperty[").append(", \"").append((String)this.prev2).append(childStr).append("\"]");
						Expr makePptExpr = new Expr(makePptStr);
						Expr existingExpr = ExprUtils.sequenceExpr(exprList);
						headExpr = new Expr(Expr.SYMBOL, "MathProperty");
						if(childRuleWrapperList.size() > 0){
							exprList.add(new Expr(headExpr, new Expr[]{makePptExpr, existingExpr, new Expr((String)this.prev2), childRuleWrapperList.get(0).expr()}));
						}else{
							exprList.add(new Expr(headExpr, new Expr[]{makePptExpr, existingExpr, new Expr((String)this.prev2)}));							
						}
					}else{
						fullContentSB.insert(0, "Math[").append(", \"").append((String)this.prev2).append(childStr).append("\"]");
						Expr existingExpr = ExprUtils.sequenceExpr(exprList);
						headExpr = new Expr(Expr.SYMBOL, "Math");
						if(childRuleWrapperList.size() > 0){
							exprList.add(new Expr(headExpr, new Expr[]{existingExpr, new Expr((String)this.prev2), childRuleWrapperList.get(0).expr()}));
						}else{
							exprList.add(new Expr(headExpr, new Expr[]{existingExpr, new Expr((String)this.prev2)}));							
						}
					}
				}
			}				
			
			//been built into one command already
			//this.WLCommandStrVisitedCount++;
			if(null == this.commandBuilt){
				this.commandBuilt = curCommand;
				this.WLCommandStrVisitedCount++;
			}else if(!curCommand.equals(this.commandBuilt)){
				this.WLCommandStrVisitedCount++;
			}			
			WLCommand.increment_commandNumUnits(curCommand, this);		
			//if(added){
				//fullContentSB.append(" ADDED ");
				//System.out.println(" THIS  " + this + " ADDED for command " + curCommand);
			//}
			return fullContentSB.toString();
		}else{		
			//System.out.println("+++" + this.simpleToString2(includeType, curCommand));
			return this.simpleToString2(includeType, curCommand, triggerPosTerm, exprList);
		}
	}
	
	/**
	 * Auxilliary method for simpleToString; also called inside StructH.simpleToString2.
	 * Try to get a single Expr onto exprList, so to construct only one Expr per struct.simpleToString call.
	 * @param includeType
	 * @param curCommand
	 * @param triggerPosTerm
	 * @param exprList
	 * @return
	 */
	private String simpleToString2(boolean includeType, WLCommand curCommand, PosTerm triggerPosTerm,
			List<Expr> exprList){		
		/*if(type.equals("assert")){
			System.out.println("StructA -");
			//System.out.println(Arrays.toString(Thread.currentThread().getStackTrace()));
		}*/
		//return "" if commandStr is not null, so to not repeat commands.
		//if(this.WLCommandStr != null) return "";
		/*if(this.WLCommandWrapperList != null){ 
			return "";
		}*/		
		//been built into one command already
		//this.WLCommandStrVisitedCount++;
		if(null == this.commandBuilt){
			this.commandBuilt = curCommand;
			this.WLCommandStrVisitedCount++;
		}else if(!curCommand.equals(this.commandBuilt)){
			this.WLCommandStrVisitedCount++;
		}
		List<Expr> curLevelExprList = new ArrayList<Expr>();
		//don't include prepositions for spanning purposes, since prepositions are almost 
		//always counted if its subsequent entity is, but counting it gives false high span
		//scores, especially compared to the case when they are absorbed into a StructH, in
		//which case they are not counted.
		if(curCommand != null && !this.type.equals("pre")){ 
			WLCommand.increment_commandNumUnits(curCommand, this);
			/*if(added){
				//tempSB.append(" ADDED ");
				//System.out.println("ADDED for command " + curCommand + " THIS  " + this);
			}*/
		}
		//whether to wrap braces around the subcontent, to group terms together.
		//wrap if type is phrase, etc
		boolean wrapBraces = false;
		boolean inConj = false;
		//String str = "";
		StringBuilder tempSB = new StringBuilder();
		//tempStr to add to
		//String tempStr = "";		
		
		Expr conjDisjHeadExpr = null;
		//str += this.type.matches("conj_.*|disj_.*") ? this.type.split("_")[0] +  " " : "";		
		//also wrap braces around prev1 and prev2 or the conj/disj
		if(this.type.matches("conj_.*|disj_.*")){
			String toAppend = this.type.matches("conj_.*") ? "Conjunction" : "Disjunction";
			conjDisjHeadExpr = new Expr(Expr.SYMBOL, toAppend);
			//str += this.type.split("_")[0] + " ";
			//str += toAppend;
			inConj = true;
			//wrapBraces = true;
			//tempStr += "[";
			tempSB.append(toAppend).append("[");
		}
		
		if(this.type.equals("phrase") || this.type.equals("prep")
				){
			wrapBraces = true;		
			//tempStr += "{";
			tempSB.append("{");
		}		
		//NEED TO CONSTRUCT EXPR's here, including Conj and Disj Expr's!!!
		if(this.prev1 != null){
			/*if(inConj){ //tempStr += "{";
				tempSB.append("{");
			}*/
			//if(prev1 instanceof Struct && ((Struct) prev1).WLCommandStr() == null){
			if((PREV1_TYPE.isTypeStruct()) ){
					//&& ((Struct) prev1).WLCommandWrapperList() == null){
				List<WLCommandWrapper> prev1WrapperList = ((Struct) prev1).WLCommandWrapperList();
				if(prev1WrapperList == null){
					String prev1Str = ((Struct) prev1).simpleToString(includeType, curCommand, curLevelExprList);
					if(!WordForms.getWhiteEmptySpacePattern().matcher(prev1Str).matches()){
						//tempSB.append(prev1Str);
						int tempSBLen = tempSB.length();
						if(tempSBLen > 1){
							char tempSBLastChar = tempSB.charAt(tempSBLen-1);
							if(' ' != tempSBLastChar && ' ' != prev1Str.charAt(0)){
								tempSB.append(" ").append(prev1Str);							
							}else{
								tempSB.append(prev1Str);
							}
						}else{
							tempSB.append(prev1Str);							
						}
					}
				}else{
					WLCommandWrapper prev1Wrapper = prev1WrapperList.get(0);
					tempSB.append(prev1Wrapper.WLCommandStr());					
					curLevelExprList.add(prev1Wrapper.commandExpr());
				}
			}else if(PREV1_TYPE.equals(NodeType.STR) && !prev1.equals("")){
				//if(!type.matches("pre|partiby")){
				if(!type.matches("partiby")){
					//if(prev1.equals("above")){
						//throw new RuntimeException(prev1.toString());
						//tempSB.append("\"").append(prev1).append("\"");
					//}
					//else{
					String prev1Str = prev1.toString();
					tempSB.append(prev1Str);
					curLevelExprList.add(new Expr(prev1Str));
					//}
				}
			}			
			/*if(inConj){ //tempStr += "}";
				tempSB.append("}");
			}*/
		}
		
		if(prev2 != null){
			//String prev2String = "";
			StringBuilder prev2SB = new StringBuilder(20);
			
			if((PREV2_TYPE.isTypeStruct())){
				List<WLCommandWrapper> prev2WrapperList = ((Struct)prev2).WLCommandWrapperList();
				 if(prev2WrapperList == null){
					//System.out.println("######prev2: " + prev2);
					String prev2Str = ((Struct) prev2).simpleToString(includeType, curCommand, curLevelExprList);
					if(!prev2Str.matches("\\s*")){
						if(tempSB.length() > 0){ //tempStr += ", ";
							tempSB.append(", ");
						}
						//prev2String += prev2Str;
						prev2SB.append(prev2Str);
					}
				 }else{
					// prev2String += prev2WrapperList.get(0).WLCommandStr();
					 if(tempSB.length() > 0){
						 prev2SB.append(", ");						 
					 }
					 WLCommandWrapper prev2Wrapper = prev2WrapperList.get(0);
					 prev2SB.append(prev2Wrapper.WLCommandStr());
					 curLevelExprList.add(prev2Wrapper.commandExpr());
				 }
			}else if(PREV2_TYPE.equals(NodeType.STR) && !((String)prev2).matches("\\s*")){			
				//prev2String += ", " + prev2;	
				String prev2Str = prev2.toString();				
				prev2SB.append(", ").append(prev2Str);
				curLevelExprList.add(new Expr(prev2Str));
			}
			//tempStr += inConj ? "{" + prev2String + "}" : prev2String;
			/*tempSB.append(inConj ? "[" + prev2SB + "]" : prev2SB);*/
			tempSB.append(prev2SB);
		}		
		List<RuleExprWrapper> ruleWrapperList = new ArrayList<RuleExprWrapper>();
		tempSB.append(appendChildrenQualifierString(includeType, curCommand, ruleWrapperList));
		if(ruleWrapperList.size() > 0){
			curLevelExprList.add(ruleWrapperList.get(0).expr());
		}	
		Expr combinedLevelExpr; 		
		if(null != conjDisjHeadExpr){
			combinedLevelExpr = ExprUtils.createExprFromList(conjDisjHeadExpr, curLevelExprList);
		}else{
			combinedLevelExpr = ExprUtils.listExpr(curLevelExprList);
		}
		exprList.add(combinedLevelExpr);
		
		if(wrapBraces){ //tempStr += "}";
			tempSB.append("}");
		}
		if(inConj){ //tempStr += "]";
			//this one must be left in place
			tempSB.append("]");
		}
		//str += tempStr;
		return tempSB.toString();
		//return str;
	}
	
	@Override
	public boolean isLeafNodeCouldHaveChildren(){
		return this.PREV1_TYPE.equals(NodeType.STR) && this.PREV2_TYPE.equals(NodeType.STR);
	}
	
	@Override
	public void clear_commandBuilt(){
		this.commandBuilt = null;
	}
	
	@Override
	public List<String> contentStrList(){		
		//String contentStr = "";
		List<String> contentStrList = new ArrayList<String>();
		if(PREV1_TYPE != null){
			if(PREV1_TYPE.equals(NodeType.STR)){
				String prev1Str = (String)prev1;
				if(!WordForms.getWhiteEmptySpacePattern().matcher(prev1Str).matches()){
					contentStrList.add(prev1Str);	
				}				
			}else if(PREV1_TYPE.isTypeStruct()){
				contentStrList.addAll(((Struct)prev1).contentStrList());
			}
		}
		if(PREV2_TYPE != null){
			if(PREV2_TYPE.equals(NodeType.STR)){
			//should not have the space if list is empty before
				String prev2Str = (String)prev2;
				if(!WordForms.getWhiteEmptySpacePattern().matcher(prev2Str).matches()){
					prev2Str = contentStrList.isEmpty() 
							? prev2Str : " " + prev2Str;
					contentStrList.add(prev2Str);		
				}				
			}else if(PREV2_TYPE.isTypeStruct()){
				contentStrList.addAll(((Struct)prev2).contentStrList());
			}
		}		
		return contentStrList;
	}
	
	/**
	 * @return will not be null.
	 */
	@Override
	public String nameStr(){		
		String nameStr = "";
		if(PREV1_TYPE != null && PREV1_TYPE.equals(NodeType.STR)){
			nameStr = (String) prev1;			
		}
		return nameStr;
	}
	
	/**
	 * Calling the applicable ParseTreeToVec with dynamic dispatch.
	 * To avoid casting, and to distribute the logic.
	 */
	@Override
	public void setContextVecEntry(int structParentIndex, Map<Integer, Integer> contextVecMap, boolean adjustVecFromCommand){
		ParseTreeToVec.setStructAContextVecEntry(this, structParentIndex, contextVecMap, adjustVecFromCommand);
	}
	
	/**
	 * 
	 * @param prev1
	 * @param prev2
	 * @param type
	 * @param structList   pointer to list of Struct's containing this.
	 */
	/*
	public StructA(A prev1, B prev2, String type, double score, StructList structList){		
		this.prev1 = prev1;		
		this.prev2 = prev2;
		this.type = type; 
		this.structList = structList;
		this.score = score;
		this.numUnits = 1;
		//this.mxPathNodeList = new ArrayList<MatrixPathNode>();
	} */
	
	@Override
	public int numUnits(){
		return this.numUnits;
	}
	
	public Struct previousBuiltStruct(){
		return this.previousBuiltStruct;
	}
	
	public Struct posteriorBuiltStruct(){
		return this.posteriorBuiltStruct;
	}
	
	public void set_previousBuiltStruct(Struct previousBuiltStruct){
		this.previousBuiltStruct = previousBuiltStruct;
	}
	
	public void set_posteriorBuiltStruct(Struct posteriorBuiltStruct){
		this.posteriorBuiltStruct = posteriorBuiltStruct;
	}
	
	public Struct structToAppendCommandStr(){
		return this.structToAppendCommandStr;
	}
	
	public void set_structToAppendCommandStr(Struct structToAppendCommandStr){
		this.structToAppendCommandStr = structToAppendCommandStr;
	}

	
	@Override
	public void set_structList(StructList structList){
		this.structList = structList;
	}

	@Override
	public StructList get_StructList(){
		return this.structList;
	}
	
	/**
	 * List of additional pos, e.g. "prime" has pos adj, but is 
	 * often used as an "ent". "type()" contains primary pos.
	 * @return
	 */
	@Override
	public Set<String> extraPosSet(){
		return this.extraPosSet;
	}
	
	/**
	 * Add additional parts of speech to this struct.
	 */
	@Override
	public void addExtraPos(String pos){
		
		//Lazy initialization with double-check locking.
		if(extraPosSet == null){
			synchronized(this){
				if(extraPosSet == null){
					extraPosSet = new HashSet<String>();
				}
			}
		}
		extraPosSet.add(pos);		
	}
	
	@Override
	public double maxDownPathScore(){
		return this.maxDownPathScore;
	}
	
	/**
	 * This sets the max path score among any mxPathNode's that
	 * pass here. *down* score
	 * 
	 * These scores will in turn be selected for the max score 
	 * inside the StructList this Struct is on.
	 * @param pathScore
	 */
	@Override
	public void set_maxDownPathScore(double pathScore){
		this.maxDownPathScore = pathScore;
	}
	
	@Override
	public double score(){
		return this.score;
	}
	
	public boolean has_child(){
		return hasChild;
	}
	
	@Override
	public A prev1(){
		return this.prev1;		
	}
	
	@Override
	public B prev2(){
		return this.prev2;		
	}

	//use carefully: must know the declared type
	/////remove necessity to cast!
	@Override
	@SuppressWarnings("unchecked")
	public void set_prev1(Object prev1){
		this.prev1 = (A)prev1;	 
		if(PREV1_TYPE.isTypeStruct()){
			this.PREV1_TYPE = ((Struct) prev1).isStructA() ? NodeType.STRUCTA : NodeType.STRUCTH;			
		}
	}
	
	//***this is terrible! Cannot just cast String
	@SuppressWarnings("unchecked")
	public void set_prev1(String prev1){		
		if(!(PREV1_TYPE.equals(NodeType.STR))) {
			String msg = "PREV1_TYPE should be String rather than " + PREV1_TYPE + "!";		
			System.out.println(msg);
			this.PREV1_TYPE = NodeType.STR;
			//assert false : msg;
		}
		this.prev1 = (A)prev1;
	}
	
	@Override
	@SuppressWarnings("unchecked")
	public void set_prev2(Object prev2){
		this.prev2 = (B)prev2;
		if(prev2 instanceof Struct){
			this.PREV2_TYPE = ((Struct) prev2).isStructA() ? NodeType.STRUCTA : NodeType.STRUCTH;			
		}
	}

	@SuppressWarnings("unchecked")
	public void set_prev2(String prev2){
		this.prev2 = (B)prev2;	
		if(!(PREV2_TYPE.equals(NodeType.STR))) {
			//String msg = "PREV1_TYPE should be String rather than " + PREV1_TYPE + "!";		
			//System.out.println(msg);
			this.PREV2_TYPE = NodeType.STR;
			//assert false : msg;
		}
	}
	
	@Override
	public String type(){
		return this.type;		
	}

	@Override
	public void set_type(String type){
		this.type = type;		
	}
	
	/*public String type1(){
		return this.type1;		
	}

	public String type2(){
		return this.type2;		
	}*/

	@Override
	public void set_score(double score){
		this.score = score;
	}

	@Override
	public String toString(){
		//if(this.type.equals("symb")) System.out.println(Arrays.toString(Thread.currentThread().getStackTrace()));
		/* concatenating together with '+' in one op gives same performance as with SB */
		String str = "[Type: " + this.type 				
				+ ", prev1:" + this.prev1 +"]";		
		return str;
	}
	
	//used by present() in StructH; right now no need
	//to go deeper into prev1/prev2
	@Override
	public String present(String str){
		//str += this.type + "[";
		boolean showprev1 = true;
		if(this.type.matches("hyp") && this.prev1NodeType().equals(NodeType.STR)
				&& !((String)this.prev1).matches("for all|for every")){
			showprev1 = false;
		}
		
		//str += "[";
		//temporary string used to add to main str later. Use StringBuilder
		//in revamp!
		String tempStr = "";
		
		if(prev1 != null && !prev1.equals("")){
			if(PREV1_TYPE.isTypeStruct()){
				tempStr = ((Struct) prev1).present(str);
				
			}else if(PREV1_TYPE.equals(NodeType.STR) && showprev1){
				if(!type.matches("pre|partiby")){
					tempStr += prev1;
				}
			}			
		}
		
		if(prev2 != null && !prev2.equals("")){
			if(PREV2_TYPE.isTypeStruct()){
				tempStr = ((Struct) prev2).present(str + ", ");
			}else{
				tempStr += ", " + prev2;
			}
		}
		
		if(!tempStr.matches("\\s*")){
			str += this.type.matches("conj_.*") ? this.type + "[" + tempStr + "]": "[" + tempStr + "]";
		}
		return str;
	}
	
	@Override
	public List<Struct> children() {
		return this.children;
	}
	
	@Override
	public List<ChildRelation> childRelationList() {
		return this.childRelationList;
	}
	
}
