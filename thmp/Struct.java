package thmp;
import java.util.HashMap;
import java.util.List;
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

	public void set_structList(StructList structList){
	}

	/**
	 * Set parent pointer of this struct
	 * 
	 */
	public void set_parentStruct(Struct parent){		
	}
	
	public Struct parentStruct(){
		return null;
	}
	
	//returns probability of relation in Rule
	public double score(){
		return 0;
	}
	
	//Simple toString to return the bare minimum to identify this Struct.
	//To be used in ParseToWLTree.
	public String simpleToString(){
		return "";
	}
	
	public String simpleToString2(String str){
		return "";
	}
	
	public void append_WLCommandStr(String WLCommandStr){
	}

	/**
	 * Sets WLCommandStr to null.
	 */
	public void clear_WLCommandStr(){
	}

	public String WLCommandStr(){
		return "";
	}

	public int numUnits(){
		return 1;
	}
	
	public double maxDownPathScore(){
		return 0;
	}
	
	public void set_maxDownPathScore(double pathScore){
		
	}
	
	public StructList StructList(){
		return null;
	}
	
	public void set_score(double score){
		
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
	
	//to be overwritten in StructH
	public ArrayList<Struct> children(){
		return null;		
	}

	//to be overwritten in StructH
	public ArrayList<String> childRelation(){
		return null;		
	}
	
	//to be overwritten in StructH
	public void add_child(Struct child, String relation){		
	}
	
	// to be overriden
	public HashMap<String, String> struct(){
		return null;
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
	
	public String present(String str){
		return "";
	}
	
	public Number test(ArrayList<Number> b){
		return 3;
	}
}
