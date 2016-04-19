import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

public class StructH<H> extends Struct{

	private HashMap<String, String> struct; //hashmap
	//ent (entity) is only structure that uses hashmap
	private String type; //ent, adj, etc
	private boolean hasChild = false;
	private ArrayList<Struct> children; 
	//relation to child, eg "of," "enjoyed"
	private ArrayList<String> childRelation;	
	//parent
	//private Struct parent;
	
	public StructH(HashMap<String, String> struct, String type){
	
		this.struct = struct;
		this.type = type;
		this.children = new ArrayList<Struct>();
		this.childRelation = new ArrayList<String>();
	}

	//when is this used??
	public StructH(String type){
		
		this.type = type;
		this.children = new ArrayList<Struct>();
		this.childRelation = new ArrayList<String>();
	}
	
	public void set_struct(HashMap<String, String> struct){
		this.struct = struct;
	}
	
	//make semi-deep copy, points to same struct, but children are deep copy
	@Override
	public StructH<H> copy(){
		StructH<H> newStructH = new StructH<H>(this.struct, this.type);
		
		for(int i = 0; i < this.children.size(); i++){
			newStructH.add_child(this.children.get(i), this.childRelation.get(i));
		}
		
		return newStructH;
	}
	
	public void add_previous(Struct prev){
		hasChild = true;
		children.add(prev);
		//if no relation specified
		childRelation.add("");
	}

	public void add_child(Struct prev, String relation){
		hasChild = true;
		children.add(prev);
		childRelation.add(relation);
	}
	
	public boolean has_previous(){
		return hasChild;		
	}
	
	public ArrayList<Struct> children(){
		return children;		
	}
	
	public ArrayList<String> childRelation(){
		return childRelation;		
	}
	
	@Override
	public HashMap<String, String> struct(){		
		return this.struct;		
	}
	
	@Override
	public String type(){
		return this.type;		
	}
	
	@Override
	public void set_type(String type){
		this.type = type;		
	}
	
	@Override
	public String toString(){
		String str = this.type;
		str += this.struct; //struct is hashmap for structH
		
		return str;
	}
	

	public void test(Object obj){
		
	}

}
