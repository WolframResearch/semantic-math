package food.parse;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.wolfram.jlink.Expr;

import thmp.parse.ParseToWLTree.WLCommandWrapper;
import thmp.parse.Struct;
import thmp.parse.StructA;
import thmp.parse.StructList;
import thmp.parse.WLCommand;
import thmp.parse.WLCommand.PosTerm;

public class FoodStruct extends thmp.parse.Struct {

	private static final long serialVersionUID = 6622947999934429378L;
	private Struct struct;
	private String qualifier;
	
	public FoodStruct(Struct struct_, String qualifier_){
		this.struct = struct_;
		this.qualifier = qualifier_;
	}
	
	@Override
	public boolean isFoodStruct(){
		return true;
	}
	
	@Override
	public Struct previousBuiltStruct() {		
		return struct.previousBuiltStruct();
	}

	@Override
	public Struct posteriorBuiltStruct() {
		
		return struct;
	}

	@Override
	public void set_previousBuiltStruct(Struct previousBuiltStruct) {
		
		
	}

	@Override
	public void set_posteriorBuiltStruct(Struct posteriorBuiltStruct) {
		
		
	}

	@Override
	public Struct structToAppendCommandStr() {
		
		return struct;
	}

	@Override
	public void set_structToAppendCommandStr(Struct structToAppendCommandStr) {
		
		
	}

	@Override
	public int WLCommandStrVisitedCount() {
		
		return 0;
	}

	@Override
	public void clear_WLCommandStrVisitedCount() {
		
		
	}

	@Override
	public void clear_commandBuilt() {
		
		
	}

	@Override
	public void set_structList(StructList structList) {
		
		
	}

	@Override
	public void set_parentStruct(Struct parent) {
		
		
	}

	@Override
	public Struct parentStruct() {
		
		return struct;
	}

	@Override
	public void set_possessivePrev(Struct prev) {
		
		
	}

	@Override
	public Struct possessivePrev() {
		
		return struct;
	}

	@Override
	public void set_dfsDepth(int depth) {
		
		
	}

	@Override
	public int dfsDepth() {
		
		return 0;
	}

	@Override
	public double score() {
		
		return 0;
	}

	@Override
	public boolean has_child() {
		
		return false;
	}

	@Override
	public String simpleToString(boolean includeType, WLCommand curCommand, PosTerm triggerPosTerm, PosTerm curPosTerm,
			List<Expr> exprList) {
		
		return struct.simpleToString(includeType, curCommand, triggerPosTerm, curPosTerm, exprList);
	}

	@Override
	public String simpleToString(boolean includeType, WLCommand curCommand) {
		
		return struct.simpleToString(includeType, curCommand);
	}

	@Override
	public String simpleToString(boolean includeType, WLCommand curCommand, List<Expr> exprList) {
		
		return struct.simpleToString(includeType, curCommand, exprList);
	}

	@Override
	public void setContextVecEntry(int structParentIndex, Map<Integer, Integer> contextVecMap,
			boolean adjustVecFromCommand) {
		struct.setContextVecEntry(structParentIndex, contextVecMap, adjustVecFromCommand);
		
	}

	@Override
	public WLCommandWrapper add_WLCommandWrapper(WLCommand newCommand) {
		
		return struct.add_WLCommandWrapper(newCommand);
	}

	@Override
	public List<WLCommandWrapper> WLCommandWrapperList() {
		
		return struct.WLCommandWrapperList();
	}

	@Override
	public void clear_WLCommandWrapperList() {
		
		
	}

	@Override
	public int numUnits() {
		
		return 0;
	}

	@Override
	public double maxDownPathScore() {
		
		return 0;
	}

	@Override
	public void set_maxDownPathScore(double pathScore) {
		
		
	}

	@Override
	public Set<String> extraPosSet() {
		
		return struct.extraPosSet();
	}

	@Override
	public void addExtraPos(String pos) {
		
		
	}

	@Override
	public List<String> contentStrList() {
		
		return struct.contentStrList();
	}

	@Override
	public String nameStr() {
		
		return struct.nameStr();
	}

	@Override
	public StructList get_StructList() {
		
		return struct.get_StructList();
	}

	@Override
	public void set_score(double score) {
		
		
	}

	@Override
	public void set_type(String type) {
		
		
	}

	@Override
	public Struct copy() {
		
		return struct;//////////////////
	}

	@Override
	public String type() {
		
		return struct.type();
	}

	@Override
	public boolean isLeafNodeCouldHaveChildren() {
		
		return false;
	}

	@Override
	public List<Struct> children() {
		
		return struct.children();
	}

	@Override
	public List<ChildRelation> childRelationList() {
		
		return struct.childRelationList();
	}

	@Override
	protected void setHasChildToTrue() {
		
	}

	@Override
	public Set<String> getPropertySet() {
		
		return struct.getPropertySet();
	}

	@Override
	public StructA<? extends Object, ? extends Object> copyToStructA(String newType) {
		
		return struct.copyToStructA(newType);
	}

	@Override
	public Map<String, String> struct() {
		
		return struct.struct();
	}

	@Override
	public String toString() {
		
		return struct.toString();
	}

	@Override
	public void set_prev1(String str) {
		
		
	}

	@Override
	public Object prev1() {
		
		return struct.prev1();
	}

	@Override
	public Object prev2() {
		
		return struct.prev2();
	}

	@Override
	public String present(String str) {
		
		return struct.present(str);
	}

	@Override
	public NodeType prev1NodeType() {
		
		return struct.prev1NodeType();
	}

	@Override
	public NodeType prev2NodeType() {
		
		return struct.prev2NodeType();
	}

	public Struct getStruct() {
		return struct;
	}

	public void setStruct(Struct struct) {
		this.struct = struct;
	}

	public String qualifier() {
		return qualifier;
	}

	public void setQualifier(String qualifier) {
		this.qualifier = qualifier;
	}

}
