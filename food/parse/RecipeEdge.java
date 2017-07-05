package food.parse;

import java.util.ArrayList;
import java.util.List;

import com.wolfram.jlink.Expr;

import thmp.parse.Struct;

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
