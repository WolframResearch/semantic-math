package thmp;
import java.util.ArrayList;
import java.util.List;

public class StructA<A, B> extends Struct{

	//a Struct can correspond to many MatrixPathNode's, but each MatrixPathNode 
	//corresponds to one unique Struct.
	//I.e. one-to-many map between Struct's and MatrixPathNode's
	
	private A prev1; 
	private B prev2; 
	//score for this structA, to indicate likelihood of relation in Rule
	//Ranges over (0, 1]. 1 by default
	private double score;
	private String type; //or, and, adj, pro etc, cannot ent
	private String type1; //type of prev1, , al, string etc. Is this used??
	private String type2; //type of prev2
	//list of Struct at mx element, to which this Struct belongs
	//pointer to mx.get(i).get(j)
	private StructList structList;
	private double maxDownPathScore;
	private List<MatrixPathNode> mxPathNodeList;
	
	public StructA(A prev1, B prev2, String type){
		
		this.prev1 = prev1;		
		this.prev2 = prev2;
		this.type = type; 
		this.score = 1;
	}
	
	public StructA(A prev1, B prev2, String type, StructList structList){		
		this.prev1 = prev1;		
		this.prev2 = prev2;
		this.type = type; 
		this.score = 1;
		this.structList = structList;
		this.mxPathNodeList = new ArrayList<MatrixPathNode>();
	}

	@Override
	public StructList StructList(){
		return this.structList;
	}
	
	@Override
	public double maxDownPathScore(){
		return this.maxDownPathScore;
	}
	
	/**
	 * This sets the max path score among any mxPathNode's that
	 * pass here. *down* score or total path score??
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
	
	@Override
	public A prev1(){
		return this.prev1;		
	}
	
	@Override
	public B prev2(){
		return this.prev2;		
	}

	//use carefully: must know the declared type
	/////get rid of @suppresswarnings
	@SuppressWarnings("unchecked")
	public void set_prev1(Object prev1){
		this.prev1 = (A)prev1;		
	}
	
	@SuppressWarnings("unchecked")
	public void set_prev1(String prev1){	
		this.prev1 = (A)prev1;
	}
	
	@SuppressWarnings("unchecked")
	public void set_prev2(Object prev2){
		this.prev2 = (B)prev2;		
	}

	@SuppressWarnings("unchecked")
	public void set_prev2(String prev2){
		this.prev2 = (B)prev2;	
	}
	
	@Override
	public String type(){
		return this.type;		
	}

	@Override
	public void set_type(String type){
		this.type = type;		
	}
	
	//public void set_prev1(A str){
	//}
	
	public String type1(){
		return this.type1;		
	}

	public String type2(){
		return this.type2;		
	}

	@Override
	public void set_score(double score){
		this.score = score;
	}

	@Override
	public String toString(){
		String str = " type: " + this.type 				
				+ ", " + this.prev1;
		
		return str;
	}
	
	//used by present() in StructH; right now no need
	//to go deeper into prev1/prev2
	@Override
	public String present(String str){
		//str += this.type + "[";
		boolean showprev1 = true;
		if(this.type.matches("hyp") && this.prev1 instanceof String
				&& !((String)this.prev1).matches("for all|for every")){
			showprev1 = false;
		}
		
		str += this.type.matches("conj_.*") ? this.type + "[" : "[";
		//str += "[";
		
		if(prev1 != null && !prev1.equals("")){
			if(prev1 instanceof Struct){
				str = ((Struct) prev1).present(str);
				
			}else if(prev1 instanceof String && showprev1){
				if(!type.matches("pre|partiby")){
					str += prev1;
				}
			}			
		}
		
		if(prev2 != null && !prev2.equals("")){
			if(prev2 instanceof Struct){
				str = ((Struct) prev2).present(str + ", ");
			}else if(prev2 instanceof String){
				str += ", " + prev2;
			}
		}
		
		//str += "]";
		str += "]";
		return str;
	}
	
	@Override
	public Integer test(ArrayList<Number> b){
		Integer i = 3;
		return i;
	}
}
