import java.util.Collection;

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

	public Struct(){
		
	}
	
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
	
	public String toString(){
		return "";
	}
	
	public Object prev1(){
		return null;
	}
	
	public void test(String obj){
		
	}
}
