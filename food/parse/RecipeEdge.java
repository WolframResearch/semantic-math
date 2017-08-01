package food.parse;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.wolfram.jlink.Expr;

import thmp.parse.Struct;
import thmp.utils.ExprUtils;

/**
 * Describes food processing step .
 * Used to connect FoodState's.
 * Constitutes the edges of a recipe graph. 
 * One edge object could represent *multiple* edges, in connecting
 * multiple products.
 * @author yihed
 */
public class RecipeEdge {

	//Epxr representing this processing step, 
	//e.g. Math["bake"]
	private Expr actionExpr;
	private Struct actionStruct;
	//e.g. bake ... in the oven
	private List<Struct> qualifierStructList = new ArrayList<Struct>();
	
	public RecipeEdge( Struct actionStruct_, List<Struct> qualifiers_){
		//this.actionExpr = action;
		this.actionStruct = actionStruct_;
		this.qualifierStructList = qualifiers_;
	}
	
	public Struct actionStruct(){
		return actionStruct;
	}
	
	public List<Struct> qualifierStructList(){
		return qualifierStructList;
	}
	
	public Expr actionExpr(){
		return actionExpr;
	}
	
	public Expr toExpr(){
		List<Expr> qList = new ArrayList<Expr>();	
		if(!qualifierStructList.isEmpty()){		
			System.out.println("qualifierStructList "+qualifierStructList);
			for(Struct struct : qualifierStructList){	
				Expr structExpr;
				if(struct.isFoodStruct() ){
					String qualifierStr = ((FoodStruct)struct).qualifier();
					if("".equals(qualifierStr)){
						//qList.add(new Expr(struct.nameStr()));
						structExpr = ExprUtils.listExpr(gatherStructPptExpr(struct));						
						//structExpr = new Expr(struct.nameStr());
					}else{
						//List<Expr> structExprList = new ArrayList<Expr>();	
						List<Expr> structExprList = gatherStructPptExpr(struct);
						structExprList.add(0, new Expr(qualifierStr));						
						structExpr = ExprUtils.listExpr(structExprList);
					}					
				}else{
					//qList.add(new Expr(struct.nameStr()));
					structExpr = ExprUtils.listExpr(gatherStructPptExpr(struct));
				}
				qList.add(structExpr);
			}
		}
		if(!qList.isEmpty()){	
			return new Expr(new Expr(Expr.SYMBOL, "Action"), new Expr[]{new Expr(actionStruct.nameStr()), ExprUtils.listExpr(qList)});
		}else{
			return new Expr(new Expr(Expr.SYMBOL, "Action"), new Expr[]{new Expr(actionStruct.nameStr())});
		}
	}
	
	/**
	 * Collects struct's name and properties as a list of Expr's.
	 * @param struct
	 * @return
	 */
	private List<Expr> gatherStructPptExpr(Struct struct) {

		List<Expr> structExprList = new ArrayList<Expr>();
		if(!struct.isStructA()){
			Set<String> pptSet = struct.getPropertySet();
			if(!pptSet.isEmpty()){
				for(String pptStr : pptSet){
					structExprList.add(new Expr(pptStr));				
				}
			}			
		}else if("prep".equals(struct.type()) && struct.prev2NodeType().isTypeStruct()){			
			structExprList.add(new Expr(((Struct)struct.prev2()).nameStr()));			
		}
		structExprList.add(new Expr(struct.nameStr()));
		return structExprList;
	}

	@Override
	public String toString(){
		StringBuilder sb = new StringBuilder();		
		if(!qualifierStructList.isEmpty()){
			for(Struct struct : qualifierStructList){
				sb.append(" ");
				if(struct.isFoodStruct()){
					sb.append(((FoodStruct)struct).qualifier() ).append(" ").append(struct.nameStr()).append(", "); 
				}else{
					sb.append(struct.nameStr()).append(", ");
				}
			}
			if(sb.length() > 2){
				sb = sb.delete(sb.length()-2, sb.length());				
				sb.insert(0, "{").append("}");
			}	
			//sb.append(" {").append(qualifierStructList).append("} ");
		}
		sb.insert(0, actionStruct.nameStr());
		return sb.toString();
	}
}
