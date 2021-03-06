package thmp.parse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.wolfram.jlink.Expr;

import thmp.parse.ParseToWLTree.WLCommandWrapper;
import thmp.parse.Struct.NodeType;
import thmp.parse.WLCommand.PosTerm;
import thmp.utils.ExprUtils;
import thmp.utils.ExprUtils.AssocExprWrapper;
import thmp.utils.ExprUtils.ExprWrapper;
import thmp.utils.ExprUtils.ExprWrapperType;
import thmp.utils.ExprUtils.MathExprWrapper;
import thmp.utils.ExprUtils.MathPptExprWrapper;
import thmp.utils.ExprUtils.RuleExprWrapper;
import thmp.utils.WordForms;
/**
 * 
 * *Need* to modify copy() when any field is added to StructH class.
 * @author yihed
 * @param <H>
 */
public class StructH<H> extends Struct{

	private static final long serialVersionUID = 576033501556939586L;
	private Map<String, String> struct; //hashmap
	//ent (entity) is only structure that uses hashmap
	//Primary part of speech, ent, adj, etc. 
	private String type; 
	//*additional* part of speech
	private volatile Set<String> extraPosSet;
	
	//the number of times this Struct has been visited in collect-WLCommandStr DFS.
	//To not redundantly include strings, gather into final grammar 
	//rule string only if this is 0.
	private int WLCommandStrVisitedCount;
	//List of WLCommandWrapper associated with this Struct, should have 
	//corresponding WLCommandStr. Each Wrapper contains its index in list.
	//private WLCommand WLCommand;
	private List<WLCommandWrapper> WLCommandWrapperList;
	
	//pointer to the head of a previously built Struct that already
	//contains this Struct, so no need to build this Struct again into the current 
	//WLCommand in build(), remember to reset to null after iterating through
	private Struct previousBuiltStruct;
	//don't think the posterior one is used.
	private Struct posteriorBuiltStruct;
	//the head Struct (to append to) of a WLCommand this Struct currently belongs to.
	//Not intrinsic to this Struct!
	private Struct structToAppendCommandStr;
	//parentStruct is *not* unique! Depends on which DFS path we take.
	private Struct parentStruct;
	//Depth from root of the tree. Root has depth 0. *Not* intrinsic to the Struct, 
	//depends on the DFS path.
	private int depth;
	private boolean hasChild;
	private List<Struct> children = new ArrayList<Struct>();
	//relation to child, eg "of," "which is", "over", 
	//as in "field over Q."
	private List<ChildRelation> childRelationList = new ArrayList<ChildRelation>();	
	private double score;
	private double DOWNPATHSCOREDEFAULT = 1;
	private double maxDownPathScore = DOWNPATHSCOREDEFAULT;
	private StructList structList;
	private int NUMUNITS = 1;
	//Struct that's the owner of this structH with a possesive term relation
	//eg related by its/their.
	private Struct possessivePrev;
	//set of properties, to avoid iterating through the HashMap struct each time 
	//properties are needed (since "ppt" are values, not keys, in struct). This set caches the properties.
	//Should not have used String "ppt" to record properties in struct!
	private final Set<String> propertySet = new HashSet<String>();
	//use this variable to avoid race conditions, since propertySet can be added to by multiple methods.
	private volatile boolean isPropertySetCreated = true;
	
	//whether this structH arises from a latex expression, e.g. $x > 0$.
	//Used to convert ent into assert if necessary. 
	private boolean isLatexStruct;
	
	private WLCommand commandBuilt;
	
	public StructH(Map<String, String> struct, String type){	
		this.struct = struct;
		this.type = type;
		this.score = 1;
	}
	
	public StructH(Map<String, String> struct, String type, 
			StructList structList, double downPathScore){
		this.maxDownPathScore = downPathScore;
		this.struct = struct;
		this.type = type;
		this.score = 1;
		this.structList = structList;
	}

	/**
	 * name in struct is mandatory.
	 * @param type
	 * @param name
	 */
	public StructH(String type){
		this.type = type;
		this.score = 1;
	}	
	
