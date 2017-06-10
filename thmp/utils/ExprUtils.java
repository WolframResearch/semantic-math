package thmp.utils;

import java.util.Arrays;
import java.util.List;

import com.wolfram.jlink.Expr;

/**
 * Utility class for constructing Expr's from different inputs,
 * such as constructing a Rule Expr from two arguments
 * @author yihed
 *
 */
public class ExprUtils {

	private static final Expr assocHeadExpr = new Expr(Expr.SYMBOL, "Association");
	private static final Expr listHeadExpr = new Expr(Expr.SYMBOL, "List");
	private static final Expr sequenceHeadExpr = new Expr(Expr.SYMBOL, "Sequence");
	private static final Expr sentenceHeadExpr = new Expr(Expr.SYMBOL, "Sentence");
	private static final Expr ruleHeadExpr = new Expr(Expr.SYMBOL, "Rule");
	private static final Expr mathHeadExpr = new Expr(Expr.SYMBOL, "Math");
	private static final Expr mathPptHeadExpr = new Expr(Expr.SYMBOL, "MathProperty");
	private static final Expr qualifierHeadExpr = new Expr(Expr.SYMBOL, "Qualifiers");
	private static final Expr FAILED_EXPR = new Expr(Expr.SYMBOL, "$Failed");
	
	public enum ExprWrapperType{
		RULE,
		ASSOC,
		MATH,
		MATHPPT,
		OTHER;
	}
	
	public static class ExprWrapper{
		Expr expr;
		ExprWrapperType type = ExprWrapperType.OTHER;
		
		ExprWrapper(Expr expr_, ExprWrapperType type_){
			this.expr = expr_;
			this.type = type_;
		}
		
		public ExprWrapper(Expr expr_){
			this.expr = expr_;
		}
		
		public ExprWrapperType type(){
			return this.type;
		}
		public Expr expr(){
			return this.expr;
		}
		@Override
		public String toString(){
			return this.expr.toString();
		}
	}
	
	public static class RuleExprWrapper extends ExprWrapper{		
		public RuleExprWrapper(Expr p1, Expr p2){
			super(ExprUtils.ruleExpr(p1, p2), ExprWrapperType.RULE);
			//this.expr = ExprUtils.ruleExpr(p1, p2);
		}		
	}
	
	public static class AssocExprWrapper extends ExprWrapper{		
		public AssocExprWrapper(List<RuleExprWrapper> rulesExprWrapperList){
			super(ExprUtils.assocExpr(rulesExprWrapperList), ExprWrapperType.ASSOC);
			//this.expr = ExprUtils.assocExpr(rulesExprWrapperList);
		}		
	}
	
	public static class MathExprWrapper extends ExprWrapper{		
		public MathExprWrapper(AssocExprWrapper assocExprWrapper_){
			super(ExprUtils.mathExpr(assocExprWrapper_), ExprWrapperType.MATH);
			//this.expr = ExprUtils.mathExpr(assocExprWrapper_);
		}
	}
	
	public static class MathPptExprWrapper extends ExprWrapper{		
		public MathPptExprWrapper(AssocExprWrapper assocExprWrapper_){
			super(ExprUtils.mathExpr(assocExprWrapper_), ExprWrapperType.MATHPPT);
			//this.expr = ExprUtils.mathExpr(assocExprWrapper_);
		}
	}
	
	/**
	 * @param p1
	 * @param p2
	 * @return Expr of form Rule[p1->p2]
	 */
	public static Expr ruleExpr(Expr p1, Expr p2){		
		return new Expr(ruleHeadExpr, new Expr[]{p1, p2});
	}
	
	/**
	 * @param rulesExprList list of Rule Expr's. 
	 * @return An Expr with arguments and Head Association.
	 */
	public static Expr assocExpr(List<RuleExprWrapper> rulesExprWrapperList){
		
		Expr[] rulesExprAr = new Expr[rulesExprWrapperList.size()];
		int rulesExprWrapperListSz = rulesExprWrapperList.size();
		for(int i = 0; i < rulesExprWrapperListSz; i++){
			rulesExprAr[i] = rulesExprWrapperList.get(i).expr;
		}
		Expr listExpr = new Expr(listHeadExpr, rulesExprAr);		
		return new Expr(assocHeadExpr, new Expr[]{listExpr});
	}

	/**
	 * Math Expr takes in an association.
	 * @param assocExpr to be used in Math.
	 * @return
	 */
	public static Expr mathExpr(AssocExprWrapper assocExprWrapper){		
		/*if(!assocHeadExpr.equals(assocExpr.head())){
			throw new IllegalArgumentException("The argument to an Math Expr must be an association!");
		}*/			
		return new Expr(mathHeadExpr, new Expr[]{assocExprWrapper.expr});
	}
	
	/**
	 * Creates Expr with Math Head, with list of Expr as input.
	 * @param exprList
	 * @return
	 */
	public static Expr mathExpr(List<Expr> exprList){
		return createExprFromList(mathHeadExpr, exprList);
	}
	
	public static Expr mathPptExpr(AssocExprWrapper assocExprWrapper){		
		/*if(!assocHeadExpr.equals(assocExpr.head())){
			throw new IllegalArgumentException("The argument to an Math Expr must be an association!");
		}*/			
		return new Expr(mathPptHeadExpr, new Expr[]{assocExprWrapper.expr});
	}
	
	/**
	 * Creates Expr with MathProperty Head, with list of Expr as input.
	 * @param exprList
	 * @return
	 */
	public static Expr mathPptExpr(List<Expr> exprList){	
		return createExprFromList(mathPptHeadExpr, exprList);
	}
	
	public static Expr qualifierExpr(List<Expr> exprList){	
		return createExprFromList(qualifierHeadExpr, exprList);
	}
	
	public static Expr sentenceExpr(Expr expr){
		return new Expr(sentenceHeadExpr, new Expr[]{expr});
	}
	/**
	 * Construct a list of Expr's from the input exprList.
	 * @param exprList
	 * @return
	 */
	public static Expr listExpr(List<Expr> exprList){
		Expr[] exprAr = new Expr[exprList.size()];
		exprAr = exprList.toArray(exprAr);
		return new Expr(listHeadExpr, exprAr);
	}
	
	public static Expr sequenceExpr(List<Expr> exprList){
		Expr[] exprAr = new Expr[exprList.size()];
		exprAr = exprList.toArray(exprAr);
		return new Expr(sequenceHeadExpr, exprAr);
	}
	
	/**
	 * Create Expr with head symbol headSymbolStr, and arguments in exprList.
	 * @param headSymbolStr
	 * @param exprList
	 * @return
	 */
	public static Expr createExprFromList(String headSymbolStr, List<Expr> exprList){
		Expr headExpr = new Expr(Expr.SYMBOL, headSymbolStr);
		Expr[] exprAr = new Expr[exprList.size()];
		exprAr = exprList.toArray(exprAr);
		return new Expr(headExpr, exprAr);
	}
	
	/**
	 * Create Expr with head symbol headSymbolStr, and arguments in exprList.
	 * @param headSymbolStr
	 * @param exprList
	 * @return
	 */
	public static Expr createExprFromList(Expr headSymbolExpr, List<Expr> exprList){
		Expr[] exprAr = new Expr[exprList.size()];
		exprAr = exprList.toArray(exprAr);
		return new Expr(headSymbolExpr, exprAr);
	}
	
	public static Expr FAILED_EXPR(){
		return FAILED_EXPR;
	}
}
