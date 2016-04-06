import java.util.ArrayList;

public class StructA<A, B> extends Struct{

	private A prev1; 
	private B prev2; 
	private String type; //or, and, ent, adj etc
	private String type1; //type of prev1, , al, string etc
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
	public void set_prev2(Object prev2){
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
	
	@Override
	public void set_prev2(String str){
	}

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
	
	@Override
	public Integer test(ArrayList<Number> b){
		Object obj = new Object();
		Integer i = 3;
		return i;
	}
}
