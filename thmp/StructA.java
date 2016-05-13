package thmp;
import java.util.ArrayList;

public class StructA<A, B> extends Struct{

	private A prev1; 
	private B prev2; 
	private String type; //or, and, adj, pro etc, cannot ent
	private String type1; //type of prev1, , al, string etc. Is this used??
	private String type2; //type of prev2
	
	public StructA(A prev1, B prev2, String type){
		
		this.prev1 = prev1;		
		this.prev2 = prev2;
		this.type = type; 
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
	public String toString(){
		String str = " type: " + this.type + " ";
		
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