	/**
	 * Set parent pointer
	 * @param parent  parent Struct
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
		this.possessivePrev = prev;
	}
	
	@Override
	public Struct possessivePrev(){
		return this.possessivePrev;
	}
	
	@Override
	public void set_dfsDepth(int depth){
		this.depth = depth;
	}
	
	@Override
	public int dfsDepth(){
		return this.depth;
	}
	
	@Override
	public NodeType prev1NodeType(){
		return NodeType.NONE;
	}
	
	@Override
	public NodeType prev2NodeType(){
		return NodeType.NONE;
	}
	
	/*
	@Override
	public void append_WLCommandStr(String WLCommandStr){
		this.WLCommandStr = this.WLCommandStr == null ? "" : this.WLCommandStr;
		this.WLCommandStr += " " + WLCommandStr;
	}
	
	@Override
	public void clear_WLCommandStr(){
		this.WLCommandStr = null;
	}
	
	@Override
	public String WLCommandStr(){
		return this.WLCommandStr;
	}
	*/
	
	/**
	 * @return the isLatexStruct
	 */
	@Override
	public boolean isLatexStruct() {
		return isLatexStruct;
	}
	
	@Override
	public boolean containsLatexStruct() {
		return isLatexStruct || null != struct.get("tex");
	}

	/**
	 * @param isLatexStruct the isLatexStruct to set
	 */
	public void setLatexStructToTrue() {
		this.isLatexStruct = true;
	}

	public void set_struct(HashMap<String, String> struct){
		assert(struct.containsKey("name"));
		this.struct = struct;
	}

