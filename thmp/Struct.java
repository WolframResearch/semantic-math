package thmp;
import java.util.HashMap;
import java.util.ArrayList;

/*
 * Struct to contain entities in sentence
 * to be parsed
 */

public class Struct {

	/*
	public Struct(K struct, String type){
		this.struct = struct;
		this.type = type;
	}
	
	public Struct(Struct<K> prev1, Struct<K> prev2, String type){
		this.prev1 = prev1;
		this.prev2 = prev2;
		this.type = type;
	}
	*/
	
	//to be overridden
	public void set_type(String type){		
	}
	
	public Struct copy(){
		return this;
	}
	
	//to be overridden
	public String type(){
		return "";
	}
	
	//to be overwritten in StructH
	public ArrayList<Struct> children(){
		return new ArrayList<Struct>();		
	}

	//to be overwritten in StructH
	public ArrayList<String> childRelation(){
		return new ArrayList<String>();		
	}
	
	//to be overwritten in StructH
	public void add_child(Struct child, String relation){		
	}
	
	//filler method to be overriden
	public HashMap<String, String> struct(){
		return new HashMap<String, String>();
	}
	
	public String toString(){
		return "";
	}

	public void set_prev1(String str){
	}
	
	public void set_prev2(String str){
	}
	
	public Object prev1(){
		return "";
	}
	
	public Object prev2(){
		return "";
	}
	
	public void test(String obj){
		
	}
	
	public String present(){
		return "";
	}
	
	public Number test(ArrayList<Number> b){
		return 3;
	}
}