	/**
	 * Double dispatch for measuring spans of methods.
	 */
	public int getPosTermListSpan(){
		return WLCommand.getPosTermListSpan(this);
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
	 * (Beyond the first/default pos.)
	 */
	@Override
	public void addExtraPos(String pos){
		//System.out.println("StructH addExtraPos" + this + Arrays.toString(Thread.currentThread().getStackTrace()));
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
	public void set_maxDownPathScore(double pathScore){
		this.maxDownPathScore = pathScore;
	}
	
	@Override
	public double maxDownPathScore(){
		return this.maxDownPathScore;
	}
	
	@Override
	public double score(){
		return this.score;
	}
	
	@Override
	public void set_score(double score){
		this.score = score;
	}
	
	@Override
	public int numUnits(){
		return NUMUNITS;
	}
	
	/**
	 * Make deep copy, struct and children children are copied.
	 * Parent of newly copied structs is previous Struct's parent.
	 * *Need* to modify this when any field is added to StructH class.
	 * @return
	 */
	@Override
	public StructH<H> copy(){
		
		Map<String, String> structCopy = new HashMap<String, String>(this.struct);
		StructH<H> newStructH = new StructH<H>(structCopy, this.type, this.structList,
				this.maxDownPathScore);
		
		for(int i = 0; i < this.children.size(); i++){
			Struct child = children.get(i);
			newStructH.add_child(child, this.childRelationList.get(i));
			//copy should not modify the state of the original struct! But this changes
			//the parent of the child!
			child.set_parentStruct(newStructH);
		}		
		newStructH.set_parentStruct(parentStruct);
		newStructH.setNoTexTokenListIndex(this.noTexTokenListIndex());
		newStructH.setNumCoincidingRelationIndex(this.numCoincidingRelationIndex());
		if(this.isLatexStruct){
			newStructH.setLatexStructToTrue();
		}
		return newStructH;
	}
	
	/**
	 * To be used in e.g. turning into texAssert. Use should be very limited, loses information!
	 * @param newType
	 * @return
	 */
	@Override
	public StructA<? extends Object, ? extends Object> copyToStructA(String newType){
		StringBuilder pptSB = new StringBuilder(30);
		for(String ppt : this.propertySet){
			pptSB.append(ppt).append(" ");
		}
		int pptSBLen = pptSB.length();
		if(pptSBLen > 1){
			pptSB.deleteCharAt(pptSBLen-1);
		}

		StructA<String, String> convertedStructA = new StructA<String, String>(this.nameStr(), 
				NodeType.STR, pptSB.toString(), NodeType.STR, newType);
		
		this.copyChildrenToStruct(convertedStructA);
		convertedStructA.set_parentStruct(this.parentStruct());
		convertedStructA.setNoTexTokenListIndex(this.noTexTokenListIndex());
		return convertedStructA;
	}
	
	public void add_previous(Struct prev){
		hasChild = true;
		children.add(prev);
		//if no relation specified
		childRelationList.add(new ChildRelation(""));
	}

	@Override
	protected void setHasChildToTrue(){
		this.hasChild = true;
	}

	/**
	 * Compare, if the struct is nonempty, the names, and returns 
	 * true if and only if the names are the same. 
	 * @param other
	 * @return
	 */
	@Override
	protected boolean contentEquals(Struct other){
		
		if(null == other || other.isStructA()){
			return false;
		}
		
		if(this == other){
			return true;
		}
		
		if(null != other.struct() && null != this.struct){
			String otherNameStr = other.struct().get("name");
			String thisNameStr = this.struct.get("name");
			
			if(null == otherNameStr || null == otherNameStr){
				return false;
			}
			
			if(otherNameStr.equals(thisNameStr)){
				return true;
			}
		}
		return false;
	}
	
	public boolean has_child(){
		return hasChild;		
	}
	
	@Override
	public List<Struct> children(){
		return children;		
	}
	
	@Override
	public List<ChildRelation> childRelationList(){
		return childRelationList;		
	}
	
	@Override
	public Map<String, String> struct(){		
		return this.struct;		
	}
	
	@Override
	public String type(){
		return this.type;		
	}
	
	@Override
	public void set_type(String type){
		this.type = type;		
	}
	
	@Override
	public String toString(){
		StringBuilder sb = new StringBuilder();
		sb.append("[" + this.type);
		//String str = "[" + this.type;
		sb.append(this.struct);
		//str += this.struct; //struct is hashmap for structH
		/*for(Map.Entry<String, String> e: this.struct.entrySet()){
			str += e;
		}*/
		if(this.possessivePrev != null){
			sb.append("possessivePrev: ");
			sb.append(possessivePrev.type());
		}
		/*if(children.size() > 0){
			//sb.append(" children: ");
			sb.append("||"+children.size() + "||");
		}*/
		return sb.append(']').toString();
	}
	
	public int WLCommandStrVisitedCount(){
		return this.WLCommandStrVisitedCount;
	}
	
	public void clear_WLCommandStrVisitedCount(){
		this.WLCommandStrVisitedCount = 0;
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
	
	/**
	 * Set the WLCommandWrapper.
	 * @param newCommand
	 */
	/*public void set_WLCommandWrapper(WLCommandWrapper newCommandWrapper){
		this.WLCommandWrapper = newCommandWrapper;
	} */
	
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
	 * Consider making moving this to parent class Struct.java.
	 * @return
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

	@Override
	public String simpleToString(boolean includeType, WLCommand curCommand, List<Expr> exprList){
		return simpleToString(includeType, curCommand, null, null, exprList);
	}
	/**
	 * Does not support three-argument form currently.
	 */
	@Override
	public String simpleToString(boolean includeType, WLCommand curCommand){
		return simpleToString(includeType, curCommand, null, null, new ArrayList<Expr>());
	}
	/**
	 * Simple toString to return the bare minimum to present this Struct.
	 * To be used in ParseToWLTree.
	 * @param includeType	Whether to include the type, eg "MathObj"
	 * @param curCommand current WLCommand this Struct is showing up in
	 * @return
	 */
	@Override
	public String simpleToString(boolean includeType, WLCommand curCommand, PosTerm triggerPosTerm,
			PosTerm curPosTerm, List<Expr> exprList){
		//if(this.posteriorBuiltStruct != null) return "";
		//this.WLCommandStrVisitedCount++;
		// instead of checking WLCommandStr, check if wrapperList is null
		// ie if any command has been assigned to this Struct yet. If yes,
		// get the last one (might want the one with highest commandsWithOtherHead
		// later).
		/*if(this.WLCommandStr != null){
			return this.WLCommandStr;
		} */
		
		if(this.WLCommandWrapperList != null){
			//if(true) throw new IllegalStateException();
 			int wrapperListSz = WLCommandWrapperList.size();
			//wrapperListSz should be > 0, since list is created when first wrapper is added
			WLCommandWrapper curWrapper = WLCommandWrapperList.get(wrapperListSz - 1);
			WLCommand composedCommand = curWrapper.WLCommand();
			/*if(this.type.equals("yeast")){
				throw new RuntimeException("StructA "+this);
			}*/
			/*
			 * if(WLCommand.structsWithOtherHeadCount(composedCommand) == 0 || 
					(null != (structHeadWithOtherHead = composedCommand.structHeadWithOtherHead())
					//dfsDepth suffices here, ie same level in the parse tree.
					&& this.dfsDepth() == structHeadWithOtherHead.dfsDepth())
					|| null == structHeadWithOtherHead){
			 */
			Struct structHeadWithOtherHead = null;
			if(WLCommand.structsWithOtherHeadCount(composedCommand) == 0 || 
					(null != (structHeadWithOtherHead = composedCommand.structHeadWithOtherHead())
					&& this.dfsDepth() == structHeadWithOtherHead.dfsDepth()
					|| null == structHeadWithOtherHead)){
				
			//System.out.println("StructA - this.dfsDepth() == structHeadWithOtherHead.dfsDepth() "+this.dfsDepth() == structHeadWithOtherHead.dfsDepth() + this.toString());

			if(curCommand != null){
				int commandNumUnits = WLCommand.commandNumUnits(composedCommand);
			 	WLCommand.increment_commandNumUnits(curCommand, commandNumUnits);
			}
			//System.out.println("^^^curWrapper: " + curWrapper);
			
			//been built into one command already
			if(null == this.commandBuilt){
				this.commandBuilt = curCommand;
				this.WLCommandStrVisitedCount++;
			}else if(curCommand != this.commandBuilt
					/*!curCommand.equals(this.commandBuilt) July 2017*/
					){
				this.WLCommandStrVisitedCount++;				
			}
			//this.WLCommandStrVisitedCount++;
			curCommand.addComposedWLCommands(composedCommand);
			exprList.add(curWrapper.commandExpr());
			return curWrapper.WLCommandStr();			
			}
		}		
		//String name = this.struct.get("name");
		//return name == null ? this.type : name;
		return this.simpleToString2(includeType, curCommand, triggerPosTerm, curPosTerm, exprList);
	}
	
	/**
	 * Auxilliary method for simpleToString and StructA.simpleToString.
	 * Try to get a single Expr onto exprList, so to construct only one Expr per struct.simpleToString call.
	 * @param includeType
	 * @param curCommand
	 * @param triggerPosTerm
	 * @param curPosTerm
	 * @param exprList
	 * @return
	 */
	private String simpleToString2(boolean includeType, WLCommand curCommand, PosTerm triggerPosTerm, 
			PosTerm curPosTerm, List<Expr> exprList){
		
		String makePptStr = retrievePosTermPptStr(triggerPosTerm, curPosTerm);
		
		//List<RuleExprWrapper> ruleExprWrapperList = new ArrayList<RuleExprWrapper>();
		List<Expr> curLevelExprList = new ArrayList<Expr>();
				
		if(curCommand != null) {
			WLCommand.increment_commandNumUnits(curCommand, this);
		}
		//been built into one command already
		//this.WLCommandStrVisitedCount++;
		if(null == this.commandBuilt){
			this.commandBuilt = curCommand;
			this.WLCommandStrVisitedCount++;
		}else if(!curCommand.equals(this.commandBuilt)){
			this.WLCommandStrVisitedCount++;
		}
		
		ExprWrapperType headExprWrapperType = ExprWrapperType.OTHER;
		//String pptCommaStr = "";
		StringBuilder sb = new StringBuilder();		
		if(includeType){			
			if(!"".equals(makePptStr)){
				String pptCommaStr = "\"" + makePptStr + "\", ";				
				sb.append(this.type.equals("ent") ? "MathProperty" : this.type).append("[\"Mode\"->").append(pptCommaStr);
				//curLevelExprList.add(new Expr(new Expr("\"Mode\""), new Expr[]{new Expr(makePptStr)}));
				
				headExprWrapperType = ExprWrapperType.MATHPPT;
			}else{
				sb.append(this.type.equals("ent") ? "Math" : this.type).append("[");		
				headExprWrapperType = ExprWrapperType.MATH;
			}
		}
		boolean prependCommaBool = false;
		
		List<Expr> pptExprList = new ArrayList<Expr>();
		//list of cardinalities
		List<Expr> cardExprList = new ArrayList<Expr>();
		//boolean pptAppended = false;
		//boolean cardAppended = false;
		StringBuilder pptSB = new StringBuilder(30);
		StringBuilder cardSB = new StringBuilder(15);
		Iterator<String> pptStrListIter = getPropertySet().iterator();		
		
		if(this.article() != Article.NONE){
			String articleStr = this.article().toString();
			
			cardSB.append("\"").append(articleStr).append("\", ");
			//sb.append("\"Cardinality\"->{\"").append(articleStr).append("\"");	
			cardExprList.add(new Expr(articleStr));
		}
		while(pptStrListIter.hasNext()){
			String nextStr = pptStrListIter.next();			
			if(WordForms.CARDINALITY_PPT_PATTERN.matcher(nextStr).matches()){
				cardSB.append("\"").append(nextStr).append("\", ");
				cardExprList.add(new Expr(nextStr));
			}else{
				pptSB.append("\"").append(nextStr).append("\", ");
				pptExprList.add(new Expr(nextStr));
			}
		}
		if(!pptExprList.isEmpty()){
			
			prependCommaBool = true;
			//increment once, this covers all ppt's.
			WLCommand.increment_commandNumUnits(curCommand, this);
			String pptStr = pptSB.toString();
			int pptSBLen = pptSB.length();
			if(pptSBLen > 2){ //shouldn't need to check{
				pptStr = pptSB.substring(0, pptSBLen-2);
			}
			sb.append("\"Qualifiers\"->{").append(pptStr).append("}");
			//Expr pptListExpr = ExprUtils.listExpr(pptExprList);
			curLevelExprList.add(ExprUtils.createExprFromList(new Expr("Qualifiers"), pptExprList));
			//ruleExprWrapperList.add(new RuleExprWrapper(new Expr("Property"), pptListExpr));
			//curLevelExprList.add(new Expr(new Expr("Qualifiers"), new Expr[]{pptListExpr}));
		}	
		String quantityStr = struct.get(WordForms.QUANTITY_POS);
		if(null != quantityStr){
			cardSB.append("\"").append(quantityStr).append("\", ");
			cardExprList.add(new Expr(quantityStr));
		}		
		//append cardinality list
		if(!cardExprList.isEmpty()){
			
			prependCommaBool = true;
			String cardStr = cardSB.toString();
			int cardSBLen = cardSB.length();
			if(cardSBLen > 2){ //shouldn't need to check
				cardStr = cardSB.substring(0, cardSBLen-2); 
			}
			sb.append("\"Cardinality\"->{").append(cardStr).append("}");
			Expr cardListExpr;
			if(cardExprList.size() == 1) {
				cardListExpr = cardExprList.get(0);
			}else {
				cardListExpr = ExprUtils.listExpr(cardExprList);
			}
			//ruleExprWrapperList.add(new RuleExprWrapper(new Expr("Cardinality"), cardListExpr));
			curLevelExprList.add(new Expr(new Expr("Cardinality"), new Expr[]{cardListExpr}));
		}

		//append name
		String name = struct.get("name");
		if(null != name){
			if(prependCommaBool){
				sb.append(", ");
			}
			/*sb.append("\"Name\"->\"").append(name).append("\"");
			RuleExprWrapper ruleWrapper = new RuleExprWrapper(new Expr("Name"), new Expr(name));*/
			sb.append("\"Type\"->\"").append(name).append("\"");
			curLevelExprList.add(new Expr(new Expr("Type"), new Expr[]{new Expr(name)}));
			//RuleExprWrapper ruleWrapper = new RuleExprWrapper(new Expr("Type"), new Expr(name));
			//ruleExprWrapperList.add(ruleWrapper);
			prependCommaBool = true;
		}
		
		String called = struct.get("called");
		if(null != called){
			if(prependCommaBool){
				sb.append(", ");
			}
			/*sb.append("\"Called\"->").append("\"").append(called).append("\"");
			ruleExprWrapperList.add(new RuleExprWrapper(new Expr("Called"), new Expr(called)));*/
			sb.append("\"Label\"->").append("\"").append(called).append("\"");
			//ruleExprWrapperList.add(new RuleExprWrapper(new Expr("Name"), new Expr(called)));
			curLevelExprList.add(new Expr(new Expr("Label"), new Expr[]{new Expr(called)}));
		}
		//append name
		String tex = struct.get("tex");
		if(null != tex){
			if(prependCommaBool){
				sb.append(", ");
			}
			sb.append("\"Label\"->\"").append(tex).append("\"");
			//ruleExprWrapperList.add(new RuleExprWrapper(new Expr("Label"), new Expr(tex)));
			curLevelExprList.add(new Expr(new Expr("Label"), new Expr[]{new Expr(tex)}));
		}
		
		//List<RuleExprWrapper> childRuleExprWrapperList = new ArrayList<RuleExprWrapper>();
		sb.append(appendChildrenQualifierString(includeType, curCommand, curLevelExprList));		
		/*if(childRuleExprWrapperList.size() > 0){
			//add to rules list
			ruleExprWrapperList.add(childRuleExprWrapperList.get(0));
		}*/
		
		//AssocExprWrapper assocExprWrapper = new AssocExprWrapper(ruleExprWrapperList);
		//ExprWrapper mathExprWrapper;
		Expr combinedExpr;
		if(headExprWrapperType == ExprWrapperType.MATHPPT){
			
			if(!"".equals(makePptStr)) {
				Expr modeExpr = new Expr(new Expr("Mode"), new Expr[]{new Expr(makePptStr)});
				Expr mathObjExpr = ExprUtils.mathExpr(curLevelExprList);
				combinedExpr = ExprUtils.mathPptExpr(modeExpr, mathObjExpr);				
			}else {
				combinedExpr = ExprUtils.mathPptExpr(curLevelExprList);
			}
		}else{
			//mathExprWrapper = new MathExprWrapper(assocExprWrapper);
			combinedExpr = ExprUtils.mathExpr(curLevelExprList);
		}		 
		//exprList.add(mathExprWrapper.expr());
		exprList.add(combinedExpr);
		
		if(includeType){ 
			sb.append("]");
		}				
		return sb.toString();
	}

	@Override
	public boolean isLeafNodeCouldHaveChildren(){
		//return 0 == children.size();
		return true;
	}
	/*@Override
	public boolean commandVisited(){
		return true;
	}*/
	
	@Override
	public void clear_commandBuilt(){
		this.commandBuilt = null;
	}
	
	/**
	 * @return set of properties of this StructH.
	 */
	@Override
	public Set<String> getPropertySet(){
		//seek out the properties in struct
		//thread-safe with volatile boolean isPropertySetEmpty. But could add to map multiple times
		//if called by multiple threads, could be little wasteful but no race condition.
		if(isPropertySetCreated){
			for(Map.Entry<String, String> entry : struct.entrySet()){
				if(entry.getValue().equals("ppt")){
					propertySet.add(entry.getKey());
				}
			}
		}		
		isPropertySetCreated = false;
		return propertySet;		
	}
	
	/**
	 * Retrieve name, ppt, called, tex info.
	 * @return List of name, ppt, strings, etc.
	 */
	private List<String> get_name_pptStr_List(){
		
		List<String> namePptStrList = new ArrayList<String>();
		Iterator<Entry<String, String>> structIter = struct.entrySet().iterator();
		String name = "", called = "", ppt = "", tex = "";
		//boolean addToPropertySet = propertySet.isEmpty();
		
		while(structIter.hasNext()){
			Entry<String, String> entry = structIter.next();
			
			if(entry.getValue().matches("ppt") ){
				String newPpt = entry.getKey();
				ppt += newPpt + ", ";
				if(isPropertySetCreated){
					propertySet.add(newPpt);
				}
			}
			else if(entry.getKey().matches("name") ){
				name = entry.getValue();
			}
			else if(entry.getKey().matches("called") ){
				called = entry.getValue();
			}
			else if(entry.getKey().matches("tex") ){
				tex = entry.getValue();
			}
		}		
		
		isPropertySetCreated = false;
		
		if(!name.equals("")) namePptStrList.add(name);
		if(!tex.equals("")) namePptStrList.add(tex);
		if(!called.equals("")) namePptStrList.add(called);
		if(!ppt.equals("")){ 
			String pptStr = ppt;
			pptStr = pptStr.length() > 2 ? pptStr.substring(0, pptStr.length() - 2) : pptStr;
			namePptStrList.add(pptStr);
		}
		return namePptStrList;
	}
	
	/**
	 * Retrieve name, ppt, called, tex info.
	 */
	public String append_name_pptStr(){
		
		List<String> namePptStrList = get_name_pptStr_List();
		int namePptStrListLen = namePptStrList.size();
		if(0 == namePptStrListLen) return "";
		StringBuilder sb = new StringBuilder();
				
		for(int i = 0; i < namePptStrListLen-1; i++){
			String namePptStr = namePptStrList.get(i);
			String nextStr = namePptStrList.get(i+1);
			
			if(!nextStr.equals("")){
				sb.append(namePptStr + ", ");
			}else{
				sb.append(namePptStr);
			}			
		}		
		String pptStr = namePptStrList.get(namePptStrListLen-1);
		
		sb.append(pptStr.length() > 2 ? pptStr.substring(0, pptStr.length() - 2) : pptStr);
		/*
		name = tex.length() > 0 ? name + ", ": name;
		tex = called.length() > 0 ? tex + ", ": tex;
		called = !(ppt.length() == 0) ? called + ", " : called;
		ppt = ppt.length() > 2 ? ppt.substring(0, ppt.length() - 2) : ppt;			

		for(String str : ){
			sb.append(str);
		}*/
		return sb.toString();
	}
	
	//similar to toString(). Presents StructH as a String
	@Override
	public String present(String str){
		str += this.type.equals("ent") ? "Math" : this.type;
		str += "[";
		
		str += append_name_pptStr();
		
		if(children.size() > 0) str += ", ";
		
		//iterate through children		
		int childrenSize = children.size();
		for(int i = 0; i < childrenSize; i++){
			str += childRelationList.get(i) + " ";
			Struct child = children.get(i);
			str = child.present(str);	
			if(i < childrenSize - 1)
				str += ", ";
		}
		
		str += "]";

		return str;
	}

	@Override
	public void set_prev1(String str) {			
	}

	@Override
	public Object prev1() {		
		return null;
	}

	@Override
	public Object prev2() {		
		return null;
	}
	
	/**
	 * Extracts the list of content Strings of this Struct. 
	 * Content includes name, properties, and children.
	 * List is re-constructed each time this is called.
	 */
	@Override
	public List<String> contentStrList(){
		//this list includes the name and ppt strings of this Struct.
		List<String> contentList = new ArrayList<String>(this.getPropertySet());
		String nameStr = struct.get("name");
		if(null != nameStr){
			contentList.add(nameStr);
		}
		//get Strings for children
		for(Struct child : children){
			contentList.addAll(child.contentStrList());
		}		
		return contentList;
	}
	
	/**
	 * @return nameStr Empty string is no name specified. Will not be null.
	 */
	@Override
	public String nameStr(){		
		String name = struct.get("name");
		String nameStr = name == null ? "" : name; 
		return nameStr;
	}
	
	/**
	 * Calling the applicable ParseTreeToVec with dynamic dispatch.
	 * To avoid casting, and to distribute the logic.
	 */
	@Override
	public void setContextVecEntry(int structParentIndex, Map<Integer, Integer> contextVecMap, boolean adjustVecFromCommand){
		ParseTreeToContextVec.setStructHContextVecEntry(this, structParentIndex, contextVecMap, adjustVecFromCommand);
	}
	
}
